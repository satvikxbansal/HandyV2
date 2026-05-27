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
 * Safer calendar event draft recipe. V1 remains available by direct recipe id
 * for old transcripts; canonical create_calendar_event routes here.
 */
object CalendarEventRecipeV2 : AppRecipe {
    override val id: String = ID
    override val displayName: String = "Create calendar event"
    override val description: String =
        "Open Calendar's event draft with stronger attendee parsing and recurring-rule refusal."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        if (goal.hasRecurringCalendarRule(invocation)) {
            return RecipeProposal.Refused(
                "recurrence-needs-confirmation: should I make this repeat?",
            )
        }
        val title = invocation.arg("title", "name", "event", "summary")
            ?.cleanCalendarV2Value()
            ?: goal.text.extractCalendarV2Title()
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
            ?.cleanCalendarV2Value()
            ?: goal.text.extractCalendarV2Location()
        val notes = invocation.arg("notes", "note", "description")
            ?.cleanCalendarV2Value()
            ?: goal.text.extractCalendarV2Notes()
        val attendees = invocation.arg("attendees", "attendee", "invitees", "to", "with")
            ?.extractAttendees()
            ?: goal.text.extractCalendarV2Attendees()
        if ((listOfNotNull(title, location, notes) + attendees).any { it.containsBlockedSensitiveCalendarV2Value() }) {
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
                        id = "create-calendar-event-v2-intent",
                        title = "Open calendar event draft",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.CreateCalendarEvent(
                                title = title,
                                startEpochMs = startEpochMs,
                                location = location,
                                notes = notes,
                                attendees = attendees,
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    const val ID: String = "create_calendar_event_v2"
}

private fun UserGoal.hasRecurringCalendarRule(invocation: RecipeInvocation): Boolean {
    val raw = (listOf(text) + invocation.args.flatMap { (key, value) -> listOf(key, value) })
        .joinToString(" ")
        .lowercase()
    return invocation.arg("repeat", "recurrence", "rrule") != null ||
        CALENDAR_RECURRENCE_PATTERNS.any { it.containsMatchIn(raw) }
}

private fun String.extractCalendarV2Title(): String? {
    val body = CALENDAR_V2_TITLE_PREFIXES.firstNotNullOfOrNull { pattern ->
        pattern.find(trim())?.groupValues?.getOrNull(1)
    } ?: return null
    return body
        .removeCalendarV2Notes()
        .removeCalendarV2Location()
        .removeCalendarV2Attendees()
        .removeCalendarV2TimeHints()
        .cleanCalendarV2Value()
}

private fun String.extractCalendarV2Location(): String? =
    CALENDAR_V2_LOCATION_PATTERN.findAll(this)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.cleanCalendarV2Value()

private fun String.extractCalendarV2Notes(): String? =
    CALENDAR_V2_NOTES_PATTERN.find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.cleanCalendarV2Value()

private fun String.extractCalendarV2Attendees(): List<String> =
    CALENDAR_V2_ATTENDEE_PATTERN.findAll(this)
        .flatMap { match -> match.groupValues.getOrNull(1).orEmpty().extractAttendees() }
        .distinctBy { it.lowercase() }
        .take(MAX_CALENDAR_ATTENDEES)
        .toList()

private fun String.extractAttendees(): List<String> =
    split(',', ';')
        .flatMap { chunk -> EMAIL_PATTERN.findAll(chunk).map { it.value } }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(MAX_CALENDAR_ATTENDEES)

private fun String.removeCalendarV2Notes(): String =
    CALENDAR_V2_NOTES_PATTERN.replace(this, " ")

private fun String.removeCalendarV2Location(): String =
    CALENDAR_V2_LOCATION_PATTERN.replace(this, " ")

private fun String.removeCalendarV2Attendees(): String =
    CALENDAR_V2_ATTENDEE_PATTERN.replace(this, " ")

private fun String.removeCalendarV2TimeHints(): String {
    var out = this
    CALENDAR_V2_TIME_HINTS.forEach { hint ->
        out = hint.replace(out, " ")
    }
    return out
}

private fun String.cleanCalendarV2Value(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':', '-')
        .replace(Regex("""\s+"""), " ")
        .trim()
        .takeIf { it.isNotBlank() }

private fun String.containsBlockedSensitiveCalendarV2Value(): Boolean {
    val normalized = lowercase()
    return CARD_LIKE_REGEX.containsMatchIn(this) ||
        CALENDAR_V2_BLOCKED_SENSITIVE_TERMS.any { normalized.contains(it) }
}

private val CALENDAR_V2_BLOCKED_SENSITIVE_TERMS = listOf(
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
private val EMAIL_PATTERN = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)

private val CALENDAR_V2_TITLE_PREFIXES = listOf(
    Regex("""^(?:please\s+)?schedule\s+(?:a\s+|an\s+)?(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?calendar event\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?(?:create|make|add)\s+(?:a\s+|an\s+)?calendar event(?:\s+for)?\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?add to calendar\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?add\s+(.+?)\s+to\s+(?:my\s+)?calendar$""", RegexOption.IGNORE_CASE),
)

private val CALENDAR_V2_NOTES_PATTERN = Regex(
    pattern = """\b(?:notes?|with notes?|description)\s*[:\-]?\s+(.+)$""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val CALENDAR_V2_ATTENDEE_PATTERN = Regex(
    pattern = """\b(?:with|invite|attendees?|to)\s+([^.;]+?@[^.;]+?)(?=\s+(?:at|on|tomorrow|this|next|notes?|description)\b|$)""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val CALENDAR_V2_LOCATION_PATTERN = Regex(
    pattern = """\bat\s+([a-zA-Z][^,;]*?)(?=\s+(?:notes?|with notes?|description|invite|attendees?|tomorrow|this|next|monday|tuesday|wednesday|thursday|friday|saturday|sunday|at\s+\d|\d{4}-\d{2}-\d{2}|in\s+\d+\s*(?:hours?|hrs?|h|minutes?|mins?|m))\b|$)""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val CALENDAR_V2_TIME_FRAGMENT =
    """(?:at\s+)?(?:\d{1,2}(?::[0-5]\d)?\s*(?:a\.?m\.?|p\.?m\.?)|\d{1,2}:[0-5]\d)"""

private val CALENDAR_V2_TIME_HINTS = listOf(
    Regex("""\b\d{4}-\d{2}-\d{2}[ T]\d{1,2}:[0-5]\d\b""", RegexOption.IGNORE_CASE),
    Regex("""\bin\s+\d+\s*(?:hours?|hrs?|h|minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE),
    Regex("""\btomorrow\b(?:\s+$CALENDAR_V2_TIME_FRAGMENT)?""", RegexOption.IGNORE_CASE),
    Regex(
        pattern = """\b(?:(?:this|next)\s+)?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b(?:\s+$CALENDAR_V2_TIME_FRAGMENT)?""",
        options = setOf(RegexOption.IGNORE_CASE),
    ),
    Regex("""\bat\s+\d{1,2}(?::[0-5]\d)?(?:\s*(?:a\.?m\.?|p\.?m\.?))?\b""", RegexOption.IGNORE_CASE),
    Regex("""\b\d{1,2}(?::[0-5]\d)?\s*(?:a\.?m\.?|p\.?m\.?)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(19\d{2}|20\d{2}|21\d{2})\b""", RegexOption.IGNORE_CASE),
)

private val CALENDAR_RECURRENCE_PATTERNS = listOf(
    Regex("""\bevery\s+(day|week|month|year|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(daily|weekly|monthly|yearly|recurring|repeat)\b""", RegexOption.IGNORE_CASE),
)

private const val MAX_CALENDAR_ATTENDEES = 10
