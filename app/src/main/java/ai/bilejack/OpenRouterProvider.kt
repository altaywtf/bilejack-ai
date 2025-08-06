package ai.bilejack

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OpenRouterProvider(private val context: Context) {
    private val tag = "OpenRouterProvider"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    private val moshi =
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val web_search_options: WebSearchOptions? = null,
    )

    data class WebSearchOptions(
        val search_context_size: String,
    )

    data class ChatMessage(
        val role: String,
        val content: String,
    )

    data class ChatResponse(
        val choices: List<Choice>,
    )

    data class Choice(
        val message: ChatMessage,
    )

    data class ErrorResponse(
        val error: Error,
    )

    data class Error(
        val message: String,
    )

    fun isConfigured(): Boolean {
        val apiKey = getApiKey()
        return apiKey.isNotBlank()
    }

    private fun getApiKey(): String {
        return context.getString(R.string.openrouter_api_key)
    }

    private fun getModel(): String {
        val prefs = context.getSharedPreferences("bilejack_llm", Context.MODE_PRIVATE)
        val savedModel = prefs.getString("selected_model_v2", "") ?: ""
        return if (savedModel.isNotBlank()) {
            savedModel
        } else {
            context.getString(R.string.openrouter_default_model)
        }
    }

    private fun getSystemPrompt(): String {
        return context.getString(R.string.openrouter_system_prompt)
    }

    private fun getWebSearchContextSize(): String {
        return context.getString(R.string.openrouter_web_search_context_size)
    }

    suspend fun sendMessage(userMessage: String): String {
        val apiKey = getApiKey()

        if (!isConfigured()) {
            throw Exception("OpenRouter API key not configured")
        }

        try {
            val requestData =
                ChatRequest(
                    model = getModel(),
                    messages =
                        listOf(
                            ChatMessage(
                                "system",
                                getSystemPrompt(),
                            ),
                            ChatMessage("user", userMessage),
                        ),
                    web_search_options = WebSearchOptions(getWebSearchContextSize()), // TODO: Make this dynamic
                )

            Log.d(tag, "🤖 Sending request to OpenRouter with model: ${getModel()}")
            val requestJson = moshi.adapter(ChatRequest::class.java).toJson(requestData)
            val requestBody = requestJson.toRequestBody("application/json".toMediaType())

            val request =
                Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(tag, "❌ OpenRouter API request failed with status ${response.code}")
                val errorResponse = moshi.adapter(ErrorResponse::class.java).fromJson(responseBody ?: "")
                val errorMessage = errorResponse?.error?.message ?: "HTTP ${response.code}"
                throw Exception("OpenRouter API Error (${response.code}): $errorMessage")
            }

            val chatResponse = moshi.adapter(ChatResponse::class.java).fromJson(responseBody ?: "")

            val llmMessage =
                chatResponse?.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("Invalid OpenRouter response format")

            val content = llmMessage.trim()

            if (content.isEmpty()) {
                Log.e(tag, "❌ Empty response from LLM - check API usage or model")
                Log.e(tag, "🔍 Full response for debugging: $responseBody")
                throw Exception(
                    "Empty response from LLM - model may be returning citations/annotations instead of text",
                )
            }

            Log.d(tag, "✅ OpenRouter response received successfully (${content.length} chars)")
            return content
        } catch (e: IOException) {
            Log.e(tag, "❌ Network error during OpenRouter API call", e)
            throw Exception("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "❌ Error calling OpenRouter API: ${e.message}", e)
            throw e
        }
    }

    fun getConfigurationStatus(): String {
        if (!isConfigured()) {
            return "❌ API key required"
        }
        return "✅ ${getModel()}"
    }

    fun getAvailableModels(): List<String> {
        val modelsString = context.getString(R.string.openrouter_available_models)
        return modelsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    suspend fun testConnection(): Boolean {
        return try {
            sendMessage("Hello")
            true
        } catch (e: Exception) {
            Log.e(tag, "Test failed: ${e.message}", e)
            false
        }
    }
}
