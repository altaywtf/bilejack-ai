package wtf.altay.gptsmsrelay

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class GptClient(private val context: Context) {
    private val tag = "GptClient"

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
        val max_tokens: Int
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

    private fun getApiKey(): String {
        return context.getString(R.string.openai_api_key)
    }

    private fun getGptModel(): String {
        return context.getString(R.string.gpt_model)
    }

    private fun getMaxTokens(): Int {
        return context.resources.getInteger(R.integer.gpt_max_tokens)
    }

    private fun getSystemPrompt(): String {
        return context.getString(R.string.gpt_system_prompt)
    }

    suspend fun sendMessage(userMessage: String): String {
        val apiKey = getApiKey()
        Log.d(tag, "Checking API key configuration...")

        if (apiKey.isBlank()) {
            Log.e(tag, "API key not configured properly in config.xml")
            throw Exception("OpenAI API key not configured in res/values/config.xml")
        }

        Log.d(tag, "API key found, length: ${apiKey.length} characters")
        Log.d(tag, "API key starts with: ${apiKey.take(10)}...")

        try {
            val requestData =
                ChatRequest(
                    model = getGptModel(),
                    messages =
                        listOf(
                            ChatMessage("system", getSystemPrompt()),
                            ChatMessage("user", userMessage),
                        ),
                    max_tokens = getMaxTokens(),
                )

            Log.d(tag, "Preparing request with model: ${getGptModel()}, max_tokens: ${getMaxTokens()}")
            val requestJson = moshi.adapter(ChatRequest::class.java).toJson(requestData)
            val requestBody = requestJson.toRequestBody("application/json".toMediaType())

            val request =
                Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

            Log.d(tag, "Sending request to OpenAI API...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(tag, "Response received - Status: ${response.code}, Body length: ${responseBody?.length ?: 0}")

            if (!response.isSuccessful) {
                Log.e(tag, "API request failed with status ${response.code}")
                Log.e(tag, "Response body: $responseBody")

                val errorResponse = moshi.adapter(ErrorResponse::class.java).fromJson(responseBody ?: "")
                val errorMessage = errorResponse?.error?.message ?: "HTTP ${response.code}"
                throw Exception("API Error (${response.code}): $errorMessage")
            }

            val chatResponse = moshi.adapter(ChatResponse::class.java).fromJson(responseBody ?: "")
            val gptMessage =
                chatResponse?.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("Invalid response format - no message content")

            Log.d(tag, "GPT Response received successfully: $gptMessage")
            return gptMessage.trim()
        } catch (e: IOException) {
            Log.e(tag, "Network/IO error during API call", e)
            throw Exception("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Error calling GPT API: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            Log.d(tag, "Running connection test with 'Hello' message...")
            sendMessage("Hello")
            Log.d(tag, "Connection test successful!")
            true
        } catch (e: Exception) {
            Log.e(tag, "Connection test failed: ${e.javaClass.simpleName}: ${e.message}", e)
            false
        }
    }

    fun isApiKeyConfigured(): Boolean {
        val apiKey = getApiKey()
        return apiKey.isNotBlank()
    }
}
