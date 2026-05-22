package com.handy.core.eval

import com.google.common.truth.Truth.assertThat
import com.handy.core.llm.availableTools
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HindiHinglishEvalTest {

    @Test
    fun `recorded responses keep Hinglish shopping routing scoped and useful`() = runTest {
        val screen = evalScreenText(
            packageName = "com.meesho.supply",
            nodes = listOf(
                EvalNode(markId = "m1", text = "https://www.meesho.com/kurti/p/abc123", role = "EditText"),
                EvalNode(markId = "m2", text = "7 days easy return", role = "TextView", clickable = false),
                EvalNode(markId = "m3", text = "View coupons"),
            ),
        )
        val tools = availableTools(
            webSearchEnabled = true,
            hasBraveKey = false,
            intentDispatchEnabled = true,
        )
        val suite = ModelEvalSuite(
            name = "HindiHinglish",
            cases = listOf(
                ModelEvalCase(
                    id = "returnable-hai",
                    userMessage = "returnable hai?",
                    systemPrompt = evalSystemPrompt(screenText = screen, webSearchEnabled = true),
                    screenText = screen,
                    tools = tools,
                    checks = listOf(
                        ResponseChecks.contains("answers in Hinglish", "hai"),
                        ResponseChecks.emitsMarkId("m2"),
                    ),
                ),
                ModelEvalCase(
                    id = "coupon-dhoondo",
                    userMessage = "coupon dhoondo",
                    systemPrompt = evalSystemPrompt(screenText = screen, webSearchEnabled = true),
                    screenText = screen,
                    tools = tools,
                    checks = listOf(
                        ResponseChecks.contains("keeps coupon intent", "coupon"),
                        ResponseChecks.emitsMarkId("m3"),
                    ),
                ),
                ModelEvalCase(
                    id = "similar-compare",
                    userMessage = "similar se compare karo",
                    systemPrompt = evalSystemPrompt(screenText = screen, webSearchEnabled = true),
                    screenText = screen,
                    tools = tools,
                    checks = listOf(
                        ResponseChecks.toolCall("fetch_page"),
                        ResponseChecks.toolInputFieldContains("fetch_page", "url", "meesho.com"),
                    ),
                ),
            ),
        )

        val report = runRecordedSuite(
            suite = suite,
            responses = mapOf(
                "returnable-hai" to RecordedLlmResponse.text(
                    "haan, 7 days easy return dikh raha hai. [POINT:markId=m2]",
                ),
                "coupon-dhoondo" to RecordedLlmResponse.text(
                    "coupon ke liye view coupons kholo. [POINT:markId=m3]",
                ),
                "similar-compare" to RecordedLlmResponse.toolCall(
                    id = "t1",
                    name = "fetch_page",
                    inputJson = "{\"url\":\"https://www.meesho.com/kurti/p/abc123\"}",
                ),
            ),
        )

        assertThat(report.total).isEqualTo(3)
    }
}
