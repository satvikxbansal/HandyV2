package com.handy.core.eval

import com.google.common.truth.Truth.assertThat
import com.handy.core.model.AssistantMode
import com.handy.core.prompts.PromptCatalog
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSerializer
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode

suspend fun runRecordedSuite(
    suite: ModelEvalSuite,
    responses: Map<String, RecordedLlmResponse>,
): ModelEvalReport {
    val fake = RecordedResponseLlmClient(responses)
    val report = ModelEvalRunner.run(suite, fake) { case ->
        fake.enqueue(case.id)
    }
    println(report.passRateLine())
    report.softGateWarningOrNull()?.let(::println)
    assertThat(report.total).isGreaterThan(0)
    if (suite.softGate.hardFail) {
        assertThat(report.passRate).isAtLeast(suite.softGate.minimumPassRate)
    }
    return report
}

fun evalSystemPrompt(
    screenText: ScreenTextSnapshot? = null,
    webSearchEnabled: Boolean = false,
    hasBraveKey: Boolean = false,
    quickOverlayResponse: Boolean = false,
    contextFailureReason: String? = null,
): String =
    PromptCatalog.buildSystemPrompt(
        mode = AssistantMode.HELP_ONLY,
        fromVoice = false,
        webSearchEnabled = webSearchEnabled,
        hasBraveKey = hasBraveKey,
        screenTextPackage = screenText?.packageName,
        screenTextFlattenedTree = screenText?.let(ScreenTextSerializer::flatten),
        quickOverlayResponse = quickOverlayResponse,
        contextFailureReason = contextFailureReason,
    )

fun evalScreenText(
    packageName: String,
    windowTitle: String? = null,
    nodes: List<EvalNode>,
): ScreenTextSnapshot =
    ScreenTextSnapshot(
        packageName = packageName,
        windowTitle = windowTitle,
        timestampEpochMs = 0L,
        root = UiNode(
            role = "Root",
            boundsInScreen = IntRect(0, 0, 1080, 2200),
            children = nodes.mapIndexed { index, node -> node.toUiNode(index) },
        ),
    )

data class EvalNode(
    val markId: String,
    val role: String = "Button",
    val text: String? = null,
    val desc: String? = null,
    val viewId: String? = null,
    val clickable: Boolean = true,
)

private fun EvalNode.toUiNode(index: Int): UiNode {
    val top = 120 + index * 112
    return UiNode(
        markId = markId,
        role = role,
        text = text,
        contentDescription = desc,
        viewIdResourceName = viewId,
        boundsInScreen = IntRect(64, top, 1016, top + 84),
        clickable = clickable,
        enabled = true,
    )
}
