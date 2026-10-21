package com.hmtautosolutions.hmttvocale.ai

import com.hmtautosolutions.hmttvocale.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Optional online AI for general questions. It can only answer with text; it never executes actions. */
interface OnlineAiProvider {
    fun ask(text: String, lang: String): Result<String>
}

/** Works with any OpenAI-compatible /chat/completions endpoint configured by the user on the device. */
class OpenAiCompatibleProvider(private val prefs: Prefs) : OnlineAiProvider {
    override fun ask(text: String, lang: String): Result<String> = runCatching {
        require(prefs.isAiConfigured) { "AI not configured" }
        val language = when (lang) { "en" -> "English"; "ar" -> "Arabic"; else -> "French" }
        val body = JSONObject()
            .put("model", prefs.aiModel)
            .put("max_tokens", 200)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "You are HMT Vocale, an in-car voice assistant created by HMT Auto Solutions. " +
                        "Answer in $language in at most two short sentences, suitable to be read aloud while driving. " +
                        "You cannot control the car or the phone; never claim to have performed an action."))
                .put(JSONObject().put("role", "user").put("content", text)))

        val conn = URL(prefs.aiEndpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${prefs.aiApiKey}")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${resp.take(200)}")
            JSONObject(resp).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } finally {
            conn.disconnect()
        }
    }
}
