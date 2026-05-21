package com.handy.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CorrectionIntentTest {

    @Test
    fun `no asks for the other candidate`() {
        assertThat(CorrectionIntent.classify("no"))
            .isEqualTo(CorrectionIntent.Other())
    }

    @Test
    fun `other one asks for the other candidate`() {
        assertThat(CorrectionIntent.classify("other one"))
            .isEqualTo(CorrectionIntent.Other())
    }

    @Test
    fun `no the other continue keeps label hint`() {
        assertThat(CorrectionIntent.classify("no, the other Continue"))
            .isEqualTo(CorrectionIntent.Other(labelHint = "continue"))
    }

    @Test
    fun `next and previous map to relative hops`() {
        assertThat(CorrectionIntent.classify("next")).isEqualTo(CorrectionIntent.Next)
        assertThat(CorrectionIntent.classify("previous")).isEqualTo(CorrectionIntent.Previous)
    }

    @Test
    fun `popup phrase maps to popup intent`() {
        assertThat(CorrectionIntent.classify("the popup one")).isEqualTo(CorrectionIntent.Popup)
    }

    @Test
    fun `unrelated text is not a correction`() {
        assertThat(CorrectionIntent.classify("what is on this screen")).isNull()
    }
}
