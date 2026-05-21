package com.handy.core.privacy

import com.handy.core.overlay.AccessibilityMark
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode

/**
 * Shared prompt/log redaction for screen-derived text.
 *
 * The rules are intentionally conservative: field-level password redaction is
 * unconditional; card-like numbers require a Luhn pass; OTP/CVV-style short
 * codes redact only when nearby labels indicate verification/security context.
 */
object ScreenRedactor {
    private val digitLikeRegex = Regex("""(?:\d[ -]?){12,19}\d""")
    private val shortCodeRegex = Regex("""\b\d{3,8}\b""")
    private val emailRegex = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("""(?<!\d)(?:\+?\d[\d .()-]{7,}\d)(?!\d)""")

    private val sensitiveContextTerms = listOf(
        "otp",
        "one time",
        "one-time",
        "verification",
        "verify",
        "code",
        "cvv",
        "cvc",
        "security code",
        "card",
        "debit",
        "credit",
    )

    fun redactSnapshot(snapshot: ScreenTextSnapshot, diagnostics: Boolean = false): ScreenTextSnapshot =
        snapshot.copy(root = redactNode(snapshot.root, context = "", diagnostics = diagnostics))

    fun redactMark(mark: AccessibilityMark, diagnostics: Boolean = false): AccessibilityMark {
        val context = listOfNotNull(mark.text, mark.contentDescription, mark.viewIdSuffix, mark.role)
            .joinToString(" ")
        return mark.copy(
            text = redactText(mark.text, context, mark.isPassword, diagnostics),
            contentDescription = redactText(
                mark.contentDescription,
                context,
                isPassword = mark.isPassword,
                diagnostics = diagnostics,
            ),
        )
    }

    // Controlled typing uses this same path with diagnostics=true to
    // fail closed before ACTION_SET_TEXT when proposed text would be masked.
    fun redactText(
        value: String?,
        context: String = "",
        isPassword: Boolean = false,
        diagnostics: Boolean = false,
    ): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isPassword) return REDACTED
        var out = redactCards(raw)
        if (isSensitiveContext(context + " " + raw)) {
            out = shortCodeRegex.replace(out, REDACTED)
        }
        if (diagnostics) {
            out = emailRegex.replace(out, REDACTED_EMAIL)
            out = phoneRegex.replace(out) { match ->
                if (match.value.count(Char::isDigit) >= 8) REDACTED_PHONE else match.value
            }
        }
        return out
    }

    private fun redactNode(node: UiNode, context: String, diagnostics: Boolean): UiNode {
        val nodeContext = listOfNotNull(
            context,
            node.role,
            node.text,
            node.contentDescription,
            node.viewIdResourceName,
        ).joinToString(" ")
        return node.copy(
            text = redactText(node.text, nodeContext, isPassword = false, diagnostics = diagnostics),
            contentDescription = redactText(
                node.contentDescription,
                nodeContext,
                isPassword = false,
                diagnostics = diagnostics,
            ),
            children = node.children.map { child ->
                redactNode(child, nodeContext, diagnostics)
            },
        )
    }

    private fun redactCards(value: String): String =
        digitLikeRegex.replace(value) { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 13..19 && passesLuhn(digits)) REDACTED_CARD else match.value
        }

    private fun isSensitiveContext(value: String): Boolean {
        val normalized = value.lowercase()
        return sensitiveContextTerms.any { normalized.contains(it) }
    }

    private fun passesLuhn(digits: String): Boolean {
        var sum = 0
        var doubleIt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (doubleIt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            doubleIt = !doubleIt
        }
        return sum % 10 == 0
    }

    private const val REDACTED = "[redacted]"
    private const val REDACTED_CARD = "[redacted-card]"
    private const val REDACTED_EMAIL = "[redacted-email]"
    private const val REDACTED_PHONE = "[redacted-phone]"
}
