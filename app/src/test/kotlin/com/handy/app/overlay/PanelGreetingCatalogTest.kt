package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.tool.ToolContext
import org.junit.Test

class PanelGreetingCatalogTest {

    @Test
    fun `new greeting categories resolve from representative packages`() {
        val cases = listOf(
            "com.google.android.apps.photos" to PanelGreetingCategory.PHOTOS,
            "com.spotify.music" to PanelGreetingCategory.MUSIC,
            "com.google.android.apps.youtube.music" to PanelGreetingCategory.MUSIC,
            "com.google.android.youtube" to PanelGreetingCategory.VIDEO,
            "com.netflix.mediaclient" to PanelGreetingCategory.VIDEO,
            "com.whatsapp" to PanelGreetingCategory.MESSAGING,
            "com.instagram.android" to PanelGreetingCategory.SOCIAL,
            "com.google.android.calendar" to PanelGreetingCategory.CALENDAR,
            "notion.id" to PanelGreetingCategory.NOTES,
            "com.chase.sig.android" to PanelGreetingCategory.BANKING,
            "in.swiggy.android" to PanelGreetingCategory.FOOD,
            "com.ubercab" to PanelGreetingCategory.RIDE,
            "com.google.android.documentsui" to PanelGreetingCategory.FILES,
        )

        cases.forEach { (packageName, expected) ->
            assertThat(panelGreetingCategoryFor(packageName, siteLabel = null))
                .isEqualTo(expected)
        }
    }

    @Test
    fun `category greetings contain display label verbatim when provided`() {
        val cases = listOf(
            GreetingCase(PanelGreetingCategory.SETTINGS, "com.android.settings", "Settings"),
            GreetingCase(PanelGreetingCategory.BROWSER, "com.android.chrome", "Chrome"),
            GreetingCase(PanelGreetingCategory.EMAIL, "com.google.android.gm", "Gmail"),
            GreetingCase(PanelGreetingCategory.MAPS, "com.google.android.apps.maps", "Maps"),
            GreetingCase(PanelGreetingCategory.CAMERA, "com.google.android.GoogleCamera", "Camera"),
            GreetingCase(PanelGreetingCategory.PHONE, "com.google.android.dialer", "Phone"),
            GreetingCase(PanelGreetingCategory.SHOPPING, "com.flipkart.android", "Flipkart"),
            GreetingCase(PanelGreetingCategory.PHOTOS, "com.google.android.apps.photos", "Photos"),
            GreetingCase(PanelGreetingCategory.MUSIC, "com.spotify.music", "Spotify"),
            GreetingCase(PanelGreetingCategory.VIDEO, "com.netflix.mediaclient", "Netflix"),
            GreetingCase(PanelGreetingCategory.MESSAGING, "com.whatsapp", "WhatsApp"),
            GreetingCase(PanelGreetingCategory.SOCIAL, "com.instagram.android", "Instagram"),
            GreetingCase(PanelGreetingCategory.CALENDAR, "com.google.android.calendar", "Calendar"),
            GreetingCase(PanelGreetingCategory.NOTES, "notion.id", "Notion"),
            GreetingCase(PanelGreetingCategory.FOOD, "in.swiggy.android", "Swiggy"),
            GreetingCase(PanelGreetingCategory.RIDE, "com.ubercab", "Uber"),
            GreetingCase(PanelGreetingCategory.FILES, "com.google.android.documentsui", "Files"),
            GreetingCase(PanelGreetingCategory.DEFAULT, "com.example.reader", "Reader"),
        )

        cases.forEach { case ->
            assertThat(panelGreetingCategoryFor(case.packageName, siteLabel = null))
                .isEqualTo(case.category)

            val greeting = panelGreetingFor(snapshot(case.packageName, case.label))

            assertThat(greeting).contains(case.label)
        }
    }

    @Test
    fun `banking greeting intentionally drops display label`() {
        val label = "Chase"

        assertThat(panelGreetingCategoryFor("com.chase.sig.android", siteLabel = null))
            .isEqualTo(PanelGreetingCategory.BANKING)

        val greeting = panelGreetingFor(snapshot("com.chase.sig.android", label))

        assertThat(greeting).doesNotContain(label)
        assertThat(greeting).isEqualTo("Banking app open. I'll keep things general.")
    }

    private fun snapshot(
        packageName: String,
        appLabel: String,
        umbrellaSiteLabel: String? = null,
    ): PanelSnapshot = PanelSnapshot(
        toolContext = ToolContext(
            packageName = packageName,
            appLabel = appLabel,
            umbrellaSiteLabel = umbrellaSiteLabel,
        ),
        capturedAtEpochMs = 0L,
    )

    private data class GreetingCase(
        val category: PanelGreetingCategory,
        val packageName: String,
        val label: String,
    )
}
