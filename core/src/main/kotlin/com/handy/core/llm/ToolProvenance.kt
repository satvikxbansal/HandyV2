package com.handy.core.llm

data class ToolProvenance(
    val turnId: String,
    val usedUntrustedTools: Set<String> = emptySet(),
    val untrustedDomains: List<String> = emptyList(),
    val containsActionLikeInstruction: Boolean = false,
) {
    val isUntrusted: Boolean get() = usedUntrustedTools.isNotEmpty()
}
