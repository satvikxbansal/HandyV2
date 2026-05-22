package com.handy.app.privacy

import com.google.common.truth.Truth.assertThat
import com.handy.core.privacy.Sensitive
import com.lemonappdev.konsist.api.Konsist
import org.junit.Test

class SensitiveLoggingKonsistTest {

    @Test fun `Timber debug calls do not reference Sensitive properties`() {
        val sensitiveNames = Konsist
            .scopeFromProject()
            .properties()
            .filter { it.text.contains("@${Sensitive::class.simpleName}") }
            .map { it.name }
            .filter { it.isNotBlank() }
            .toSet()

        val violations = Konsist
            .scopeFromProduction()
            .functions(includeLocal = true)
            .flatMap { function ->
                timberDebugCalls(function.text).flatMap { call ->
                    sensitiveNames.mapNotNull { name ->
                        val rawArgument = Regex(
                            """(?:,|\()\s*(?:[\w)]+\.)*${Regex.escape(name)}\s*(?:,|\))""",
                        )
                        if (rawArgument.containsMatchIn(call)) {
                            "${function.location}: $name"
                        } else {
                            null
                        }
                    }
                }
            }

        assertThat(violations).isEmpty()
    }

    private fun timberDebugCalls(source: String): List<String> =
        Regex("""Timber\.d\([\s\S]*?(?:\n\s*\)|\))""")
            .findAll(source)
            .map { it.value }
            .toList()
}
