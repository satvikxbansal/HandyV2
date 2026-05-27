package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.RecipeTarget
import com.handy.core.agent.SideEffectClassification
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WhatsAppRecipe : AppRecipe {
    override val id: String = "whatsapp_reply"
    override val displayName: String = "Draft WhatsApp reply"
    override val description: String =
        "Open a WhatsApp chat, fill the user's draft, and pause before Send for strong hold confirmation."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val recipient = invocation.arg("recipient", "contact", "to", "name")
            ?.cleanRecipeValue()
            ?: goal.text.extractReplyRecipient()
            ?: return RecipeProposal.Refused("missing-recipient")
        val message = invocation.arg("message", "body", "text")
            ?.cleanRecipeValue()
            ?: goal.text.extractQuotedMessage()
            ?: return RecipeProposal.Refused("missing-message")
        val phone = invocation.arg("phone", "recipientPhone", "number")
            ?.normalizeWhatsAppPhone()
        if (listOfNotNull(recipient, message, phone).any { it.containsBlockedSensitiveValue() }) {
            return RecipeProposal.Refused("sensitive-message-blocked")
        }

        val steps = if (phone != null) {
            phoneDeepLinkSteps(phone, recipient, message, invocation)
        } else {
            contactSearchSteps(recipient, message, invocation)
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = WHATSAPP_PACKAGE,
                appLabel = "WhatsApp",
                summary = "Draft WhatsApp reply to $recipient",
                steps = steps,
            ).validate(),
        )
    }

    private fun phoneDeepLinkSteps(
        phone: String,
        recipient: String,
        message: String,
        invocation: RecipeInvocation,
    ): List<RecipeStep> = listOf(
        RecipeStep(
            id = "open-chat",
            title = "Open WhatsApp chat with $recipient",
            command = RecipeCommand.NativeAction(
                action = AssistantAction.OpenUrl(
                    "https://wa.me/$phone?text=${message.urlEncode()}",
                ),
                allowPackageChangeAfter = true,
            ),
        ),
        sendStep(invocation),
    )

    private fun contactSearchSteps(
        recipient: String,
        message: String,
        invocation: RecipeInvocation,
    ): List<RecipeStep> = listOf(
        RecipeStep(
            id = "open-whatsapp",
            title = "Open WhatsApp",
            command = RecipeCommand.NativeAction(
                action = AssistantAction.OpenApp(WHATSAPP_PACKAGE),
                allowPackageChangeAfter = true,
            ),
        ),
        RecipeStep(
            id = "open-search",
            title = "Open WhatsApp search",
            command = RecipeCommand.Tap(invocation.searchButtonTarget()),
        ),
        RecipeStep(
            id = "search-contact",
            title = "Search for $recipient",
            command = RecipeCommand.TypeText(
                target = invocation.searchFieldTarget(),
                text = recipient,
            ),
        ),
        RecipeStep(
            id = "open-contact",
            title = "Open chat with $recipient",
            command = RecipeCommand.Tap(invocation.contactTarget(recipient)),
        ),
        RecipeStep(
            id = "type-message",
            title = "Fill WhatsApp draft",
            command = RecipeCommand.TypeText(
                target = invocation.messageFieldTarget(),
                text = message,
            ),
        ),
        sendStep(invocation),
    )

    private fun sendStep(invocation: RecipeInvocation): RecipeStep =
        RecipeStep(
            id = "send",
            title = "Send WhatsApp message?",
            command = RecipeCommand.Tap(invocation.sendTarget()),
            sensitive = true,
            confirmationOverride = ConfirmationLevel.STRONG_HOLD,
        )
}

private const val WHATSAPP_PACKAGE = "com.whatsapp"

private fun RecipeInvocation.searchButtonTarget(): RecipeTarget.Node =
    arg("searchMarkId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("searchViewId")?.let { RecipeTarget.Node(viewId = it, role = "button") }
        ?: arg("searchDesc")?.let { RecipeTarget.Node(desc = it, role = "button") }
        ?: RecipeTarget.Node(desc = "Search", role = "button")

private fun RecipeInvocation.searchFieldTarget(): RecipeTarget.Node =
    arg("searchFieldMarkId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("searchFieldViewId")?.let { RecipeTarget.Node(viewId = it, role = "textfield") }
        ?: arg("searchFieldDesc")?.let { RecipeTarget.Node(desc = it, role = "textfield") }
        ?: RecipeTarget.Node(role = "textfield")

private fun RecipeInvocation.contactTarget(recipient: String): RecipeTarget.Node =
    arg("contactMarkId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("contactViewId")?.let { RecipeTarget.Node(viewId = it) }
        ?: arg("contactDesc")?.let { RecipeTarget.Node(desc = it) }
        ?: RecipeTarget.Node(text = recipient)

private fun RecipeInvocation.messageFieldTarget(): RecipeTarget.Node =
    arg("messageFieldMarkId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("messageFieldViewId")?.let { RecipeTarget.Node(viewId = it, role = "textfield") }
        ?: arg("messageFieldDesc")?.let { RecipeTarget.Node(desc = it, role = "textfield") }
        ?: RecipeTarget.Node(desc = "Type a message", role = "textfield")

private fun RecipeInvocation.sendTarget(): RecipeTarget.Node =
    arg("sendMarkId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("sendViewId")?.let { RecipeTarget.Node(viewId = it, role = "button") }
        ?: arg("sendDesc")?.let { RecipeTarget.Node(desc = it, role = "button") }
        ?: RecipeTarget.Node(desc = "Send", role = "button")

private fun String.extractReplyRecipient(): String? {
    REPLY_TO_PATTERN.find(this)?.let { return it.groupValues[1].cleanRecipeValue() }
    return null
}

private fun String.extractQuotedMessage(): String? {
    QUOTED_MESSAGE_PATTERN.find(this)?.let { match ->
        return (match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: match.groupValues.getOrNull(2))
            ?.cleanRecipeValue()
    }
    return null
}

private fun String.cleanRecipeValue(): String? =
    trim()
        .trim('"', '\'')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.normalizeWhatsAppPhone(): String? =
    filter(Char::isDigit)
        .takeIf { it.length >= 7 }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

private fun String.containsBlockedSensitiveValue(): Boolean {
    val normalized = lowercase()
    return CARD_LIKE_REGEX.containsMatchIn(this) ||
        WHATSAPP_BLOCKED_SENSITIVE_TERMS.any { normalized.contains(it) }
}

private val WHATSAPP_BLOCKED_SENSITIVE_TERMS = listOf(
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

private val REPLY_TO_PATTERN = Regex(
    pattern = """\b(?:reply|message|text|whatsapp)\s+(?:to\s+)?([A-Za-z][A-Za-z0-9 ._-]{0,60})""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val QUOTED_MESSAGE_PATTERN = Regex(
    pattern = """"([^"]+)""" + "|" + """'([^']+)'""",
)
