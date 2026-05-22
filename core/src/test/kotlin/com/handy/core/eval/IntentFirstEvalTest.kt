package com.handy.core.eval

import com.google.common.truth.Truth.assertThat
import com.handy.core.llm.availableTools
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class IntentFirstEvalTest {

    @Test
    fun `recorded responses choose dispatch action for canonical one step intents`() = runTest {
        val tools = availableTools(
            webSearchEnabled = true,
            hasBraveKey = true,
            intentDispatchEnabled = true,
        )
        val suite = ModelEvalSuite(
            name = "IntentFirst",
            cases = listOf(
                ModelEvalCase(
                    id = "set-alarm",
                    userMessage = "set an alarm for 7 30 tomorrow morning",
                    systemPrompt = evalSystemPrompt(webSearchEnabled = true, hasBraveKey = true),
                    tools = tools,
                    checks = listOf(
                        ResponseChecks.toolCall("dispatch_action"),
                        ResponseChecks.toolInputFieldEquals("dispatch_action", "type", "set_alarm"),
                        ResponseChecks.noPointing(),
                    ),
                ),
                ModelEvalCase(
                    id = "open-youtube",
                    userMessage = "open youtube",
                    systemPrompt = evalSystemPrompt(webSearchEnabled = true, hasBraveKey = true),
                    tools = tools,
                    checks = listOf(
                        ResponseChecks.toolCall("dispatch_action"),
                        ResponseChecks.toolInputFieldEquals("dispatch_action", "type", "open_app"),
                        ResponseChecks.noPointing(),
                    ),
                ),
                ModelEvalCase(
                    id = "search-google",
                    userMessage = "search google for best cafes nearby",
                    systemPrompt = evalSystemPrompt(webSearchEnabled = true, hasBraveKey = true),
                    tools = tools,
                    checks = listOf(
                        ResponseChecks.toolCall("dispatch_action"),
                        ResponseChecks.toolInputFieldEquals("dispatch_action", "type", "web_search"),
                        ResponseChecks.noPointing(),
                    ),
                ),
            ),
        )

        val report = runRecordedSuite(
            suite = suite,
            responses = mapOf(
                "set-alarm" to RecordedLlmResponse.toolCall(
                    id = "intent-1",
                    name = "dispatch_action",
                    inputJson = "{\"type\":\"set_alarm\",\"hour\":7,\"minute\":30}",
                ),
                "open-youtube" to RecordedLlmResponse.toolCall(
                    id = "intent-2",
                    name = "dispatch_action",
                    inputJson = "{\"type\":\"open_app\",\"packageHint\":\"youtube\"}",
                ),
                "search-google" to RecordedLlmResponse.toolCall(
                    id = "intent-3",
                    name = "dispatch_action",
                    inputJson = "{\"type\":\"web_search\",\"query\":\"best cafes nearby\"}",
                ),
            ),
        )

        assertThat(report.total).isEqualTo(3)
    }
}
