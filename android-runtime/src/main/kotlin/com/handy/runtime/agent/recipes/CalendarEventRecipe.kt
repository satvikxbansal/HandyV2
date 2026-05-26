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
import com.handy.core.agent.parsing.DateTimeParser
import com.handy.core.screen.GroundingSnapshot

/**
 * Opens Android Calendar's event compose UI with prefilled fields. This recipe
 * NEVER creates an event silently; the user must review the draft and tap Save
 * in Calendar.
 */
object CalendarEventRecipe : AppRecipe {
    override val id: String = ID
    override val displayName: String = "Create calendar event"
    override val description: String =
        "Open the OS Calendar compose UI with event fields prefilled; the user taps Save."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val title = invocation.arg("title", "name", "event")
            ?.cleanCalendarValue()
            ?: goal.text.extractCalendarTitle()
            ?: return RecipeProposal.Refused("missing-title")

        val parsedStart = DateTimeParser.parse(
            invocation.arg("start", "time", "when", "date") ?: goal.text,
        )
        val startEpochMs = if (parsedStart.isSuccess) {
            parsedStart.getOrNull()
        } else {
            val error = parsedStart.exceptionOrNull()
            if (error != null && DateTimeParser.isRefusal(error)) {
                return RecipeProposal.Refused("invalid-time")
            }
            null
        }

        val location = invocation.arg("location", "place")
            ?.cleanCalendarValue()
            ?: goal.text.extractCalendarLocation()
        val notes = invocation.arg("notes", "note", "description")
            ?.cleanCalendarValue()
            ?: goal.text.extractCalendarNotes()
        if (listOfNotNull(title, location, notes).any { it.containsBlockedSensitiveCalendarValue() }) {
            return RecipeProposal.Refused("sensitive-calendar-blocked")
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Calendar",
                summary = "Open calendar event draft for \"$title\"",
                steps = listOf(
                    RecipeStep(
                        id = "create-calendar-event-intent",
                        title = "Create calendar event draft",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.CreateCalendarEvent(
                                title = title,
                                startEpochMs = startEpochMs,
                                location = location,
                                notes = notes,
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    const val ID: String = "create_calendar_event"
}

private fun String.extractCalendarTitle(): String? {
    val body = CALENDAR_TITLE_PREFIXES.firstNotNullOfOrNull { pattern ->
        pattern.find(trim())?.groupValues?.getOrNull(1)
    } ?: return null
    return body
        .removeCalendarNotes()
        .removeCalendarLocation()
        .removeCalendarTimeHints()
        .cleanCalendarValue()
}

private fun String.extractCalendarLocation(): String? =
    LOCATION_PATTERN.findAll(this)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.cleanCalendarValue()

private fun String.extractCalendarNotes(): String? =
    NOTES_PATTERN.find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.cleanCalendarValue()

private fun String.removeCalendarNotes(): String =
    NOTES_PATTERN.replace(this, " ")

private fun String.removeCalendarLocation(): String =
    LOCATION_PATTERN.replace(this, " ")

private fun String.removeCalendarTimeHints(): String {
    var out = this
    CALENDAR_TIME_HINTS.forEach { hint ->
        out = hint.replace(out, " ")
    }
    return out
}

private fun String.cleanCalendarValue(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':', '-')
        .replace(Regex("""\s+"""), " ")
        .trim()
        .takeIf { it.isNotBlank() }

private fun String.containsBlockedSensitiveCalendarValue(): Boolean {
    val normalized = lowercase()
    return CARD_LIKE_REGEX.containsMatchIn(this) ||
        CALENDAR_BLOCKED_SENSITIVE_TERMS.any { normalized.contains(it) }
}

private val CALENDAR_BLOCKED_SENSITIVE_TERMS = listOf(
    "password",
    "passcode",
    "otp",
    "one time password",
    "cvv",
    "cvc",
    "card number",
    "upi pin",
)

private val CARD_LIKE_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")

private val CALENDAR_TITLE_PREFIXES = listOf(
    Regex("""^(?:please\s+)?schedule\s+(?:a\s+|an\s+)?(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?calendar event\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?(?:create|make|add)\s+(?:a\s+|an\s+)?calendar event(?:\s+for)?\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?remind me of\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?add to calendar\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?add\s+(.+?)\s+to\s+(?:my\s+)?calendar$""", RegexOption.IGNORE_CASE),
)

private val NOTES_PATTERN = Regex(
    pattern = """\b(?:notes?|with notes?|description)\s*[:\-]?\s+(.+)$""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val LOCATION_PATTERN = Regex(
    pattern = """\bat\s+([a-zA-Z][^,;]*?)(?=\s+(?:notes?|with notes?|description|tomorrow|this|next|monday|tuesday|wednesday|thursday|friday|saturday|sunday|at\s+\d|\d{4}-\d{2}-\d{2}|in\s+\d+\s*(?:hours?|hrs?|h|minutes?|mins?|m))\b|$)""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val TIME_FRAGMENT =
    """(?:at\s+)?(?:\d{1,2}(?::[0-5]\d)?\s*(?:a\.?m\.?|p\.?m\.?)|\d{1,2}:[0-5]\d)"""

private val CALENDAR_TIME_HINTS = listOf(
    Regex("""\b\d{4}-\d{2}-\d{2}[ T]\d{1,2}:[0-5]\d\b""", RegexOption.IGNORE_CASE),
    Regex("""\bin\s+\d+\s*(?:hours?|hrs?|h|minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE),
    Regex("""\btomorrow\b(?:\s+$TIME_FRAGMENT)?""", RegexOption.IGNORE_CASE),
    Regex(
        pattern = """\b(?:(?:this|next)\s+)?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b(?:\s+$TIME_FRAGMENT)?""",
        options = setOf(RegexOption.IGNORE_CASE),
    ),
    Regex("""\bat\s+\d{1,2}(?::[0-5]\d)?(?:\s*(?:a\.?m\.?|p\.?m\.?))?\b""", RegexOption.IGNORE_CASE),
    Regex("""\b\d{1,2}(?::[0-5]\d)?\s*(?:a\.?m\.?|p\.?m\.?)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(19\d{2}|20\d{2}|21\d{2})\b""", RegexOption.IGNORE_CASE),
)
