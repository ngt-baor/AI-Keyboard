package vn.gotunhien.keyboard.translation

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class LocalTranslator(context: Context) {
    private val appContext = context.applicationContext
    private val repository = ModelRepository(appContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var engine: Engine? = null

    fun translate(
        sourceText: String,
        target: TranslationTarget,
        onComplete: (Result<String>) -> Unit,
    ) {
        executor.execute {
            val result = try {
                check(repository.hasVerifiedModel())
                val activeEngine = engine ?: createEngine().also { engine = it }
                Result.success(
                    activeEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(TranslationPolicy.systemInstruction(target)),
                            samplerConfig = SamplerConfig(topK = 1, topP = 0.85, temperature = 0.2),
                            maxOutputToken = MAX_OUTPUT_TOKENS,
                            thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0),
                        ),
                    ).use { conversation -> extractText(conversation, sourceText) },
                )
            } catch (failure: Exception) {
                Result.failure(failure)
            } catch (failure: LinkageError) {
                Result.failure(failure)
            }
            mainHandler.post { onComplete(result) }
        }
    }

    fun close() {
        executor.execute {
            engine?.close()
            engine = null
        }
        executor.shutdown()
    }

    private fun createEngine(): Engine {
        var gpuEngine: Engine? = null
        try {
            gpuEngine = Engine(
                EngineConfig(
                    modelPath = repository.modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = appContext.cacheDir.absolutePath,
                ),
            )
            gpuEngine.initialize()
            return gpuEngine
        } catch (_: Exception) {
            runCatching { gpuEngine?.close() }
        } catch (_: LinkageError) {
            runCatching { gpuEngine?.close() }
        }

        return Engine(
            EngineConfig(
                modelPath = repository.modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath,
            ),
        ).apply { initialize() }
    }

    private fun extractText(conversation: Conversation, sourceText: String): String {
        val response = conversation.sendMessage(sourceText)
        return response.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
            .trim()
            .removeSurrounding("\"", "\"")
            .removeSurrounding("“", "”")
    }

    companion object {
        private const val MAX_OUTPUT_TOKENS = 160
    }
}
