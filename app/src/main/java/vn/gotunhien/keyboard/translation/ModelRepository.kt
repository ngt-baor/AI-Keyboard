package vn.gotunhien.keyboard.translation

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class DownloadOutcome {
    SUCCESS,
    CANCELLED,
    FAILED,
}

internal class ModelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val modelsDirectory = File(appContext.filesDir, "models")
    val modelFile = File(modelsDirectory, MODEL_FILE_NAME)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val verificationLock = Any()

    @Volatile
    private var verifiedLength = -1L

    @Volatile
    private var verifiedModified = -1L

    fun hasVerifiedModel(): Boolean {
        if (!modelFile.isFile || modelFile.length() != MODEL_SIZE_BYTES) return false
        val length = modelFile.length()
        val modified = modelFile.lastModified()
        if (verifiedLength == length && verifiedModified == modified) return true

        synchronized(verificationLock) {
            if (!modelFile.isFile || modelFile.length() != MODEL_SIZE_BYTES) return false
            val currentLength = modelFile.length()
            val currentModified = modelFile.lastModified()
            if (verifiedLength == currentLength && verifiedModified == currentModified) return true

            val digest = FileInputStream(modelFile).use { input -> digestStream(input) }
            val verified = digest.equals(MODEL_SHA256, ignoreCase = true)
            if (verified) {
                verifiedLength = currentLength
                verifiedModified = currentModified
            }
            return verified
        }
    }

    fun deleteModel(): Boolean {
        val deleted = !modelFile.exists() || modelFile.delete()
        verifiedLength = -1L
        verifiedModified = -1L
        return deleted
    }

    fun close() {
        executor.shutdownNow()
    }

    fun download(
        onProgress: (downloadedBytes: Long) -> Unit,
        onComplete: (DownloadOutcome) -> Unit,
    ): DownloadHandle {
        val handle = DownloadHandle()
        executor.execute {
            val partialFile = File(modelsDirectory, "$MODEL_FILE_NAME.part")
            var outcome = DownloadOutcome.FAILED
            var connection: HttpURLConnection? = null

            try {
                modelsDirectory.mkdirs()
                partialFile.delete()
                val request = URL(MODEL_URL).openConnection() as HttpURLConnection
                connection = request
                handle.connection.set(request)
                request.instanceFollowRedirects = true
                request.connectTimeout = 20_000
                request.readTimeout = 30_000
                request.setRequestProperty("User-Agent", "GoTuNhienKeyboard/0.1")
                request.connect()

                if (request.responseCode !in 200..299) throw IllegalStateException("download_failed")
                val reportedSize = request.contentLengthLong
                if (reportedSize > 0 && reportedSize != MODEL_SIZE_BYTES) {
                    throw IllegalStateException("unexpected_model_size")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var lastReported = 0L
                request.inputStream.use { input ->
                    FileOutputStream(partialFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (handle.cancelled.get()) throw InterruptedException()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > MODEL_SIZE_BYTES) throw IllegalStateException("model_too_large")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            if (received - lastReported >= PROGRESS_INTERVAL || received == MODEL_SIZE_BYTES) {
                                lastReported = received
                                val current = received
                                mainHandler.post { onProgress(current) }
                            }
                        }
                    }
                }

                if (handle.cancelled.get()) throw InterruptedException()
                if (received != MODEL_SIZE_BYTES) throw IllegalStateException("incomplete_model")
                val actualHash = digest.digest().toHexString()
                if (!actualHash.equals(MODEL_SHA256, ignoreCase = true)) {
                    throw IllegalStateException("model_checksum_mismatch")
                }

                try {
                    Files.move(
                        partialFile.toPath(),
                        modelFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(partialFile.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                verifiedLength = received
                verifiedModified = modelFile.lastModified()
                outcome = DownloadOutcome.SUCCESS
            } catch (_: Exception) {
                outcome = if (handle.cancelled.get()) DownloadOutcome.CANCELLED else DownloadOutcome.FAILED
            } finally {
                handle.connection.compareAndSet(connection, null)
                connection?.disconnect()
                if (outcome != DownloadOutcome.SUCCESS) partialFile.delete()
                mainHandler.post { onComplete(outcome) }
            }
        }
        return handle
    }

    inner class DownloadHandle internal constructor() {
        internal val cancelled = AtomicBoolean(false)
        internal val connection = AtomicReference<HttpURLConnection?>(null)

        fun cancel() {
            cancelled.set(true)
            connection.get()?.disconnect()
        }
    }

    private fun digestStream(input: FileInputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val MODEL_FILE_NAME = "qwen3-0.6b-int4.litertlm"
        const val MODEL_SIZE_BYTES = 344_671_744L
        const val MODEL_SIZE_LABEL = "329 MiB"
        const val MODEL_SHA256 = "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"

        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL = 1024 * 1024L
    }
}
