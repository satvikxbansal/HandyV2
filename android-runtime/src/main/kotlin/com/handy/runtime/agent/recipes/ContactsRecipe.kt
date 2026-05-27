package com.handy.runtime.agent.recipes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
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
import timber.log.Timber

class ContactsRecipe(
    private val findContacts: (String) -> ContactLookupResult = {
        ContactLookupResult.Matches(emptyList())
    },
) : AppRecipe {
    override val id: String = "contacts"
    override val displayName: String = "Open contact handoff"
    override val description: String =
        "Resolve a local contact by name, then open the contact, dialer draft, or SMS draft. Never auto-calls or sends."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.DRAFT_ONLY

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val mode = goal.contactMode(invocation)
            ?: return RecipeProposal.Refused("missing-contact-action")
        val name = invocation.arg("name", "contact", "recipient", "to")
            ?.cleanContactValue()
            ?: goal.text.extractContactName(mode)
            ?: return RecipeProposal.Refused("missing-contact-name")
        val message = if (mode == ContactMode.Sms) {
            invocation.arg("message", "body", "text")
                ?.cleanContactValue()
                ?: goal.text.extractSmsBody(name)
                ?: return RecipeProposal.Refused("missing-message")
        } else {
            null
        }
        if (listOfNotNull(name, message).any { it.containsBlockedSensitiveContactValue() }) {
            return RecipeProposal.Refused("sensitive-contact-blocked")
        }

        val matches = when (val result = findContacts(name)) {
            ContactLookupResult.PermissionDenied ->
                return RecipeProposal.Refused("contacts-permission-required")
            is ContactLookupResult.Matches -> result.contacts
        }
        if (matches.isEmpty()) {
            return RecipeProposal.Refused("contact-not-found:$name")
        }
        if (matches.size > 1) {
            val labels = matches.map { it.label }.distinct().take(5)
            return RecipeProposal.Refused(
                reason = "ambiguous-contact:Which one? ${labels.joinToString()}",
                candidateLabels = labels,
            )
        }

        val match = matches.single()
        val phone = match.phoneNumber?.takeIf { it.isNotBlank() }
        val action = when (mode) {
            ContactMode.Open -> AssistantAction.OpenContact(match.contactUri)
            ContactMode.Call -> AssistantAction.DialNumber(
                phone ?: return RecipeProposal.Refused("contact-has-no-phone"),
            )
            ContactMode.Sms -> AssistantAction.ComposeSms(
                to = phone ?: return RecipeProposal.Refused("contact-has-no-phone"),
                body = message,
            )
        }
        val title = when (mode) {
            ContactMode.Open -> "Open ${match.label}'s contact"
            ContactMode.Call -> "Open dialer for ${match.label}"
            ContactMode.Sms -> "Open SMS draft to ${match.label}"
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Contacts",
                summary = title,
                steps = listOf(
                    RecipeStep(
                        id = mode.stepId,
                        title = title,
                        command = RecipeCommand.NativeAction(
                            action = action,
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }
}

class AndroidContactsResolver(
    private val context: Context,
) {
    fun find(name: String): ContactLookupResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ContactLookupResult.PermissionDenied
        }

        return runCatching {
            val contacts = queryContacts(name)
            ContactLookupResult.Matches(contacts)
        }.getOrElse { error ->
            if (error is SecurityException) {
                ContactLookupResult.PermissionDenied
            } else {
                Timber.w(error, "ContactsRecipe: contact query failed")
                ContactLookupResult.Matches(emptyList())
            }
        }
    }

    private fun queryContacts(name: String): List<ContactMatch> {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val args = arrayOf("%${name.trim()}%")
        val sort = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
        val out = mutableListOf<ContactMatch>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext() && out.size < MAX_CONTACT_MATCHES) {
                val id = cursor.getLong(idIdx)
                val lookupKey = cursor.getString(lookupIdx) ?: continue
                val label = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                val uri = ContactsContract.Contacts.getLookupUri(id, lookupKey)?.toString() ?: continue
                out += ContactMatch(
                    label = label,
                    contactUri = uri,
                    phoneNumber = queryPhoneNumber(id),
                )
            }
        }
        return out
    }

    private fun queryPhoneNumber(contactId: Long): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val args = arrayOf(contactId.toString())
        return context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
        }?.cleanPhoneNumber()
    }
}

sealed class ContactLookupResult {
    data object PermissionDenied : ContactLookupResult()
    data class Matches(val contacts: List<ContactMatch>) : ContactLookupResult()
}

data class ContactMatch(
    val label: String,
    val contactUri: String,
    val phoneNumber: String? = null,
)

private enum class ContactMode(val stepId: String) {
    Open("open-contact"),
    Call("open-dialer"),
    Sms("open-sms-draft"),
}

private fun UserGoal.contactMode(invocation: RecipeInvocation): ContactMode? {
    val mode = invocation.arg("mode", "action")?.normalizeRecipeText()
    return when {
        requestedIntent == "open_contact" || mode == "open" -> ContactMode.Open
        requestedIntent == "prepare_call" || mode == "call" || text.startsWith("call ", ignoreCase = true) ->
            ContactMode.Call
        requestedIntent == "prepare_sms" || mode in setOf("text", "sms", "message") ->
            ContactMode.Sms
        text.startsWith("text ", ignoreCase = true) || text.startsWith("message ", ignoreCase = true) ->
            ContactMode.Sms
        else -> null
    }
}

private fun String.extractContactName(mode: ContactMode): String? = when (mode) {
    ContactMode.Open -> OPEN_CONTACT_PATTERN.find(this)?.groupValues?.getOrNull(1)?.cleanContactValue()
    ContactMode.Call -> CALL_CONTACT_PATTERN.find(this)?.groupValues?.getOrNull(1)?.cleanContactValue()
    ContactMode.Sms -> smsParts()?.first?.cleanContactValue()
}

private fun String.extractSmsBody(name: String): String? {
    val body = smsParts()?.second?.cleanContactValue() ?: return null
    return body.takeIf { it != name }
}

private fun String.smsParts(): Pair<String, String?>? {
    val tail = SMS_CONTACT_PATTERN.find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: return null
    val parts = tail.split(Regex("""\s+"""), limit = 2)
    val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
    return name to parts.getOrNull(1)
}

private fun String.cleanContactValue(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.cleanPhoneNumber(): String? =
    trim()
        .replace(Regex("""[^\d+]+"""), "")
        .takeIf { it.any(Char::isDigit) }

private fun String.containsBlockedSensitiveContactValue(): Boolean {
    val normalized = lowercase()
    return CARD_LIKE_REGEX.containsMatchIn(this) ||
        CONTACT_BLOCKED_SENSITIVE_TERMS.any { normalized.contains(it) }
}

private val OPEN_CONTACT_PATTERN = Regex(
    pattern = """\bopen\s+(.+?)(?:'s)?\s+contact$""",
    options = setOf(RegexOption.IGNORE_CASE),
)
private val CALL_CONTACT_PATTERN = Regex("""\bcall\s+(.+)$""", RegexOption.IGNORE_CASE)
private val SMS_CONTACT_PATTERN = Regex(
    pattern = """\b(?:text|message|sms)\s+(.+)$""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val CONTACT_BLOCKED_SENSITIVE_TERMS = listOf(
    "password",
    "passcode",
    "otp",
    "one time password",
    "cvv",
    "cvc",
    "card number",
)

private val CARD_LIKE_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")
private const val MAX_CONTACT_MATCHES = 6
