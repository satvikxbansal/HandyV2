package com.handy.core.parsing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Golden-string tests for the macOS-ported parser. If one of these fails,
 * you have drifted from the `PointParser.swift` contract — stop and
 * reason about why before "fixing" the test.
 */
class AssistantMarkupParserTest {

    // ---- stripPointTags -----------------------------------------------------

    @Test fun `stripPointTags removes a trailing pixel pointer`() {
        val input = "click the save button. [POINT:120,40:save]"
        assertThat(AssistantMarkupParser.stripPointTags(input))
            .isEqualTo("click the save button.")
    }

    @Test fun `stripPointTags removes a trailing semantic pointer`() {
        val input = "tap the gear icon. [POINT:role=button;text=Settings]"
        assertThat(AssistantMarkupParser.stripPointTags(input))
            .isEqualTo("tap the gear icon.")
    }

    @Test fun `stripPointTags removes POINT_none`() {
        val input = "html is a markup language. [POINT:none]"
        assertThat(AssistantMarkupParser.stripPointTags(input))
            .isEqualTo("html is a markup language.")
    }

    @Test fun `stripPointTags collapses triple newlines to double`() {
        val input = "line one\n\n\n\n[POINT:none]"
        assertThat(AssistantMarkupParser.stripPointTags(input))
            .isEqualTo("line one")
    }

    @Test fun `stripPointTags preserves double newlines inside body`() {
        val input = "para one.\n\npara two. [POINT:none]"
        assertThat(AssistantMarkupParser.stripPointTags(input))
            .isEqualTo("para one.\n\npara two.")
    }

    @Test fun `stripDisplayMarkup removes internal spoken and point tags`() {
        val input = "[SPOKEN]tap search.[/SPOKEN]\n\nopen search. [POINT:desc=Search]"
        assertThat(AssistantMarkupParser.stripDisplayMarkup(input))
            .isEqualTo("tap search.\n\nopen search.")
    }

    @Test fun `stripDisplayMarkup hides trailing partial control tag while streaming`() {
        val input = "[SPOKEN]tap search.[/SPOKEN]\n\n[POINT:desc=Sea"
        assertThat(AssistantMarkupParser.stripDisplayMarkup(input))
            .isEqualTo("tap search.")
    }

    // ---- parsePoint: semantic forms ----------------------------------------

    @Test fun `parsePoint reads role and text`() {
        val result = AssistantMarkupParser.parsePoint("go there [POINT:role=button;text=Share]")
        assertThat(result.semantic?.role).isEqualTo("button")
        assertThat(result.semantic?.text).isEqualTo("Share")
        assertThat(result.semantic?.viewId).isNull()
        assertThat(result.isNone).isFalse()
    }

    @Test fun `parsePoint reads markId`() {
        val result = AssistantMarkupParser.parsePoint("tap this [POINT:markId=m3]")
        assertThat(result.semantic?.markId).isEqualTo("m3")
        assertThat(result.semantic?.text).isNull()
        assertThat(result.isNone).isFalse()
    }

    @Test fun `parsePoint carries controlled type text and strips type tag`() {
        val result = AssistantMarkupParser.parsePoint("type milk [TYPE:text=milk] [POINT:markId=m3]")

        assertThat(result.typeText).isEqualTo("milk")
        assertThat(result.semantic?.markId).isEqualTo("m3")
        assertThat(result.cleanedText).isEqualTo("type milk")
    }

    @Test fun `parsePoint ignores unknown semantic keys without throwing`() {
        val result = AssistantMarkupParser.parsePoint("tap this [POINT:foo=bar]")
        assertThat(result.semantic).isNull()
        assertThat(result.pixel).isNull()
        assertThat(result.isNone).isFalse()
    }

    @Test fun `parsePoint reads viewId`() {
        val result = AssistantMarkupParser.parsePoint("here: [POINT:viewId=send_btn]")
        assertThat(result.semantic?.viewId).isEqualTo("send_btn")
        assertThat(result.semantic?.text).isNull()
    }

    @Test fun `parsePoint reads contentDescription`() {
        val result = AssistantMarkupParser.parsePoint("[POINT:desc=Compose new email]")
        assertThat(result.semantic?.contentDescription).isEqualTo("Compose new email")
    }

    @Test fun `parsePoint handles POINT none as the none branch`() {
        val result = AssistantMarkupParser.parsePoint("general knowledge answer. [POINT:none]")
        assertThat(result.isNone).isTrue()
        assertThat(result.semantic).isNull()
        assertThat(result.pixel).isNull()
    }

    @Test fun `parsePoint semantic wins when both forms present`() {
        val input = "foo [POINT:120,30:save] bar [POINT:role=button;text=Share]"
        val result = AssistantMarkupParser.parsePoint(input)
        assertThat(result.semantic).isNotNull()
        assertThat(result.pixel).isNull()
    }

    // ---- parsePoint: legacy pixel compatibility ----------------------------

    @Test fun `parsePoint reads pixel with label and screen number`() {
        val result = AssistantMarkupParser.parsePoint("open it. [POINT:400,80:browser tab:screen2]")
        assertThat(result.pixel?.x).isEqualTo(400)
        assertThat(result.pixel?.y).isEqualTo(80)
        assertThat(result.pixel?.label).isEqualTo("browser tab")
        assertThat(result.pixel?.screenNumber).isEqualTo(2)
    }

    @Test fun `parsePoint reads last pixel match when multiple are present`() {
        val input = "thing one [POINT:1,1:a] thing two [POINT:99,99:b]"
        val result = AssistantMarkupParser.parsePoint(input)
        assertThat(result.pixel?.x).isEqualTo(99)
        assertThat(result.pixel?.label).isEqualTo("b")
    }

    // ---- extractSpokenPart --------------------------------------------------

    @Test fun `extractSpokenPart separates spoken from detail`() {
        val input = "[SPOKEN]tap share, then export.[/SPOKEN]\n\n" +
            "you can also pick png or pdf in the export panel. [POINT:role=button;text=Share]"
        val (spoken, display) = AssistantMarkupParser.extractSpokenPart(input)
        assertThat(spoken).isEqualTo("tap share, then export.")
        assertThat(display)
            .startsWith("tap share, then export.")
        assertThat(display)
            .contains("you can also pick png or pdf in the export panel.")
    }

    @Test fun `extractSpokenPart returns text unchanged when no SPOKEN tags`() {
        val input = "html is markup. [POINT:none]"
        val (spoken, display) = AssistantMarkupParser.extractSpokenPart(input)
        assertThat(spoken).isEqualTo(input)
        assertThat(display).isEqualTo(input)
    }

    @Test fun `extractSpokenPart when body is only SPOKEN block yields spoken-only display`() {
        val input = "[SPOKEN]yes.[/SPOKEN]"
        val (spoken, display) = AssistantMarkupParser.extractSpokenPart(input)
        assertThat(spoken).isEqualTo("yes.")
        assertThat(display).isEqualTo("yes.")
    }

    @Test fun `stripInternalTagsForDisplay hides spoken and point tags`() {
        val input = "[SPOKEN]tap search.[/SPOKEN] [POINT:desc=Search]"
        assertThat(AssistantMarkupParser.stripInternalTagsForDisplay(input))
            .isEqualTo("tap search.")
    }

    // ---- clamp rules --------------------------------------------------------

    @Test fun `clampVoiceSpokenForTts passes through short text`() {
        val input = "hello there."
        assertThat(AssistantMarkupParser.clampVoiceSpokenForTts(input)).isEqualTo(input)
    }

    @Test fun `clampVoiceSpokenForTts truncates at sentence boundary`() {
        val body = "first sentence is exactly twenty characters long. " +
            "second sentence here. third one comes along."
        // Build something > 420 chars by repeating sentences.
        val padded = body + body + body + body + body + body + body + body + body
        val clamped = AssistantMarkupParser.clampVoiceSpokenForTts(padded)
        assertThat(clamped.length).isAtMost(AssistantMarkupParser.MAX_CHARS_TTS)
        assertThat(clamped).endsWith(".")
    }

    @Test fun `clampVoiceSpokenForOverlay caps at 110 chars`() {
        val input = "a".repeat(400)
        val clamped = AssistantMarkupParser.clampVoiceSpokenForOverlay(input)
        assertThat(clamped.length).isAtMost(AssistantMarkupParser.MAX_CHARS_OVERLAY + 1) // + ellipsis
        assertThat(clamped).endsWith("…")
    }

    @Test fun `clamp constants match macOS verbatim`() {
        assertThat(AssistantMarkupParser.MAX_CHARS_TTS).isEqualTo(420)
        assertThat(AssistantMarkupParser.MAX_CHARS_OVERLAY).isEqualTo(110)
    }
}
