package com.handy.runtime.audit

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ScrollDirection
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeRunEvent
import com.handy.core.agent.RecipeRunResult
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.VerificationResult
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecipeAuditObserverTest {

    @Test fun `failed verification appends recipe step failed audit event`() = runTest {
        val store = RecordingAuditStore()
        val observer = RecipeAuditObserver(store, clock = { 42L })
        val plan = recipePlan()
        val step = plan.steps.single()

        observer.onEvent(RecipeRunEvent.Started(plan))
        observer.onEvent(
            RecipeRunEvent.StepVerified(
                step = step,
                result = VerificationResult.Failed("screen-did-not-change"),
                verifierName = "ScreenChangedVerifier",
            ),
        )
        observer.onEvent(
            RecipeRunEvent.Finished(
                plan,
                RecipeRunResult.Failed(stepId = step.id, reason = "screen-did-not-change"),
            ),
        )

        val event = store.events.single()
        assertThat(event.timestampEpochMs).isEqualTo(42L)
        assertThat(event.action).isEqualTo(AuditAction.RecipeStepFailed)
        assertThat(event.requestId).isEqualTo("recipe:test_recipe:tap")
        assertThat(event.targetApp).isEqualTo("Example")
        assertThat(event.semanticTarget).contains("tap:Tap Continue")
        assertThat(event.result).isEqualTo(AuditResult.Failed("screen-did-not-change"))
        assertThat(event.failureReason).isEqualTo("screen-did-not-change")
        assertThat(event.verifiedBy).isEqualTo("ScreenChangedVerifier")
    }

    @Test fun `verified recipe completion appends recipe completed audit event`() = runTest {
        val store = RecordingAuditStore()
        val observer = RecipeAuditObserver(store, clock = { 99L })
        val plan = recipePlan()
        val step = plan.steps.single()

        observer.onEvent(RecipeRunEvent.Started(plan))
        observer.onEvent(
            RecipeRunEvent.StepVerified(
                step = step,
                result = VerificationResult.Verified,
                verifierName = "ScreenChangedVerifier",
            ),
        )
        observer.onEvent(
            RecipeRunEvent.Finished(
                plan,
                RecipeRunResult.Verified(completedSteps = 1, verifiedBy = "RuntimeResultVerifier"),
            ),
        )

        val event = store.events.single()
        assertThat(event.timestampEpochMs).isEqualTo(99L)
        assertThat(event.action).isEqualTo(AuditAction.RecipeCompleted)
        assertThat(event.requestId).isEqualTo("recipe:test_recipe")
        assertThat(event.targetApp).isEqualTo("Example")
        assertThat(event.semanticTarget).isEqualTo("Tap Continue")
        assertThat(event.result).isEqualTo(AuditResult.Dispatched(component = "test_recipe"))
        assertThat(event.verifiedBy).isEqualTo("ScreenChangedVerifier")
    }

    @Test fun `completed recipe without verified result does not append completion audit`() = runTest {
        val store = RecordingAuditStore()
        val observer = RecipeAuditObserver(store)
        val plan = recipePlan()

        observer.onEvent(RecipeRunEvent.Started(plan))
        observer.onEvent(RecipeRunEvent.Finished(plan, RecipeRunResult.Completed(completedSteps = 1)))

        assertThat(store.events).isEmpty()
    }

    private fun recipePlan(): RecipePlan =
        RecipePlan(
            recipeId = "test_recipe",
            displayName = "Test Recipe",
            packageName = "com.example",
            appLabel = "Example",
            summary = "Tap Continue",
            steps = listOf(
                RecipeStep(
                    id = "tap",
                    title = "Tap Continue",
                    command = RecipeCommand.Scroll(ScrollDirection.DOWN),
                ),
            ),
        )

    private class RecordingAuditStore : AuditStore {
        val events = mutableListOf<AuditEvent>()
        private val state = MutableStateFlow<List<AuditEvent>>(emptyList())

        override suspend fun append(event: AuditEvent) {
            events += event
            state.value = events.toList()
        }

        override suspend fun recent(limit: Int): List<AuditEvent> = events.takeLast(limit)

        override fun observe(limit: Int): Flow<List<AuditEvent>> = state
    }
}
