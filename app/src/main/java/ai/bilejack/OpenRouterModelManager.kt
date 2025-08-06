package ai.bilejack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class OpenRouterModelManager(private val context: Context) {
    private val tag = "OpenRouterModelManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("bilejack_llm", Context.MODE_PRIVATE)
    private val openRouter = OpenRouterProvider(context)

    companion object {
        private const val KEY_SELECTED_MODEL = "selected_model"
    }

    init {
        if (getSelectedModel().isEmpty()) {
            val defaultModel = getDefaultModel()
            Log.d(tag, "Setting default model: $defaultModel")
            setSelectedModel(defaultModel)
        } else {
            openRouter.setSelectedModel(getSelectedModel())
        }
    }

    private fun getDefaultModel(): String {
        return context.getString(R.string.openrouter_default_model)
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, "") ?: ""
    }

    fun setSelectedModel(modelId: String) {
        if (openRouter.getAvailableModels().contains(modelId)) {
            prefs.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
            openRouter.setSelectedModel(modelId)
            Log.d(tag, "💾 Selected LLM model: $modelId")
        } else {
            Log.e(tag, "❌ Attempted to select invalid model ID: $modelId")
        }
    }

    fun getAvailableModels(): List<String> {
        return openRouter.getAvailableModels()
    }

    fun isConfigured(): Boolean {
        return openRouter.isConfigured()
    }

    fun getModelSummary(): String {
        val selectedModel = getSelectedModel()
        return if (openRouter.isConfigured() && selectedModel.isNotBlank()) {
            "✅ $selectedModel"
        } else if (openRouter.isConfigured()) {
            val defaultModel = getDefaultModel()
            "⚙️ Default: $defaultModel"
        } else {
            "❌ API key required"
        }
    }

    suspend fun testConnection(): Boolean {
        return if (isConfigured()) {
            openRouter.testConnection()
        } else {
            false
        }
    }

    suspend fun sendMessage(userMessage: String): String {
        if (!openRouter.isConfigured()) {
            throw Exception("OpenRouter not configured")
        }

        if (getSelectedModel().isEmpty()) {
            throw Exception("No model selected")
        }

        return openRouter.sendMessage(userMessage)
    }
}
