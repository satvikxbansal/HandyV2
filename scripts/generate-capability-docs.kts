import java.io.File

private const val README_START = "<!-- CAPABILITIES:README:START -->"
private const val README_END = "<!-- CAPABILITIES:README:END -->"
private const val PLAY_START = "<!-- CAPABILITIES:PLAY_FEATURE_CLAIMS:START -->"
private const val PLAY_END = "<!-- CAPABILITIES:PLAY_FEATURE_CLAIMS:END -->"
private const val PRIVACY_START = "<!-- CAPABILITIES:PRIVACY_DISCLOSURES:START -->"
private const val PRIVACY_END = "<!-- CAPABILITIES:PRIVACY_DISCLOSURES:END -->"

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

private fun parseCapabilities(file: File): List<Capability> {
    require(file.isFile) { "Missing capability manifest: ${file.path}" }
    val rows = mutableListOf<Pair<String, MutableMap<String, Any>>>()
    var current: Pair<String, MutableMap<String, Any>>? = null
    file.readLines().forEachIndexed { index, rawLine ->
        val withoutComment = rawLine.substringBefore("#").trimEnd()
        if (withoutComment.isBlank() || withoutComment == "capabilities:") return@forEachIndexed
        val rowMatch = Regex("^  ([A-Za-z0-9_]+):\\s*$").matchEntire(withoutComment)
        if (rowMatch != null) {
            current = rowMatch.groupValues[1] to linkedMapOf()
            rows += current!!
            return@forEachIndexed
        }
        val propMatch = Regex("^    ([A-Za-z0-9_]+):\\s*(.+)$").matchEntire(withoutComment)
            ?: error("Unsupported YAML at ${file.path}:${index + 1}: $rawLine")
        val row = current ?: error("Property before capability at ${file.path}:${index + 1}")
        row.second[propMatch.groupValues[1]] = parseYamlValue(propMatch.groupValues[2])
    }
    return rows.map { (id, map) ->
        Capability(
            id = id,
            title = map.string("title", id.replace('_', ' ')),
            active = map.bool("active"),
            status = map.string("status", if (map.bool("active")) "active" else "coming_soon"),
            scope = map.string("scope"),
            reason = map.string("reason", ""),
            settings = map.string("settings", "none"),
            list = map.list("list"),
        )
    }
}

private fun parseYamlValue(raw: String): Any {
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
    this[key] as? String ?: default ?: error("CAPABILITIES.yaml missing '$key'")

private fun Map<String, Any>.bool(key: String): Boolean =
    this[key] as? Boolean ?: error("CAPABILITIES.yaml missing boolean '$key'")

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.list(key: String): List<String> =
    this[key] as? List<String> ?: emptyList()

private fun buildReadmeBlock(capabilities: List<Capability>): String = buildString {
    appendLine(README_START)
    appendLine("Handy Android is past scaffold stage. The capability claims in this block are generated from [`docs/CAPABILITIES.yaml`](docs/CAPABILITIES.yaml); edit the manifest, then run `./gradlew generateCapabilityDocs`.")
    appendLine()
    appendCapabilities("### Active", capabilities.filter { it.active && it.statusLabel == "Active" })
    appendCapabilities("### Off by default", capabilities.filter { it.active && it.statusLabel == "Off by default" })
    appendCapabilities("### Coming soon / out of beta", capabilities.filter { !it.active })
    capabilities.firstOrNull { it.id == "recipes" }?.takeIf { it.list.isNotEmpty() }?.let { recipes ->
        appendLine("### Recipe families")
        appendLine()
        appendLine(recipes.list.joinToString(prefix = "- `", separator = "`, `", postfix = "`"))
        appendLine()
    }
    appendLine("### Practical behavior")
    appendLine()
    appendLine("- A user asking \"Where is Search?\" gets an explanation and Buddy pointing at the visible Search control.")
    appendLine("- A user asking \"Tap Search for me\" gets a Tap-for-me confirmation first; the tap is refused if the target is stale, sensitive, ambiguous, or low confidence.")
    appendLine("- A user asking \"Type Delhi here\" gets ordinary text insertion only into a visible non-sensitive field after confirmation.")
    appendLine("- A user asking \"Install Spotify\" gets a Play Store listing or search handoff; Handy never taps Install.")
    appendLine("- A user asking for a payment, banking action, password, OTP, card entry, checkout, purchase, delete, or personal-data submission gets a block instead of automation.")
    appendLine(README_END)
}

private fun buildPlayBlock(capabilities: List<Capability>): String = buildString {
    appendLine(PLAY_START)
    appendLine("Handy uses AccessibilityService for a general screen-aware AI copilot experience. After prominent disclosure and user consent, the service supports only the capabilities marked active in [`docs/CAPABILITIES.yaml`](docs/CAPABILITIES.yaml).")
    appendLine()
    appendCapabilities("Active capabilities", capabilities.filter { it.active && it.statusLabel == "Active" })
    appendCapabilities("Off-by-default capabilities", capabilities.filter { it.active && it.statusLabel == "Off by default" })
    appendCapabilities("Not active in this beta", capabilities.filter { !it.active })
    appendLine("Safety boundaries generated from the manifest:")
    appendLine()
    appendLine("- Web-tool output is informational evidence only and cannot trigger device actions.")
    appendLine("- Tap-for-me and Type-for-me require a separate disclosure, a visible target, policy approval, and user confirmation.")
    appendLine("- Payments, banking app automation, password/OTP/card typing, secure-window content, purchases, checkout, deletion, and personal-data submission are outside the active beta scope.")
    appendLine(PLAY_END)
}

private fun buildPrivacyBlock(capabilities: List<Capability>): String = buildString {
    val privacyCapabilities = capabilities.filterNot {
        !it.active && it.id in setOf("stt_sarvam", "tts_sarvam")
    }
    appendLine(PRIVACY_START)
    appendLine("This section is generated from [`docs/CAPABILITIES.yaml`](docs/CAPABILITIES.yaml).")
    appendLine()
    appendCapabilities("### Active by default or permission", privacyCapabilities.filter { it.active && it.statusLabel == "Active" })
    appendCapabilities("### Off by default until you opt in", privacyCapabilities.filter { it.active && it.statusLabel == "Off by default" })
    appendCapabilities("### Not active in this beta", privacyCapabilities.filter { !it.active })
    appendLine("## What Handy will not do")
    appendLine()
    appendLine("- Handy will not listen or capture the screen in the background.")
    appendLine("- Handy will not let fetched web pages or tool results trigger actions on your phone.")
    appendLine("- Handy will not run open-ended LLM-authored plans.")
    appendLine("- Handy will not type passwords, OTPs, card numbers, CVVs, recovery codes, private keys, seed phrases, or secure-window content.")
    appendLine("- Handy will not pay, purchase, checkout, transfer money, delete, add to cart, apply coupons, submit personal data, or automate banking/payment/password-manager/authenticator apps in this beta.")
    appendLine()
    appendLine("## What leaves the device")
    appendLine()
    appendLine("- Your typed message or recognized voice transcript when you send a turn.")
    appendLine("- The minimum screen context needed for that turn when Accessibility is enabled, such as visible labels, roles, bounds, app/window metadata, and optional screenshot data when needed.")
    if (capabilities.any { it.id == "web_tools" && it.active }) {
        appendLine("- Optional public web-search queries, fetched-page URLs, and public GitHub queries when web tools are enabled.")
    }
    if (capabilities.any { it.id == "stt_sarvam" && it.active } || capabilities.any { it.id == "tts_sarvam" && it.active }) {
        appendLine("- Optional Sarvam cloud voice traffic only when the matching Sarvam voice feature is selected, consent is saved where required, and a user-supplied Sarvam API key is present.")
    }
    appendLine("- Action arguments sent through visible Android platform flows, such as a Maps query, calendar title, mail draft, or share text.")
    appendLine()
    appendLine("## What stays on the device")
    appendLine()
    appendLine("- API keys in Android Keystore-backed encrypted storage.")
    appendLine("- Chat history in app-private storage.")
    appendLine("- Redacted local action and timeline audit entries.")
    appendLine("- Per-turn screen snapshots after the turn; they are ephemeral and are not appended to chat history as hidden raw data.")
    appendLine("- Timber/logcat diagnostics must not contain API keys, screenshots, raw prompts, raw notification bodies, raw clipboard contents, or raw accessibility trees.")
    appendLine(PRIVACY_END)
}

private fun StringBuilder.appendCapabilities(title: String, rows: List<Capability>) {
    if (rows.isEmpty()) return
    appendLine(title)
    appendLine()
    rows.forEach { cap ->
        val reason = cap.reason.takeIf { it.isNotBlank() }?.let { " Reason: $it." }.orEmpty()
        val list = cap.list.takeIf { it.isNotEmpty() }?.joinToString(prefix = " Includes: `", separator = "`, `", postfix = "`.").orEmpty()
        appendLine("- **${cap.title}** (`${cap.id}`) - ${cap.scope}.$reason$list")
    }
    appendLine()
}

private fun buildCapabilitiesXml(capabilities: List<Capability>): String = buildString {
    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
    appendLine("<resources>")
    appendLine("    <!-- Generated by scripts/generate-capability-docs.kts. Do not edit manually. -->")
    appendArray("capability_ids", capabilities.map { it.id })
    appendArray("capability_titles", capabilities.map { it.title })
    appendArray("capability_statuses", capabilities.map { if (!it.active) "coming_soon" else it.status })
    appendArray("capability_status_labels", capabilities.map { it.statusLabel })
    appendArray("capability_scopes", capabilities.map { it.scope })
    appendArray("capability_reasons", capabilities.map { it.reason })
    appendArray("capability_settings_targets", capabilities.map { it.settings })
    appendArray("capability_recipe_lists", capabilities.map { it.list.joinToString(", ") })
    appendLine("</resources>")
}

private fun StringBuilder.appendArray(name: String, values: List<String>) {
    appendLine("""    <string-array name="$name">""")
    values.forEach { value -> appendLine("        <item>${value.xmlEscape()}</item>") }
    appendLine("    </string-array>")
}

private fun String.xmlEscape(): String = buildString {
    this@xmlEscape.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("\\'")
            else -> append(ch)
        }
    }
}

private fun generatedFiles(root: File, capabilities: List<Capability>): Map<File, String> {
    val readme = root.resolve("README.md")
    val play = root.resolve("PLAYSTORE_SUBMISSION.md")
    val privacy = root.resolve("PRIVACY_POLICY.md")
    return linkedMapOf(
        readme to replaceReadme(readme.readText(), buildReadmeBlock(capabilities)),
        play to replacePlay(play.readText(), buildPlayBlock(capabilities)),
        privacy to replacePrivacy(privacy.readText(), buildPrivacyBlock(capabilities)),
        root.resolve("app/src/main/res/values/capabilities.xml") to buildCapabilitiesXml(capabilities),
    )
}

private fun replaceReadme(current: String, block: String): String =
    replaceMarkedOrFallback(
        current = current,
        start = README_START,
        end = README_END,
        block = block,
        fallbackStart = "## Current State\n\n",
        fallbackEnd = "\n---\n\n## Latest Implementation Evaluation",
        keepFallbackStart = true,
        keepFallbackEnd = true,
    )

private fun replacePlay(current: String, block: String): String =
    replaceMarkedOrFallback(
        current = current,
        start = PLAY_START,
        end = PLAY_END,
        block = block,
        fallbackStart = "### 4.3 What does the service do?\n\n",
        fallbackEnd = "Verbatim in-app Accessibility service description",
        keepFallbackStart = true,
        keepFallbackEnd = true,
    )

private fun replacePrivacy(current: String, block: String): String =
    replaceMarkedOrFallback(
        current = current,
        start = PRIVACY_START,
        end = PRIVACY_END,
        block = block,
        fallbackStart = "## What Handy can do\n\n",
        fallbackEnd = "\n## Tap-for-me",
        keepFallbackStart = true,
        keepFallbackEnd = true,
    )

private fun replaceMarkedOrFallback(
    current: String,
    start: String,
    end: String,
    block: String,
    fallbackStart: String,
    fallbackEnd: String,
    keepFallbackStart: Boolean,
    keepFallbackEnd: Boolean,
): String {
    val markedRegex = Regex("${Regex.escape(start)}.*?${Regex.escape(end)}", RegexOption.DOT_MATCHES_ALL)
    if (markedRegex.containsMatchIn(current)) return markedRegex.replace(current, block.trimEnd())
    val startIndex = current.indexOf(fallbackStart)
    val endIndex = current.indexOf(fallbackEnd, startIndex + fallbackStart.length)
    if (startIndex < 0 || endIndex < 0) {
        error("Could not find generated block or fallback anchors for $start")
    }
    val prefixEnd = if (keepFallbackStart) startIndex + fallbackStart.length else startIndex
    val suffixStart = if (keepFallbackEnd) endIndex else endIndex + fallbackEnd.length
    return current.substring(0, prefixEnd) + block.trimEnd() + "\n" + current.substring(suffixStart)
}

val root = File(args.getOrNull(0) ?: ".").canonicalFile
val mode = args.getOrNull(1) ?: "generate"
val capabilities = parseCapabilities(root.resolve("docs/CAPABILITIES.yaml"))
val files = generatedFiles(root, capabilities)

when (mode) {
    "generate" -> files.forEach { (file, text) ->
        if (file.exists() && file.readText() == text) return@forEach
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
    "verify" -> {
        val stale = files.filter { (file, expected) ->
            !file.exists() || file.readText() != expected
        }.keys.map { it.relativeTo(root).path }
        check(stale.isEmpty()) {
            "Capability docs are out of sync. Run ./gradlew generateCapabilityDocs. Stale: ${stale.joinToString()}"
        }
    }
    else -> error("Unknown mode '$mode'. Use generate or verify.")
}
