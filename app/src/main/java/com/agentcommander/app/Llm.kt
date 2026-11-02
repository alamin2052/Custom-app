package com.agentcommander.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One piece of a prompt: either text or a base64 JPEG image. */
class Part(val text: String? = null, val imageB64: String? = null)

/** Minimal client for the Anthropic Messages API. */
object Llm {
    @Volatile
    private var current: HttpURLConnection? = null

    /** Called by the kill switch so an in-flight request is dropped immediately. */
    fun abort() {
        try {
            current?.disconnect()
        } catch (_: Exception) {
        }
    }

    suspend fun call(system: String, parts: List<Part>, maxTokens: Int = 1024): String =
        withContext(Dispatchers.IO) {
            val key = Store.apiKey()
            if (key.isBlank()) throw IllegalStateException("No API key set. Open Settings and add your key.")

            val content = JSONArray()
            for (p in parts) {
                if (p.imageB64 != null) {
                    content.put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", p.imageB64)
                        )
                    )
                } else if (!p.text.isNullOrEmpty()) {
                    content.put(JSONObject().put("type", "text").put("text", p.text))
                }
            }
            val body = JSONObject()
                .put("model", Store.model())
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", content))
                )

            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            current = conn
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 90000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", key)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    throw RuntimeException("AI service error $code: ${text.take(300)}")
                }
                val arr = JSONObject(text).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.optString("text"))
                }
                sb.toString()
            } finally {
                conn.disconnect()
                if (current === conn) current = null
            }
        }

    /** Pulls the first JSON object out of a model reply. */
    fun extractJson(reply: String): JSONObject {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("The AI reply was not understandable.")
        return JSONObject(reply.substring(start, end + 1))
    }
}
