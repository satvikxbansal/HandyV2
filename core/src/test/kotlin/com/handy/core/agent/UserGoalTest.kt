package com.handy.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UserGoalTest {

    @Test fun `parses and strips recipe directive`() {
        val text = """
            i can do that.
            use recipe tap_visible with args {"label":"Add an email address"}
        """.trimIndent()

        val goal = UserGoal.fromAssistantText(text)

        assertThat(goal.requestedRecipe?.recipeId).isEqualTo("tap_visible")
        assertThat(goal.requestedRecipe?.arg("label")).isEqualTo("Add an email address")
        assertThat(goal.text).isEqualTo("i can do that.")
    }

    @Test fun `parses and strips intent directive`() {
        val text = """
            i can do that.
            [INTENT:set_alarm]
            use recipe set_alarm with args {"time":"7am"}
        """.trimIndent()

        val goal = UserGoal.fromAssistantText(text)

        assertThat(goal.requestedIntent).isEqualTo("set_alarm")
        assertThat(goal.requestedRecipe?.recipeId).isEqualTo("set_alarm")
        assertThat(goal.requestedRecipe?.arg("time")).isEqualTo("7am")
        assertThat(goal.text).isEqualTo("i can do that.")
    }

    @Test fun `guidance questions do not allow recipe execution`() {
        assertThat(UserGoal.allowsRecipeExecution("how do I add a new email address?")).isFalse()
        assertThat(UserGoal.allowsRecipeExecution("show me where to tap")).isFalse()
        assertThat(UserGoal.allowsRecipeExecution("what can I do here?")).isFalse()
    }

    @Test fun `explicit do it for me requests allow recipe execution`() {
        assertThat(UserGoal.allowsRecipeExecution("tap add an email address for me")).isTrue()
        assertThat(UserGoal.allowsRecipeExecution("please type hello into this field")).isTrue()
        assertThat(UserGoal.allowsRecipeExecution("can you scroll down?")).isTrue()
        assertThat(UserGoal.allowsRecipeExecution("go ahead and do it")).isTrue()
    }

    @Test fun `help wording plus explicit automation still allows execution`() {
        assertThat(UserGoal.allowsRecipeExecution("can you show me where and tap it for me?")).isTrue()
    }

    @Test fun `canonical recipe requests allow execution`() {
        listOf(
            "open spotify",
            "install spotify",
            "set 7am alarm",
            "set a 10-minute timer",
            "google android 16 release notes",
            "turn on dnd",
            "ringtone settings",
            "schedule dentist tomorrow 3 pm",
            "book a cab to airport",
            "find cheap saree on meesho",
            "draft email to mom",
            "reply to john on whatsapp",
        ).forEach { request ->
            assertThat(UserGoal.allowsRecipeExecution(request)).isTrue()
        }
    }

    @Test fun `new action verbs still respect guidance-only wording`() {
        assertThat(UserGoal.allowsRecipeExecution("how do I set a timer?")).isFalse()
        assertThat(UserGoal.allowsRecipeExecution("where is brightness?")).isFalse()
        assertThat(UserGoal.allowsRecipeExecution("show me where to install spotify")).isFalse()
    }
}
