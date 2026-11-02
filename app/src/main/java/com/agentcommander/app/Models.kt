package com.agentcommander.app

import org.json.JSONArray
import org.json.JSONObject

enum class AgentStatus {
    DRAFT,              // created, nothing learned yet
    LEARNING,           // owner is demonstrating, agent is watching
    CHECKING,           // agent is checking whether it can do the job
    AWAITING_APPROVAL,  // agent says it can do it and waits for the owner's OK
    RUNNING,
    PAUSED,
    NEEDS_HELP,         // agent is confused or hit an error and is asking the owner
    STOPPED,
    DONE
}

val ACTIVE_STATUSES = setOf(
    AgentStatus.LEARNING,
    AgentStatus.CHECKING,
    AgentStatus.AWAITING_APPROVAL,
    AgentStatus.RUNNING,
    AgentStatus.PAUSED,
    AgentStatus.NEEDS_HELP
)

data class Step(
    val index: Int,
    val app: String,
    val action: String,
    val target: String,
    val text: String,
    val screenshot: String? = null
) {
    fun describe(): String {
        val t = if (text.isNotEmpty()) " text=\"$text\"" else ""
        return "$index. [$app] $action \"$target\"$t"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("i", index)
        .put("app", app)
        .put("action", action)
        .put("target", target)
        .put("text", text)
        .put("shot", screenshot ?: "")

    companion object {
        fun fromJson(o: JSONObject): Step = Step(
            index = o.optInt("i"),
            app = o.optString("app"),
            action = o.optString("action"),
            target = o.optString("target"),
            text = o.optString("text"),
            screenshot = o.optString("shot").ifEmpty { null }
        )
    }
}

data class Agent(
    val id: String,
    val name: String,
    val instructions: String,
    val status: AgentStatus = AgentStatus.DRAFT,
    val steps: List<Step> = emptyList(),
    val plan: String = "",
    val question: String = "",
    val confirm: String = "",
    val log: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("instructions", instructions)
        .put("status", status.name)
        .put("plan", plan)
        .put("steps", JSONArray(steps.map { it.toJson() }))
        .put("log", JSONArray(log))

    companion object {
        fun fromJson(o: JSONObject): Agent {
            val stepsArr = o.optJSONArray("steps") ?: JSONArray()
            val logArr = o.optJSONArray("log") ?: JSONArray()
            return Agent(
                id = o.getString("id"),
                name = o.optString("name"),
                instructions = o.optString("instructions"),
                status = runCatching { AgentStatus.valueOf(o.optString("status")) }
                    .getOrDefault(AgentStatus.DRAFT),
                plan = o.optString("plan"),
                steps = (0 until stepsArr.length()).map { Step.fromJson(stepsArr.getJSONObject(it)) },
                log = (0 until logArr.length()).map { logArr.getString(it) }
            )
        }
    }
}
