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
        assertThat(withSnapshot).contains("<screen_ui>")
        assertThat(withSnapshot).contains("com.google.android.gm")
        assertThat(withoutSnapshot).doesNotContain("<screen_ui>")
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
