package com.handy.app.settings

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

class CapabilityManifestSyncTest {

    @Test
    fun capabilitiesXmlMatchesManifestOrderAndValues() {
        val root = repoRoot()
        val capabilities = parseManifest(root.resolve("docs/CAPABILITIES.yaml"))
        val arrays = readStringArrays(root.resolve("app/src/main/res/values/capabilities.xml"))

        assertThat(arrays["capability_ids"]).containsExactlyElementsIn(capabilities.map { it.id }).inOrder()
        assertThat(arrays["capability_titles"]).containsExactlyElementsIn(capabilities.map { it.title }).inOrder()
        assertThat(arrays["capability_statuses"]).containsExactlyElementsIn(
            capabilities.map { if (!it.active) "coming_soon" else it.status },
        ).inOrder()
        assertThat(arrays["capability_status_labels"]).containsExactlyElementsIn(
            capabilities.map { it.statusLabel },
        ).inOrder()
        assertThat(arrays["capability_scopes"]).containsExactlyElementsIn(capabilities.map { it.scope }).inOrder()
        assertThat(arrays["capability_reasons"]).containsExactlyElementsIn(capabilities.map { it.reason }).inOrder()
        assertThat(arrays["capability_settings_targets"]).containsExactlyElementsIn(
            capabilities.map { it.settings },
        ).inOrder()
        assertThat(arrays["capability_recipe_lists"]).containsExactlyElementsIn(
            capabilities.map { it.list.joinToString(", ") },
        ).inOrder()
    }

    @Test
    fun generatedDocsContainManifestRowsAndDoNotPromoteInactiveCapabilities() {
        val root = repoRoot()
        val capabilities = parseManifest(root.resolve("docs/CAPABILITIES.yaml"))
        val readme = block(
            root.resolve("README.md").toFile().readText(),
            "<!-- CAPABILITIES:README:START -->",
            "<!-- CAPABILITIES:README:END -->",
        )
        val play = block(
            root.resolve("PLAYSTORE_SUBMISSION.md").toFile().readText(),
            "<!-- CAPABILITIES:PLAY_FEATURE_CLAIMS:START -->",
            "<!-- CAPABILITIES:PLAY_FEATURE_CLAIMS:END -->",
        )
        val privacy = block(
            root.resolve("PRIVACY_POLICY.md").toFile().readText(),
            "<!-- CAPABILITIES:PRIVACY_DISCLOSURES:START -->",
            "<!-- CAPABILITIES:PRIVACY_DISCLOSURES:END -->",
        )

        capabilities.forEach { capability ->
            val row = "**${capability.title}** (`${capability.id}`) - ${capability.scope}"
            assertThat(readme).contains(row)
            assertThat(play).contains(row)
            if (capability.active || capability.id !in setOf("stt_sarvam", "tts_sarvam")) {
                assertThat(privacy).contains(row)
            }
        }

        val readmePromoted = readme
            .substringAfter("### Active")
            .substringBefore("### Coming soon / out of beta")
        capabilities.filterNot { it.active }.forEach { inactive ->
            assertThat(readmePromoted).doesNotContain("`${inactive.id}`")
        }
    }

    @Test
    fun privacyMentionsSarvamCloudOnlyWhenCloudVoiceCapabilitiesAreActive() {
        val root = repoRoot()
        val capabilities = parseManifest(root.resolve("docs/CAPABILITIES.yaml"))
        val privacy = root.resolve("PRIVACY_POLICY.md").toFile().readText()
        val sarvamCloudActive = capabilities.any { it.id == "stt_sarvam" && it.active } ||
            capabilities.any { it.id == "tts_sarvam" && it.active }

        if (sarvamCloudActive) {
            assertThat(privacy).contains("Sarvam")
        } else {
            assertThat(privacy).doesNotContain("Sarvam")
        }
    }

    @Test
    fun releaseCopyDoesNotContainOldNeverTapsClaim() {
        val root = repoRoot()
        val releaseCopy = listOf(
            root.resolve("README.md"),
            root.resolve("PLAYSTORE_SUBMISSION.md"),
            root.resolve("PRIVACY_POLICY.md"),
        ).joinToString("\n") { it.toFile().readText() }

        assertThat(releaseCopy.lowercase()).doesNotContain("v1 never taps")
    }

    private fun repoRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("docs/CAPABILITIES.yaml"))) return current
            current = current.parent
        }
        error("Could not find docs/CAPABILITIES.yaml from ${Path.of("").toAbsolutePath()}")
    }

    private fun block(text: String, start: String, end: String): String {
        val startIndex = text.indexOf(start)
        val endIndex = text.indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) { "Missing generated block $start" }
        return text.substring(startIndex, endIndex + end.length)
    }

    private fun readStringArrays(path: Path): Map<String, List<String>> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(path.toFile())
        val arrays = document.getElementsByTagName("string-array")
        return (0 until arrays.length).associate { index ->
            val arrayNode = arrays.item(index)
            val name = arrayNode.attributes.getNamedItem("name").nodeValue
            val items = arrayNode.childNodes
            name to (0 until items.length)
                .map { items.item(it) }
                .filter { it.nodeName == "item" }
                .map { it.textContent }
        }
    }

    private data class Capability(
        val id: String,
        val title: String,
        val active: Boolean,
        val status: String,
        val scope: String,
        val reason: String,
        val settings: String,
        val list: List<String>,
    ) {
        val statusLabel: String
            get() = when {
                !active -> "Coming soon"
                status == "off_by_default" -> "Off by default"
                else -> "Active"
            }
    }

    private fun parseManifest(path: Path): List<Capability> {
        val rows = mutableListOf<Pair<String, MutableMap<String, Any>>>()
        var current: Pair<String, MutableMap<String, Any>>? = null
        path.toFile().readLines().forEachIndexed { index, raw ->
            val line = raw.substringBefore("#").trimEnd()
            if (line.isBlank() || line == "capabilities:") return@forEachIndexed
            val rowMatch = Regex("^  ([A-Za-z0-9_]+):\\s*$").matchEntire(line)
            if (rowMatch != null) {
                current = rowMatch.groupValues[1] to linkedMapOf()
                rows += current!!
                return@forEachIndexed
            }
            val propertyMatch = Regex("^    ([A-Za-z0-9_]+):\\s*(.+)$").matchEntire(line)
                ?: error("Unsupported YAML at $path:${index + 1}: $raw")
            current?.second?.set(propertyMatch.groupValues[1], parseValue(propertyMatch.groupValues[2]))
                ?: error("Property before capability at $path:${index + 1}")
        }
        return rows.map { (id, properties) ->
            Capability(
                id = id,
                title = properties.string("title", id.replace('_', ' ')),
                active = properties.bool("active"),
                status = properties.string("status", if (properties.bool("active")) "active" else "coming_soon"),
                scope = properties.string("scope"),
                reason = properties.string("reason", ""),
                settings = properties.string("settings", "none"),
                list = properties.list("list"),
            )
        }
    }

    private fun parseValue(raw: String): Any {
        val value = raw.trim()
        return when {
            value == "true" -> true
            value == "false" -> false
            value.startsWith("\"") && value.endsWith("\"") -> value.removeSurrounding("\"")
            value.startsWith("[") && value.endsWith("]") -> {
                val body = value.removePrefix("[").removeSuffix("]").trim()
                if (body.isBlank()) emptyList<String>() else body.split(",").map { it.trim().trim('"') }
            }
            else -> value
        }
    }

    private fun Map<String, Any>.string(key: String, default: String? = null): String =
        this[key] as? String ?: default ?: error("Missing string $key")

    private fun Map<String, Any>.bool(key: String): Boolean =
        this[key] as? Boolean ?: error("Missing boolean $key")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.list(key: String): List<String> =
        this[key] as? List<String> ?: emptyList()
}
