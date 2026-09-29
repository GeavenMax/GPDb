package com.gpdb.android.data.ai

import com.gpdb.android.data.settings.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class LLMTranslationService(
    private val appSettingsRepository: AppSettingsRepository
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        val apiKey = appSettingsRepository.llmApiKeyFlow.first()
        val baseUrl = appSettingsRepository.llmBaseUrlFlow.first()
        val model = appSettingsRepository.llmModelFlow.first()
        val targetLanguage = appSettingsRepository.llmTargetLanguageFlow.first()
        val systemPromptTemplate = appSettingsRepository.llmSystemPromptFlow.first()

        if (apiKey.isBlank()) {
            throw IllegalStateException("请先在设置中配置 API Key")
        }

        val url = if (baseUrl.endsWith("/")) {
            "${baseUrl}chat/completions"
        } else {
            "${baseUrl}/chat/completions"
        }

        val systemPrompt = systemPromptTemplate.replace("{Target_Language}", targetLanguage)

        val jsonBody = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            }
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("翻译失败: HTTP ${response.code} ${response.message}")
            }
            val responseBody = response.body?.string() ?: throw IOException("返回数据为空")
            try {
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    return@withContext message.getString("content").trim()
                } else {
                    throw IOException("解析失败: 未找到 choices")
                }
            } catch (e: Exception) {
                throw IOException("解析失败: ${e.message}")
            }
        }
    }
}
