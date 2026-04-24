package com.handy.core.brain

import com.handy.core.llm.LlmClient
import com.handy.core.llm.LocalAvailability
import com.handy.core.llm.LocalGenAiClient
import com.handy.core.llm.LocalTask
import com.handy.core.model.CloudProvider
import com.handy.core.model.HandySettings

/**
 * Scope §5.2 brain router. Pure Kotlin.
 *
 * The router does NOT run chat turns — it only *decides* which client
 * should handle a given [AssistantTask]. Callers (panel pipeline,
 * clipboard assist, notification listener) invoke the returned client
 * directly.
 */
class BrainRouter(
    private val claude: LlmClient,
    private val geminiCloud: LlmClient?,
    private val localGenAi: LocalGenAiClient?,
) {

    fun route(task: AssistantTask, settings: HandySettings): BrainDecision {
        // Privacy-mode: eligible local tasks go local first.
        if (settings.preferLocalWhenPossible && task.localTask != null && settings.localAiEnabled) {
            val local = localGenAi
            if (local != null) {
                return BrainDecision.UseLocal(local, task.localTask)
            }
        }

        // Task requires full-capability cloud (tools, vision, long context,
        // multi-turn history) — never local.
        if (task.requiresCloud) {
            return pickCloud(settings) ?: failOffline("No cloud provider reachable.")
        }

        // Text-only task: default is still cloud, but local is a viable
        // fallback when cloud is not configured AND the task is local-
        // supported AND the user has enabled local AI.
        val cloud = pickCloud(settings)
        if (cloud != null) return cloud

        if (task.localTask != null && settings.localAiEnabled && localGenAi != null) {
            return BrainDecision.UseLocal(localGenAi, task.localTask)
        }

        return failOffline("No brain available for this request.")
    }

    /**
     * Answer the narrower question: can [task] run locally right now?
     * Used by the clipboard / notification features that need a binary
     * decision without cloud fallback.
     */
    suspend fun canRunLocally(task: LocalTask, settings: HandySettings): Boolean {
        if (!settings.localAiEnabled) return false
        val local = localGenAi ?: return false
        if (local.isAvailable() !is LocalAvailability.Available) return false
        return local.supports(task)
    }

    private fun pickCloud(settings: HandySettings): BrainDecision.UseCloud? {
        val client = when (settings.cloudProvider) {
            CloudProvider.GEMINI -> geminiCloud
            CloudProvider.CLAUDE -> claude
        }
        return client?.let(BrainDecision::UseCloud)
    }

    private fun failOffline(msg: String): BrainDecision =
        BrainDecision.FailGracefully(msg)
}

/**
 * Describes the request well enough for the router to pick a lane.
 * The router is stateless — the pipeline fills this in per-turn.
 */
data class AssistantTask(
    /** True when the task needs tools / vision / multi-turn / long context. */
    val requiresCloud: Boolean,
    /**
     * Maps to a [LocalTask] when the task is known-local-supported.
     * Null when the task is chat-like (panel turn) — router won't pick
     * local even in privacy mode.
     */
    val localTask: LocalTask? = null,
)

sealed class BrainDecision {
    data class UseCloud(val client: LlmClient) : BrainDecision()
    data class UseLocal(val client: LocalGenAiClient, val localTask: LocalTask) : BrainDecision()
    data class FailGracefully(val message: String) : BrainDecision()
}
