package vn.gotunhien.keyboard.translation

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class LocalTranslator(context: Context) {
    private val appContext = context.applicationContext
    private val repository = ModelRepository(appContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mlKitTranslators = mutableMapOf<TranslationTarget, Translator>()

    @Volatile
    private var engine: Engine? = null

    fun translate(
        sourceText: String,
        target: TranslationTarget,
        onComplete: (Result<TranslationCandidates>) -> Unit,
    ) {
        executor.execute {
            val result = try {
                Result.success(
                    TranslationPipeline.translate(
                        sourceText = sourceText,
                        translateBase = { text -> translateWithMlKit(text, target) },
                        shouldNaturalize = repository::hasVerifiedModel,
                        naturalize = { source, draft -> naturalize(source, draft, target) },
                    ),
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
            mlKitTranslators.values.forEach(Translator::close)
            mlKitTranslators.clear()
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

    private fun translateWithMlKit(sourceText: String, target: TranslationTarget): String {
        val targetLanguage = when (target) {
            TranslationTarget.ENGLISH -> TranslateLanguage.ENGLISH
            TranslationTarget.RUSSIAN -> TranslateLanguage.RUSSIAN
        }
        val translator = mlKitTranslators.getOrPut(target) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.VIETNAMESE)
                    .setTargetLanguage(targetLanguage)
                    .build(),
            )
        }
        Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()))
        return Tasks.await(translator.translate(sourceText)).trim()
    }

    private fun naturalize(sourceText: String, baseTranslation: String, target: TranslationTarget): String {
        val activeEngine = engine ?: createEngine().also { engine = it }
        return activeEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(TranslationPolicy.systemInstruction(target)),
                samplerConfig = SamplerConfig(topK = 1, topP = 0.85, temperature = 0.2),
                maxOutputToken = MAX_OUTPUT_TOKENS,
                thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0),
            ),
        ).use { conversation ->
            extractText(conversation, TranslationPolicy.naturalizationInput(target, sourceText, baseTranslation))
        }
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
