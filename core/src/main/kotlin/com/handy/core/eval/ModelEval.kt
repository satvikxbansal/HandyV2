package com.handy.core.eval

import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolDefinition
import com.handy.core.llm.ToolResult
import com.handy.core.llm.ToolRunner
import com.handy.core.model.ChatMessage
import com.handy.core.model.MessageRole
import com.handy.core.screen.ScreenTextSnapshot
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

data class ModelEvalSuite(
    val name: String,
    val cases: List<ModelEvalCase>,
    val softGate: EvalSoftGate = EvalSoftGate(),
)

data class ModelEvalCase(
    val id: String,
    val userMessage: String,
    val systemPrompt: String,
    val screenText: ScreenTextSnapshot? = null,
    val tools: List<ToolDefinition> = emptyList(),
    val history: List<ChatMessage> = emptyList(),
    val checks: List<ResponseCheck>,
)

data class EvalSoftGate(
    val minimumPassRate: Double = 1.0,
    val hardFail: Boolean = false,
)

data class ResponseCheck(
    val name: String,
    val evaluate: (ModelEvalOutput) -> Boolean,
    val failureMessage: (ModelEvalOutput) -> String,
)

data class ModelEvalOutput(
    val text: String,
    val toolCalls: List<LlmChunk.ToolCall>,
    val request: LlmRequest,
)

data class ResponseCheckResult(
    val name: String,
    val passed: Boolean,
    val failureMessage: String?,
)

data class ModelEvalCaseResult(
    val caseId: String,
    val passed: Boolean,
    val output: ModelEvalOutput,
    val checkResults: List<ResponseCheckResult>,
)

data class ModelEvalReport(
    val suiteName: String,
    val caseResults: List<ModelEvalCaseResult>,
    val softGate: EvalSoftGate,
) {
    val passed: Int get() = caseResults.count { it.passed }
    val total: Int get() = caseResults.size
    val passRate: Double get() = if (total == 0) 1.0 else passed.toDouble() / total.toDouble()

    fun passRateLine(): String =
        "Model eval $suiteName pass rate: $passed/$total (${(passRate * 100.0).formatPercent()}%)"

    fun softGateWarningOrNull(): String? {
        if (passRate >= softGate.minimumPassRate) return null
        val level = if (softGate.hardFail) "HARD" else "SOFT"
        return "$level gate warning for $suiteName: pass rate ${passRate.formatRatio()} below ${softGate.minimumPassRate.formatRatio()}"
    }

    fun failureSummary(): String =
        caseResults
            .filterNot { it.passed }
            .joinToString(separator = "\n") { result ->
                val checks = result.checkResults
                    .filterNot { it.passed }
                    .joinToString { "${it.name}: ${it.failureMessage.orEmpty()}" }
                "${result.caseId}: $checks"
            }
}

object ModelEvalRunner {
    suspend fun run(
        suite: ModelEvalSuite,
        llmClient: LlmClient,
        beforeCase: (ModelEvalCase) -> Unit = {},
    ): ModelEvalReport {
        val results = suite.cases.map { case ->
            beforeCase(case)
            val request = case.toRequest()
            val output = collectOutput(
                request = request,
                flow = if (case.tools.isEmpty()) {
                    llmClient.streamChat(request)
                } else {
                    llmClient.streamToolAwareChat(request, NoopEvalToolRunner)
                },
            )
            val checkResults = case.checks.map { check ->
                val passed = check.evaluate(output)
                ResponseCheckResult(
                    name = check.name,
                    passed = passed,
                    failureMessage = if (passed) null else check.failureMessage(output),
                )
            }
            ModelEvalCaseResult(
                caseId = case.id,
                passed = checkResults.all { it.passed },
                output = output,
                checkResults = checkResults,
            )
        }
        return ModelEvalReport(
            suiteName = suite.name,
            caseResults = results,
            softGate = suite.softGate,
        )
    }

    private fun ModelEvalCase.toRequest(): LlmRequest =
        LlmRequest(
            systemPrompt = systemPrompt,
            messages = history + ChatMessage(
                id = "$id-user",
                role = MessageRole.USER,
                content = userMessage,
                timestampEpochMs = 0L,
            ),
            screenText = screenText,
            tools = tools,
        )

    private suspend fun collectOutput(
        request: LlmRequest,
        flow: Flow<LlmChunk>,
    ): ModelEvalOutput {
        val text = StringBuilder()
        val toolCalls = mutableListOf<LlmChunk.ToolCall>()
        flow.collect { chunk ->
            when (chunk) {
                is LlmChunk.Text -> text.append(chunk.delta)
                is LlmChunk.ToolCall -> toolCalls += chunk
                is LlmChunk.Done -> Unit
                is LlmChunk.Error -> throw chunk.throwable
            }
        }
        return ModelEvalOutput(
            text = text.toString(),
            toolCalls = toolCalls,
            request = request,
        )
    }

    private object NoopEvalToolRunner : ToolRunner {
        override suspend fun run(name: String, inputJson: String): ToolResult =
            ToolResult.Ok("eval-noop")
    }
}

private fun Double.formatPercent(): String =
    String.format(Locale.US, "%.1f", this)

private fun Double.formatRatio(): String =
    String.format(Locale.US, "%.2f", this)
