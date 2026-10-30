package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiRepository"

/**
 * Robust repository communicating with the secure Gemini backend proxy
 * and with direct Google Gemini API fallback when configured.
 * Operates with strict zero-history retention: data resides exclusively in volatile memory.
 */
class GeminiRepository {

    companion object {
        // Currently supported Gemini models (free-tier eligible and fast)
        const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.8-flash"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val backendUrl: String
        get() {
            val configured = runCatching {
                BuildConfig::class.java.getField("BACKEND_URL").get(null) as? String
            }.getOrNull()
            return if (!configured.isNullOrBlank()) configured.trimEnd('/') else "http://10.0.2.2:3000"
        }

    private val directApiKey: String
        get() {
            val key = runCatching {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            }.getOrNull().orEmpty()
            return if (key == "YOUR_GEMINI_API_KEY" || key.isBlank()) "" else key
        }

    private val systemInstruction = """
        You are the AI Assistant inside NK APP.
        - Fluently understand and respond in English, Hindi (हिंदी), and Hinglish (Hindi written using Latin characters). Match the user's chosen language naturally.
        - Provide high quality assistance in software coding, mathematics, creative writing, science, and general queries.
        - Format responses with clean Markdown: headers, numbered/bulleted lists, and code blocks with language indicators.
        - Be concise, helpful, and polite.
        - Privacy: You retain zero history between sessions.
    """.trimIndent()

    /**
     * Sends a message with in-memory multi-turn context and streams responses safely.
     */
    fun sendMessageStream(prompt: String, history: List<ChatMessage>): Flow<String> = flow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Request started: [promptLength=${prompt.length}, historyCount=${history.size}]")

        var streamEmittedAnyToken = false

        // 1. Try Backend Proxy if reachable
        try {
            var backendSucceeded = false
            tryBackendCall(prompt, history) { chunk ->
                streamEmittedAnyToken = true
                backendSucceeded = true
                emit(chunk)
            }
            if (backendSucceeded) {
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Backend request completed successfully in ${duration}ms")
                return@flow
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Backend call bypassed: [duration=${duration}ms, errorCategory=${e.javaClass.simpleName}]")
            // If tokens were already emitted to UI, do not switch pipelines midway
            if (streamEmittedAnyToken) {
                emit("\n[Connection interrupted]")
                return@flow
            }
        }

        // 2. Fallback: Direct Gemini API call if GEMINI_API_KEY is configured
        if (directApiKey.isNotBlank()) {
            try {
                Log.d(TAG, "Attempting direct Gemini API call with model: $PRIMARY_MODEL")
                var directSucceeded = false
                tryDirectGeminiCall(prompt, history, PRIMARY_MODEL) { chunk ->
                    streamEmittedAnyToken = true
                    directSucceeded = true
                    emit(chunk)
                }

                if (directSucceeded) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Direct Gemini call completed in ${duration}ms")
                    return@flow
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelUnavailableException) {
                // Primary model overloaded or unavailable, retry with fallback model
                Log.w(TAG, "Primary model failed (${e.message}). Retrying with $FALLBACK_MODEL")
                try {
                    tryDirectGeminiCall(prompt, history, FALLBACK_MODEL) { chunk ->
                        streamEmittedAnyToken = true
                        emit(chunk)
                    }
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Fallback Gemini call completed in ${duration}ms")
                    return@flow
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback direct call error: ${e2.javaClass.simpleName}")
                    throw mapExceptionToUserFriendly(e2)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct Gemini call error: ${e.javaClass.simpleName}")
                throw mapExceptionToUserFriendly(e)
            }
        }

        // 3. Neither backend nor direct API succeeded
        val duration = System.currentTimeMillis() - startTime
        Log.w(TAG, "No viable connection path [duration=${duration}ms]")
        throw UserFacingException("AI service is temporarily unavailable. Check your connection or backend configuration.")
    }.flowOn(Dispatchers.IO)

    /**
     * Executes backend proxy request (supporting streaming and unary fallback)
     */
    private suspend fun tryBackendCall(
        prompt: String,
        history: List<ChatMessage>,
        onChunk: suspend (String) -> Unit
    ) {
        val endpoint = "$backendUrl/api/chat/stream"
        val jsonPayload = buildJsonRequestBody(prompt, history)

        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "text/event-stream, application/json")
            .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response: Response = client.newCall(request).execute()
        val statusCode = response.code
        val isSuccessful = response.isSuccessful

        Log.d(TAG, "Backend HTTP response: $statusCode")

        if (!isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            response.close()
            when (statusCode) {
                404 -> throw ModelUnavailableException("Backend endpoint not found")
                429 -> throw UserFacingException("AI usage limit reached. Please try again later.")
                503 -> throw UserFacingException("AI service is temporarily unavailable. Please try again in a moment.")
                else -> {
                    val message = runCatching { JSONObject(errorBody).optString("error") }.getOrNull()
                    throw UserFacingException(message?.ifBlank { null } ?: "AI service returned HTTP $statusCode")
                }
            }
        }

        val body = response.body ?: throw UserFacingException("The AI returned an empty response. Please try again.")
        val contentType = response.header("Content-Type").orEmpty()

        if (contentType.contains("text/event-stream")) {
            // Read SSE
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?
            var hasReadAnyContent = false

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data.isNotBlank()) {
                        val text = parseGeminiChunkJson(data)
                        if (text.isNotEmpty()) {
                            hasReadAnyContent = true
                            onChunk(text)
                        }
                    }
                }
            }
            body.close()
            if (!hasReadAnyContent) {
                throw UserFacingException("The AI returned an empty response. Please try again.")
            }
        } else {
            // Read regular JSON response
            val raw = body.string()
            body.close()
            if (raw.isBlank()) throw UserFacingException("The AI returned an empty response. Please try again.")
            val json = JSONObject(raw)
            val text = json.optString("text", "")
            if (text.isBlank()) throw UserFacingException("The AI returned an empty response. Please try again.")
            onChunk(text)
        }
    }

    /**
     * Direct call to official Google Gemini API endpoint
     */
    private suspend fun tryDirectGeminiCall(
        prompt: String,
        history: List<ChatMessage>,
        modelName: String,
        onChunk: suspend (String) -> Unit
    ) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$directApiKey"
        val requestBody = buildGeminiDirectPayload(prompt, history)

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val statusCode = response.code
        Log.d(TAG, "Direct Gemini HTTP status: $statusCode for model $modelName")

        if (!response.isSuccessful) {
            val errString = response.body?.string().orEmpty()
            response.close()
            when (statusCode) {
                404 -> throw ModelUnavailableException("Model $modelName unavailable")
                429 -> throw UserFacingException("AI usage limit reached. Please try again later.")
                503 -> throw ModelUnavailableException("Model $modelName overloaded (HTTP 503)")
                400 -> throw UserFacingException("Invalid request format sent to AI service.")
                401, 403 -> throw UserFacingException("AI service setup is incomplete: Check API credentials.")
                else -> throw UserFacingException("AI service error (HTTP $statusCode)")
            }
        }

        val rawBody = response.body?.string().orEmpty()
        response.close()

        if (rawBody.isBlank()) {
            throw UserFacingException("The AI returned an empty response. Please try again.")
        }

        val parsedText = extractCandidateText(rawBody)
        if (parsedText.isBlank()) {
            throw UserFacingException("The AI returned an empty response. Please try again.")
        }

        onChunk(parsedText)
    }

    private fun extractCandidateText(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            
            // Check finish reason
            val finishReason = firstCandidate.optString("finishReason", "")
            if (finishReason == "SAFETY" || finishReason == "BLOCKLIST") {
                return "Response was withheld due to safety settings."
            }

            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val partText = parts.getJSONObject(i).optString("text", "")
                sb.append(partText)
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing error: ${e.message}")
            ""
        }
    }

    private fun parseGeminiChunkJson(data: String): String {
        return try {
            val obj = JSONObject(data)
            val candidates = obj.optJSONArray("candidates") ?: return data
            if (candidates.length() == 0) return ""
            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            sb.toString()
        } catch (e: Exception) {
            data
        }
    }

    private fun buildJsonRequestBody(prompt: String, history: List<ChatMessage>): JSONObject {
        return JSONObject().apply {
            put("prompt", prompt)
            val histArray = JSONArray()
            for (msg in history.filter { it.sender != ChatSender.SYSTEM && !it.isError && it.text.isNotBlank() }) {
                histArray.put(JSONObject().apply {
                    put("role", if (msg.sender == ChatSender.USER) "user" else "model")
                    put("text", msg.text)
                })
            }
            put("history", histArray)
        }
    }

    private fun buildGeminiDirectPayload(prompt: String, history: List<ChatMessage>): JSONObject {
        val contentsArray = JSONArray()
        for (msg in history.filter { it.sender != ChatSender.SYSTEM && !it.isError && it.text.isNotBlank() }) {
            contentsArray.put(JSONObject().apply {
                put("role", if (msg.sender == ChatSender.USER) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        })

        return JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }
    }

    private fun mapExceptionToUserFriendly(e: Throwable): Throwable {
        return when (e) {
            is UserFacingException -> e
            is UnknownHostException, is ConnectException -> {
                UserFacingException("Check your internet connection and try again.")
            }
            is SocketTimeoutException -> {
                UserFacingException("AI service request timed out. Please try again.")
            }
            is IOException -> {
                UserFacingException("Check your internet connection and try again.")
            }
            else -> {
                UserFacingException("AI service is temporarily unavailable. Please try again.")
            }
        }
    }
}

class UserFacingException(message: String) : Exception(message)
class ModelUnavailableException(message: String) : Exception(message)
