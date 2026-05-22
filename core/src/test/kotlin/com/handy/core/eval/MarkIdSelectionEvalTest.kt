package com.handy.core.eval

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MarkIdSelectionEvalTest {

    @Test
    fun `recorded responses select exact visible mark ids`() = runTest {
        val screen = evalScreenText(
            packageName = "com.handy.fixture.markids",
            nodes = listOf(
                EvalNode(markId = "m1", text = "Cancel"),
                EvalNode(markId = "m2", text = "Search"),
                EvalNode(markId = "m3", text = "Save"),
            ),
        )
        val prompt = evalSystemPrompt(screenText = screen)
        val suite = ModelEvalSuite(
            name = "MarkIdSelection",
            cases = listOf(
                ModelEvalCase(
                    id = "mark-search",
                    userMessage = "where do i search?",
                    systemPrompt = prompt,
                    screenText = screen,
                    checks = listOf(ResponseChecks.emitsMarkId("m2")),
                ),
                ModelEvalCase(
                    id = "mark-save",
                    userMessage = "where do i save this?",
                    systemPrompt = prompt,
                    screenText = screen,
                    checks = listOf(ResponseChecks.emitsMarkId("m3")),
                ),
                ModelEvalCase(
                    id = "mark-cancel",
                    userMessage = "where can i back out?",
                    systemPrompt = prompt,
                    screenText = screen,
                    checks = listOf(ResponseChecks.emitsMarkId("m1")),
                ),
            ),
        )

        val report = runRecordedSuite(
            suite = suite,
            responses = mapOf(
                "mark-search" to RecordedLlmResponse.text("tap search. [POINT:markId=m2]"),
                "mark-save" to RecordedLlmResponse.text("tap save. [POINT:markId=m3]"),
                "mark-cancel" to RecordedLlmResponse.text("tap cancel. [POINT:markId=m1]"),
            ),
        )

        assertThat(report.total).isEqualTo(3)
    }
}
