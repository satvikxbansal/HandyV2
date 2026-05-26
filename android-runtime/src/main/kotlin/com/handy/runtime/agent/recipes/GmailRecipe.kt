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

object GmailRecipe : AppRecipe {
    override val id: String = "gmail_compose"
    override val displayName: String = "Draft Gmail message"
    override val description: String =
        "Open a Gmail draft with recipient, subject, and body filled; pause before Send for strong hold confirmation."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val recipient = invocation.arg("to", "recipient", "email", "address")
            ?.cleanRecipeValue()
            ?: return RecipeProposal.Refused("missing-recipient")
        val body = invocation.arg("body", "message", "text")
            ?.cleanRecipeValue()
            ?: return RecipeProposal.Refused("missing-body")
        val subject = invocation.arg("subject", "title")?.cleanRecipeValue()
        if (listOfNotNull(recipient, body, subject).any { it.containsBlockedSensitiveValue() }) {
            return RecipeProposal.Refused("sensitive-message-blocked")
        }
        val sendTarget = invocation.sendTargetOrDefault()

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = GMAIL_PACKAGE,
                appLabel = "Gmail",
                summary = "Draft Gmail message to $recipient",
                steps = listOf(
                    RecipeStep(
                        id = "open-draft",
                        title = "Open Gmail draft to $recipient",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.OpenUrl(
                                mailtoUrl(
                                    recipient = recipient,
                                    subject = subject,
                                    body = body,
                                ),
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                    RecipeStep(
                        id = "send",
                        title = "Send Gmail message?",
                        command = RecipeCommand.Tap(sendTarget),
                        sensitive = true,
                        confirmationOverride = ConfirmationLevel.STRONG_HOLD,
                    ),
                ),
            ).validate(),
        )
    }
}

private const val GMAIL_PACKAGE = "com.google.android.gm"

private fun RecipeInvocation.sendTargetOrDefault(): RecipeTarget.Node =
    arg("sendMarkId", "markId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("sendViewId", "viewId")?.let { RecipeTarget.Node(viewId = it, role = "button") }
        ?: arg("sendDesc", "desc")?.let { RecipeTarget.Node(desc = it, role = "button") }
        ?: RecipeTarget.Node(desc = "Send", role = "button")

private fun mailtoUrl(
    recipient: String,
    subject: String?,
    body: String,
): String {
    val query = buildList {
        subject?.takeIf { it.isNotBlank() }?.let { add("subject=${it.urlEncode()}") }
        add("body=${body.urlEncode()}")
    }.joinToString("&")
    return "mailto:${recipient.urlEncode()}?$query"
}

private fun String.cleanRecipeValue(): String? =
    trim()
        .trim('"', '\'')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

private fun String.containsBlockedSensitiveValue(): Boolean {
    val normalized = lowercase()
    return CARD_LIKE_REGEX.containsMatchIn(this) ||
        GMAIL_BLOCKED_SENSITIVE_TERMS.any { normalized.contains(it) }
}

private val GMAIL_BLOCKED_SENSITIVE_TERMS = listOf(
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
