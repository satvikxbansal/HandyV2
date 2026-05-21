package com.handy.core.agent

sealed class CorrectionIntent {
    data class Other(val labelHint: String? = null) : CorrectionIntent()
    object Next : CorrectionIntent()
    object Previous : CorrectionIntent()
    object Popup : CorrectionIntent()

    companion object {
        fun classify(transcript: String): CorrectionIntent? {
            val normalized = transcript.normalizedForCorrection()
            if (normalized.isBlank()) return null

            val tokens = normalized.split(' ').filter { it.isNotBlank() }
            if (tokens.isEmpty()) return null

            if (normalized == "the popup one" || tokens.contains("popup")) {
                return Popup
            }
            if (tokens.contains("previous")) return Previous
            if (tokens.contains("next")) return Next

            val asksForOther = normalized == "no" ||
                tokens.firstOrNull() == "no" ||
                normalized == "other one" ||
                tokens.contains("other")

            if (!asksForOther) return null

            return Other(labelHint = tokens.labelHint())
        }

        private fun String.normalizedForCorrection(): String =
            lowercase()
                .replace(Regex("""[^a-z0-9]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        private fun List<String>.labelHint(): String? {
            val filler = setOf(
                "no",
                "nope",
                "nah",
                "not",
                "that",
                "this",
                "the",
                "other",
                "one",
                "please",
                "actually",
            )
            return filterNot { it in filler }
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
        }
    }
}
