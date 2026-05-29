package com.handy.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class ManifestPermissionTest {

    @Test
    fun manifestDeclaresAlarmIntentPermission() {
        val doc = manifestDocument()
        val permissions = doc.getElementsByTagName("uses-permission")
        val requested = (0 until permissions.length).mapNotNull { index ->
            permissions.item(index).attributes?.getNamedItem("android:name")?.nodeValue
        }

        assertThat(requested).contains("com.android.alarm.permission.SET_ALARM")
    }

    @Test
    fun manifestDeclaresNativeRecipeIntentQueries() {
        val queryIntents = manifestQueryIntents()
        val actions = queryIntents.map { it.action }

        assertThat(actions).containsAtLeast(
            "android.intent.action.SET_ALARM",
            "android.intent.action.SET_TIMER",
            "android.settings.APPLICATION_DETAILS_SETTINGS",
            "android.settings.ACCESSIBILITY_SETTINGS",
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
            "android.settings.DISPLAY_SETTINGS",
            "android.settings.WIFI_SETTINGS",
            "android.settings.BLUETOOTH_SETTINGS",
            "android.settings.SECURITY_SETTINGS",
            "android.settings.BIOMETRIC_ENROLL",
            "android.settings.APPLICATION_SETTINGS",
            "android.settings.SOUND_SETTINGS",
            "android.settings.ZEN_MODE_SETTINGS",
        )
        assertThat(queryIntents).contains(QueryIntentSpec("android.intent.action.SENDTO", "mailto"))
        assertThat(queryIntents).contains(QueryIntentSpec("android.intent.action.SENDTO", "smsto"))
    }

    private fun manifestDocument() =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(repoRoot().resolve("app/src/main/AndroidManifest.xml").toFile())

    private fun manifestQueryIntents(): List<QueryIntentSpec> {
        val intents = manifestDocument().getElementsByTagName("intent")
        return (0 until intents.length).mapNotNull { index ->
            val element = intents.item(index) as? Element ?: return@mapNotNull null
            val actions = element.childValues("action", "android:name")
            val schemes = element.childValues("data", "android:scheme")
            actions.flatMap { action ->
                if (schemes.isEmpty()) {
                    listOf(QueryIntentSpec(action = action, scheme = null))
                } else {
                    schemes.map { scheme -> QueryIntentSpec(action = action, scheme = scheme) }
                }
            }
        }.flatten()
    }

    private fun Element.childValues(tagName: String, attribute: String): List<String> {
        val children = getElementsByTagName(tagName)
        return (0 until children.length).mapNotNull { index ->
            children.item(index).attributes?.getNamedItem(attribute)?.nodeValue
        }
    }

    private fun repoRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) return current
            current = current.parent
        }
        error("Could not find repo root from ${Path.of("").toAbsolutePath()}")
    }

    private data class QueryIntentSpec(
        val action: String,
        val scheme: String?,
    )
}
