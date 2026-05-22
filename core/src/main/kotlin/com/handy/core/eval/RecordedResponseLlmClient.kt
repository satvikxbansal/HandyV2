package com.handy.core.eval

import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolRunner
import java.util.ArrayDeque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RecordedResponseLlmClient(
    private val responsesByCaseId: Map<String, RecordedLlmResponse>,
    override val modelId: String = "recorded-eval",
) : LlmClient {
    private val queuedCaseIds = ArrayDeque<String>()

    fun enqueue(caseId: String) {
        queuedCaseIds.addLast(caseId)
    }

    override fun streamChat(request: LlmRequest): Flow<LlmChunk> =
        recordedFlow(request)

    override fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk> =
        recordedFlow(request)

    private fun recordedFlow(request: LlmRequest): Flow<LlmChunk> = flow {
        val caseId = nextCaseId(request)
        val response = responsesByCaseId[caseId]
            ?: error("No recorded LLM response for eval case $caseId")
        response.chunks.forEach { emit(it) }
        if (response.chunks.none { it is LlmChunk.Done || it is LlmChunk.Error }) {
            emit(LlmChunk.Done("end_turn"))
        }
    }

    private fun nextCaseId(request: LlmRequest): String {
        if (!queuedCaseIds.isEmpty()) return queuedCaseIds.removeFirst()
        return request.messages
            .lastOrNull()
            ?.id
            ?.removeSuffix("-user")
            ?: error("Recorded eval request has no case id")
    }
}

data class RecordedLlmResponse(
    val chunks: List<LlmChunk>,
) {
    companion object {
        fun text(value: String): RecordedLlmResponse =
            RecordedLlmResponse(
                listOf(
                    LlmChunk.Text(value),
                    LlmChunk.Done("end_turn"),
                ),
            )

        fun toolCall(
            id: String,
            name: String,
            inputJson: String,
            textAfter: String = "",
        ): RecordedLlmResponse {
            val chunks = buildList {
                add(LlmChunk.ToolCall(id = id, name = name, inputJson = inputJson))
                if (textAfter.isNotBlank()) add(LlmChunk.Text(textAfter))
                add(LlmChunk.Done("tool_use"))
            }
            return RecordedLlmResponse(chunks)
        }
    }
}
