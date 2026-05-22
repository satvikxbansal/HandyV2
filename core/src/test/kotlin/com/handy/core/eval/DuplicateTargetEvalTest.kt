package com.handy.core.eval

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DuplicateTargetEvalTest {

    @Test
    fun `recorded responses ask before choosing among duplicate visible targets`() = runTest {
        val screen = evalScreenText(
            packageName = "com.handy.fixture.duplicates",
            nodes = listOf(
                EvalNode(markId = "m1", text = "Continue"),
                EvalNode(markId = "m2", text = "Continue"),
                EvalNode(markId = "m3", text = "Cancel"),
            ),
        )
        val suite = ModelEvalSuite(
            name = "DuplicateTarget",
            cases = listOf(
                ModelEvalCase(
                    id = "two-continue-buttons",
                    userMessage = "tap continue",
                    systemPrompt = evalSystemPrompt(screenText = screen),
                    screenText = screen,
                    checks = listOf(
                        ResponseChecks.clarification(),
                        ResponseChecks.noPointing(),
                    ),
                ),
                ModelEvalCase(
                    id = "two-edit-buttons",
                    userMessage = "which edit should i use?",
                    systemPrompt = evalSystemPrompt(
                        screenText = evalScreenText(
                            packageName = "com.handy.fixture.duplicates",
                            nodes = listOf(
                                EvalNode(markId = "m1", text = "Edit", viewId = "profile_edit"),
                                EvalNode(markId = "m2", text = "Edit", viewId = "address_edit"),
                            ),
                        ),
                    ),
                    checks = listOf(
                        ResponseChecks.clarification(),
                        ResponseChecks.noPointing(),
                    ),
                ),
            ),
        )

        val report = runRecordedSuite(
            suite = suite,
            responses = mapOf(
                "two-continue-buttons" to RecordedLlmResponse.text(
                    "which continue button do you mean, the left one or the right one? [POINT:none]",
                ),
                "two-edit-buttons" to RecordedLlmResponse.text(
                    "which one should we edit: profile or address? [POINT:none]",
                ),
            ),
        )

        assertThat(report.total).isEqualTo(2)
    }
}
