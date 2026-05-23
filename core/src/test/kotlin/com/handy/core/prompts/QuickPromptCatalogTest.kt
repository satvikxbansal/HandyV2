package com.handy.core.prompts

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class QuickPromptCatalogTest {

    @Test fun `meesho amazon and flipkart packages use shopping prompts`() {
        assertThat(QuickPromptCatalog.categorize("com.meesho.supply"))
            .isEqualTo(QuickPromptCatalog.AppCategory.SHOPPING)
        assertThat(QuickPromptCatalog.categorize("com.amazon.mShop.android.shopping"))
            .isEqualTo(QuickPromptCatalog.AppCategory.SHOPPING)
        assertThat(QuickPromptCatalog.categorize("com.flipkart.android"))
            .isEqualTo(QuickPromptCatalog.AppCategory.SHOPPING)
    }

    @Test fun `browser shopping site labels use shopping prompts`() {
        assertThat(
            QuickPromptCatalog.categorize(
                packageName = "com.android.chrome",
                siteLabel = "Meesho",
            ),
        ).isEqualTo(QuickPromptCatalog.AppCategory.SHOPPING)
        assertThat(
            QuickPromptCatalog.categorize(
                packageName = "com.android.chrome",
                siteLabel = "Flipkart",
            ),
        ).isEqualTo(QuickPromptCatalog.AppCategory.SHOPPING)
    }

    @Test fun `shopping prompts include Hindi and English shopping intents`() {
        val prompts = QuickPromptCatalog.promptsFor(QuickPromptCatalog.AppCategory.SHOPPING)

        assertThat(prompts).contains("Returnable hai? / Is this returnable?")
        assertThat(prompts).contains("Coupon dhoondo / Find coupons")
        assertThat(prompts).contains("Similar se compare karo / Compare with similar")
        assertThat(prompts).contains("Price sahi hai? / Is this a good price?")
    }

    @Test fun `summarize screen quick prompt is marked and reachable`() {
        val prompt = QuickPromptCatalog.quickPromptsFor(QuickPromptCatalog.AppCategory.UNKNOWN)
            .single { it.text == QuickPromptCatalog.SUMMARIZE_SCREEN_TEXT }

        assertThat(prompt.action).isEqualTo(QuickPromptCatalog.Action.SUMMARIZE_SCREEN)
    }
}
