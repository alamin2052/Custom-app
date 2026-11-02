package com.agentcommander.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Persistent storage for agents and settings. */
object Store {
    private lateinit var sp: SharedPreferences
    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> get() = _agents

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("agentcmd", Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val raw = sp.getString("agents", "[]") ?: "[]"
        val list = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Agent.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
        // Fail-safe: an agent is NEVER auto-resumed after the app/process restarts.
        _agents.value = list.map {
            if (it.status in ACTIVE_STATUSES) it.copy(status = AgentStatus.STOPPED) else it
        }
    }

    private fun save() {
        val arr = JSONArray(_agents.value.map { it.toJson() })
        sp.edit().putString("agents", arr.toString()).apply()
    }

    fun get(id: String): Agent? = _agents.value.firstOrNull { it.id == id }

    fun add(name: String, instructions: String): String {
        val id = UUID.randomUUID().toString()
        _agents.update { it + Agent(id = id, name = name, instructions = instructions) }
        save()
        return id
    }

    fun update(id: String, f: (Agent) -> Agent) {
        _agents.update { list -> list.map { if (it.id == id) f(it) else it } }
        save()
    }

    fun delete(id: String) {
        _agents.update { list -> list.filter { it.id != id } }
        save()
    }

    fun log(id: String, msg: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        update(id) { it.copy(log = (it.log + "$stamp  $msg").takeLast(200)) }
    }

    fun stopAllActive() {
        _agents.update { list ->
            list.map {
                if (it.status in ACTIVE_STATUSES) it.copy(status = AgentStatus.STOPPED, question = "", confirm = "") else it
            }
        }
        save()
    }

    // ---- settings ----
    fun apiKey(): String = sp.getString("api_key", "") ?: ""
    fun model(): String = sp.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
    fun maxSteps(): Int = sp.getInt("max_steps", 60)

    fun saveSettings(apiKey: String, model: String, maxSteps: Int) {
        sp.edit()
            .putString("api_key", apiKey.trim())
            .putString("model", model.trim().ifEmpty { "claude-sonnet-5-5" })
            .putInt("max_steps", maxSteps.coerceIn(1, 500))
            .apply()
    }
}
