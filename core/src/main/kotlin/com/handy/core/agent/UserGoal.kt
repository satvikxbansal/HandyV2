package com.handy.core.agent

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A user-facing goal plus the only machine-actionable shape the model may emit
 * for multi-step UI work: a named recipe and primitive JSON arguments.
 */
data class UserGoal(
    val text: String,
    val requestedRecipe: RecipeInvocation? = null,
) {
    companion object {
        fun fromAssistantText(text: String): UserGoal =
            UserGoal(
                text = stripRecipeDirective(text),
                requestedRecipe = RecipeInvocation.parse(text),
            )

        fun stripRecipeDirective(text: String): String =
            RecipeInvocation.directiveRegex.replace(text, "")
                .replace(Regex("""[ \t]+\n"""), "\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()

        fun allowsRecipeExecution(userText: String): Boolean {
            val normalized = normalize(userText)
            if (normalized.isBlank()) return false
            val explicitAutomation = EXECUTION_PATTERNS.any { it.containsMatchIn(normalized) }
            if (!explicitAutomation) return false
            val helpOnly = HELP_ONLY_PATTERNS.any { it.containsMatchIn(normalized) }
            return !helpOnly || FORCE_AUTOMATION_PATTERNS.any { it.containsMatchIn(normalized) }
        }

        private fun normalize(value: String): String =
            value.lowercase()
                .replace('-', ' ')
                .replace('_', ' ')
                .replace(Regex("""[^a-z0-9\s]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        private val HELP_ONLY_PATTERNS = listOf(
            Regex("""\bhow\s+(do|can|should)\s+i\b"""),
            Regex("""\bwhere\s+(do|can|should)\s+i\b"""),
            Regex("""\bwhere\s+is\b"""),
            Regex("""\bwhich\s+(button|field|menu|option|setting)\b"""),
            Regex("""\bshow\s+me\s+(where|how)\b"""),
            Regex("""\bwhat\s+can\s+i\s+do\s+here\b"""),
            Regex("""\bwhat\s+should\s+i\s+tap\b"""),
        )

        private val EXECUTION_PATTERNS = listOf(
            Regex("""^(please\s+)?(tap|click|press|select|choose|open|type|enter|fill|search|scroll|swipe)\b"""),
            Regex("""\b(can|could|would)\s+you\s+(please\s+)?(tap|click|press|select|choose|open|type|enter|fill|search|scroll|swipe)\b"""),
            Regex("""\b(go\s+ahead|do\s+it|do\s+this|take\s+over|handle\s+it)\b"""),
            Regex("""\bfor\s+me\b"""),
        )

        private val FORCE_AUTOMATION_PATTERNS = listOf(
            Regex("""\bfor\s+me\b"""),
            Regex("""\b(go\s+ahead|do\s+it|do\s+this|take\s+over|handle\s+it)\b"""),
            Regex("""\b(can|could|would)\s+you\s+(please\s+)?(tap|click|press|select|choose|open|type|enter|fill|search|scroll|swipe)\b"""),
        )
    }
}

data class RecipeInvocation(
    val recipeId: String,
    val args: Map<String, String>,
) {
    fun arg(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            args[name]?.takeIf { it.isNotBlank() }
                ?: args[name.lowercase()]?.takeIf { it.isNotBlank() }
        }

    companion object {
        internal val directiveRegex = Regex(
            pattern = """\buse\s+recipe\s+([a-zA-Z0-9_.-]+)\s+with\s+args\s+(\{.*?\})""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): RecipeInvocation? {
            val match = directiveRegex.findAll(text).lastOrNull() ?: return null
            val recipeId = match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                ?: return null
            val rawArgs = match.groupValues.getOrNull(2)?.trim().orEmpty()
            val args = parseArgs(rawArgs) ?: return null
            return RecipeInvocation(recipeId = recipeId, args = args)
        }

        private fun parseArgs(raw: String): Map<String, String>? = try {
            json.parseToJsonElement(raw).jsonObject
                .mapValuesNotNull { (_, value) ->
                    val primitive = value as? JsonPrimitive ?: return@mapValuesNotNull null
                    primitive.contentOrNull
                        ?: primitive.booleanOrNull?.toString()
                        ?: primitive.doubleOrNull?.toString()
                }
                .mapKeys { (key, _) -> key.lowercase() }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(
            transform: (Map.Entry<K, V>) -> R?,
        ): Map<K, R> {
            val out = LinkedHashMap<K, R>(size)
            for (entry in entries) {
                val next = transform(entry) ?: continue
                out[entry.key] = next
            }
            return out
        }
    }
}
