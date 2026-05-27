package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.SideEffectClassification
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot

object NotesRecipe : AppRecipe {
    override val id: String = "notes"
    override val displayName: String = "Create note draft"
    override val description: String =
        "Open the Android share sheet with note text so the user chooses Keep, Notion, Obsidian, or another notes app."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.DRAFT_ONLY

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val note = invocation.arg("note", "text", "body", "message")
            ?.cleanNoteValue()
            ?: goal.text.extractNoteText()
            ?: return RecipeProposal.Refused("missing-note-text")
        if (note.containsBlockedSensitiveNoteValue()) {
            return RecipeProposal.Refused("sensitive-note-blocked")
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Notes",
                summary = "Share note draft",
                steps = listOf(
                    RecipeStep(
                        id = "share-note-text",
                        title = "Open share sheet with note text",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.ShareText(
                                text = note,
                                mimeType = "text/plain",
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }
}

private fun String.extractNoteText(): String? {
    NOTE_PATTERNS.forEach { pattern ->
        val match = pattern.find(trim()) ?: return@forEach
        return match.groupValues.getOrNull(1)?.cleanNoteValue()
    }
    return null
}

private fun String.cleanNoteValue(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.containsBlockedSensitiveNoteValue(): Boolean {
    val normalized = lowercase()
    return CARD_LIKE_REGEX.containsMatchIn(this) ||
        NOTE_BLOCKED_SENSITIVE_TERMS.any { normalized.contains(it) }
}

private val NOTE_PATTERNS = listOf(
    Regex("""\b(?:take|create|make|write|add)\s+(?:a\s+)?note(?:\s*[:\-]\s*|\s+)(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""\bremind\s+me\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
)

private val NOTE_BLOCKED_SENSITIVE_TERMS = listOf(
    "password",
    "passcode",
    "otp",
    "one time password",
    "cvv",
    "cvc",
    "card number",
)

private val CARD_LIKE_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")
