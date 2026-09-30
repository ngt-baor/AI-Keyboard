package vn.gotunhien.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import vn.gotunhien.keyboard.translation.DownloadOutcome
import vn.gotunhien.keyboard.translation.ModelRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var repository: ModelRepository
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var deleteButton: Button
    private val mainHandler = Handler(Looper.getMainLooper())
    private val verificationExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var downloadHandle: ModelRepository.DownloadHandle? = null
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ModelRepository(this)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshModelStatus()
    }

    override fun onDestroy() {
        destroyed = true
        downloadHandle?.cancel()
        repository.close()
        verificationExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTextColor(0xFF17212B.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Bàn phím tiếng Việt có dịch cục bộ sang tiếng Anh hoặc tiếng Nga."
            textSize = 16f
            setTextColor(0xFF45515C.toInt())
            setPadding(0, dp(8), 0, dp(20))
        })

        content.addView(sectionTitle("1. Bật và chọn bàn phím"))
        content.addView(actionButton("Mở cài đặt bàn phím") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(actionButton("Chọn Gõ Tự Nhiên") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        content.addView(sectionTitle("2. Tùy chọn làm câu dịch tự nhiên hơn"))
        content.addView(TextView(this).apply {
            text = "Google ML Kit dịch nền trên điện thoại; gói ngôn ngữ được tải từ Google lần đầu qua Wi-Fi. Tải thêm mô hình ${ModelRepository.MODEL_SIZE_LABEL} để Qwen chỉnh câu tự nhiên hơn. Tin nhắn không được gửi lên máy chủ."
            textSize = 14f
            setTextColor(0xFF45515C.toInt())
        })

        statusText = TextView(this).apply {
            text = "Đang kiểm tra mô hình…"
            textSize = 14f
            setTextColor(0xFF45515C.toInt())
            setPadding(0, dp(12), 0, dp(4))
        }
        content.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        content.addView(progressBar, LinearLayout.LayoutParams(-1, dp(8)).apply {
            topMargin = dp(4)
            bottomMargin = dp(8)
        })

        downloadButton = actionButton("Tải mô hình chỉnh câu") { startModelDownload() }
        content.addView(downloadButton)
        deleteButton = actionButton("Xóa mô hình khỏi điện thoại") { deleteModel() }
        content.addView(deleteButton)

        content.addView(TextView(this).apply {
            text = "Bản dịch nền cần kết nối mạng khi tải gói ngôn ngữ lần đầu; sau đó ML Kit và Qwen đều xử lý trên điện thoại. Kết quả chỉ được chèn vào ô chat sau khi bạn bấm Dùng; ứng dụng không tự gửi tin."
            textSize = 13f
            setTextColor(0xFF66727D.toInt())
            setPadding(0, dp(20), 0, 0)
        })

        return ScrollView(this).apply {
            setBackgroundColor(0xFFF4F6F8.toInt())
            addView(content)
        }
    }

    private fun refreshModelStatus() {
        statusText.text = "Đang kiểm tra mô hình…"
        deleteButton.isEnabled = false
        verificationExecutor.execute {
            val ready = repository.hasVerifiedModel()
            mainHandler.post {
                if (destroyed || downloadHandle != null) return@post
                statusText.text = if (ready) {
                    "Mô hình chỉnh câu đã sẵn sàng. Cả hai bước đều chạy trên điện thoại."
                } else {
                    "Chưa có mô hình chỉnh câu. Vẫn dịch được bằng Google ML Kit."
                }
                downloadButton.text = if (ready) "Tải lại mô hình chỉnh câu" else "Tải mô hình chỉnh câu (${ModelRepository.MODEL_SIZE_LABEL})"
                deleteButton.isEnabled = ready
            }
        }
    }

    private fun startModelDownload() {
        if (downloadHandle != null) {
            downloadHandle?.cancel()
            statusText.text = "Đang hủy tải…"
            return
        }

        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        downloadButton.text = "Hủy tải"
        deleteButton.isEnabled = false
        statusText.text = "Đang tải mô hình…"
        downloadHandle = repository.download(
            onProgress = { downloaded ->
                if (!destroyed) {
                    progressBar.progress = ((downloaded * 100) / ModelRepository.MODEL_SIZE_BYTES).toInt()
                    statusText.text = "Đã tải ${formatMiB(downloaded)} / ${ModelRepository.MODEL_SIZE_LABEL}"
                }
            },
            onComplete = { outcome ->
                downloadHandle = null
                if (destroyed) return@download
                progressBar.visibility = View.GONE
                when (outcome) {
                    DownloadOutcome.SUCCESS -> statusText.text = "Tải xong. Mô hình đã qua kiểm tra SHA-256."
                    DownloadOutcome.CANCELLED -> statusText.text = "Đã hủy tải; bản tải dở đã được xóa."
                    DownloadOutcome.FAILED -> statusText.text = "Tải chưa thành công hoặc tệp không hợp lệ. Hãy thử lại khi mạng ổn định."
                }
                refreshModelStatus()
            },
        )
    }

    private fun deleteModel() {
        val deleted = repository.deleteModel()
        statusText.text = if (deleted) "Đã xóa mô hình khỏi điện thoại." else "Không xóa được mô hình."
        deleteButton.isEnabled = false
        downloadButton.text = "Tải mô hình (${ModelRepository.MODEL_SIZE_LABEL})"
    }

    private fun sectionTitle(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(0xFF17212B.toInt())
        setPadding(0, dp(12), 0, dp(8))
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun formatMiB(bytes: Long): String = "${bytes / (1024 * 1024)} MiB"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
