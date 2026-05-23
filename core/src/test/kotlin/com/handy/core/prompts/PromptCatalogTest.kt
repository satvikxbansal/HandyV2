package com.handy.core.prompts

import com.google.common.truth.Truth.assertThat
import com.handy.core.model.AssistantMode
import org.junit.jupiter.api.Test

/**
 * Structural checks on the ported prompts. We do not diff against the
 * Swift source directly (different lowercase / quote conventions), but
 * we assert the platform-adaptation rules from
 * `10-handy-project-guardrails.mdc` were applied.
 */
class PromptCatalogTest {

    private val allPrompts = listOf(
        PromptCatalog.CHAT_SYSTEM_PROMPT,
        PromptCatalog.VOICE_SYSTEM_PROMPT,
        PromptCatalog.TUTOR_MODE_SYSTEM_PROMPT,
    )

    @Test fun `no prompt mentions macOS-only platforms as current`() {
        allPrompts.forEach { prompt ->
            assertThat(prompt.lowercase())
                .doesNotContain("macos only")
            assertThat(prompt.lowercase())
                .doesNotContain("user is on macos")
        }
    }

    @Test fun `no prompt tells the user to open handy from the menu bar`() {
        allPrompts.forEach { prompt ->
            assertThat(prompt.lowercase())
                .doesNotContain("menu bar")
        }
    }

    @Test fun `no prompt generates pixel-form POINT tags as the canonical shape`() {
        // Legacy pixel form `[POINT:x,y:label]` must not appear as an
        // example that the model could copy. Only the semantic forms.
        allPrompts.forEach { prompt ->
            assertThat(prompt).doesNotContain("[POINT:x,y:label]")
        }
    }

    @Test fun `every prompt teaches the semantic POINT forms`() {
        allPrompts.forEach { prompt ->
            assertThat(prompt).contains("[POINT:markId=")
            assertThat(prompt).contains("[POINT:role=")
            assertThat(prompt).contains("[POINT:viewId=")
            assertThat(prompt).contains("[POINT:desc=")
            assertThat(prompt).contains("[POINT:none]")
        }
    }

    @Test fun `voice prompt teaches the SPOKEN tag contract`() {
        assertThat(PromptCatalog.VOICE_SYSTEM_PROMPT).contains("[SPOKEN]")
        assertThat(PromptCatalog.VOICE_SYSTEM_PROMPT).contains("[/SPOKEN]")
    }

    @Test fun `web search addendum is dynamic on brave key`() {
        val withBrave = PromptCatalog.webSearchAddendum(hasBraveKey = true)
        val withoutBrave = PromptCatalog.webSearchAddendum(hasBraveKey = false)
        assertThat(withBrave).contains("web_search")
        assertThat(withoutBrave).contains("you do NOT have web_search")
    }

    @Test fun `buildSystemPrompt omits web search body when disabled`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = true,
        )
        assertThat(prompt).doesNotContain("web_search")
    }

    @Test fun `buildSystemPrompt appends screen-text addendum only when snapshot is provided`() {
        val withSnapshot = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
            screenTextPackage = "com.google.android.gm",
            screenTextFlattenedTree = "[Button] \"Send\" (id/send_btn) @ 0,0-10,10",
        )
        val withoutSnapshot = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
        )
        // The chat prompt itself references `<screen_ui>` inside its
        // pointing rules (verbatim V1 text), so both prompts contain
        // that literal. The addendum is identifiable by its distinctive
        // "screen text (from accessibility):" lead-in.
        assertThat(withSnapshot).contains("<screen_ui>")
        assertThat(withSnapshot).contains("com.google.android.gm")
        assertThat(withSnapshot).contains("screen text (from accessibility):")
        assertThat(withSnapshot).contains("controlled typing")
        assertThat(withSnapshot).contains("[TYPE:text=")
        assertThat(withoutSnapshot).doesNotContain("screen text (from accessibility):")
        assertThat(withoutSnapshot).doesNotContain("com.google.android.gm")
    }

    @Test fun `buildSystemPrompt picks tutor prompt when mode is TUTOR`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.TUTOR,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
        )
        assertThat(prompt).contains("you're handy in tutor mode")
    }

    @Test fun `buildSystemPrompt picks voice prompt when fromVoice is true`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = true,
            webSearchEnabled = false,
            hasBraveKey = false,
        )
        assertThat(prompt).contains("[SPOKEN]")
    }

    @Test fun `buildSystemPrompt adds quick overlay spoken contract when requested`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
            quickOverlayResponse = true,
        )
        assertThat(prompt).contains("quick overlay response:")
        assertThat(prompt).contains("[SPOKEN]")
        assertThat(prompt).contains("[POINT:")
        assertThat(prompt).contains("[POINT:markId=")
        assertThat(prompt).contains("visible button, menu item, or cta directly matches")
        assertThat(prompt).doesNotContain("[POINT:x,y:label]")
    }

    @Test fun `quick overlay prompt uses recipe directives instead of raw executable plans`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
            quickOverlayResponse = true,
        )
        assertThat(prompt).contains("agent-mode recipes:")
        assertThat(prompt).contains("[INTENT:<canonical>]")
        assertThat(prompt).contains("use recipe <canonical> with args")
        assertThat(prompt).contains("runner will pick the right recipe")
        assertThat(prompt).contains("open_app, set_alarm, set_timer")
        assertThat(prompt).contains("open spotify → [INTENT:open_app]")
        assertThat(prompt).contains("install spotify → [INTENT:install_app]")
        assertThat(prompt).contains("never emit raw executable plans")
        assertThat(prompt).contains("do NOT use recipes for guidance questions")
        assertThat(prompt).contains("answer normally and append exactly one [POINT:...] tag")
        assertThat(prompt).contains("Handy will re-check policy on a fresh snapshot before every step")
    }

    @Test fun `shopping addendum appears for Meesho package and teaches fetch page compare`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = true,
            webSearchEnabled = true,
            hasBraveKey = false,
            screenTextPackage = "com.meesho.supply",
            screenTextFlattenedTree = """
                m1 [TextView] "Women Printed Kurta" @ 0,0-100,20 enabled
                m2 [TextView] "₹399" @ 0,24-100,44 enabled
            """.trimIndent(),
            quickOverlayResponse = true,
        )

        assertThat(prompt).contains("shopping mode:")
        assertThat(prompt).contains("similar se compare karo")
        assertThat(prompt).contains("use fetch_page on the current product url")
        assertThat(prompt).contains("returnability")
        assertThat(prompt).contains("coupons/offers")
        assertThat(prompt).contains("[SPOKEN]")
    }

    @Test fun `shopping addendum appears for browser shopping domains only`() {
        val shoppingPrompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = true,
            hasBraveKey = false,
            screenTextPackage = "com.android.chrome",
            screenTextFlattenedTree = """m1 [EditText] "https://www.meesho.com/kurti/p/abc123" enabled""",
        )
        val normalPrompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = true,
            hasBraveKey = false,
            screenTextPackage = "com.android.chrome",
            screenTextFlattenedTree = """m1 [EditText] "https://example.com/article" enabled""",
        )

        assertThat(shoppingPrompt).contains("shopping mode:")
        assertThat(normalPrompt).doesNotContain("shopping mode:")
    }

    @Test fun `agent recipes include scoped shopping recipes and block shopping compare recipes`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
            quickOverlayResponse = true,
        )

        assertThat(prompt).contains("shopping_search")
        assertThat(prompt).contains("shopping_find_coupons")
        assertThat(prompt).contains("for shopping compare, price check, returnability, or summary questions")
        assertThat(prompt).contains("answer with visible/fetched evidence instead of a recipe")
    }

    @Test fun `buildSystemPrompt appends context failure addendum`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
            contextFailureReason = "screenshot capture is unsupported",
        )
        assertThat(prompt).contains("screen context note:")
        assertThat(prompt).contains("screenshot capture is unsupported")
    }

    @Test fun `intent tool addendum is appended by default`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
        )
        assertThat(prompt).contains("dispatch_action")
    }

    @Test fun `intent tool addendum can be disabled`() {
        val prompt = PromptCatalog.buildSystemPrompt(
            mode = AssistantMode.HELP_ONLY,
            fromVoice = false,
            webSearchEnabled = false,
            hasBraveKey = false,
            intentToolEnabled = false,
        )
        assertThat(prompt).doesNotContain("dispatch_action")
    }
}
