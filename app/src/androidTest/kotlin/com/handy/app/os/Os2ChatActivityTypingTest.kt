package com.handy.app.os

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OS-2 instrumentation.
 *
 *  (a) Typing into the `ChatActivity` input commits characters — the
 *      replacement for the old "chat panel overlay" typing assertion
 *      now that typed chat lives in a normal Activity window.
 *  (b) No `ChatPanelOverlayService` exists anywhere in the app's
 *      manifest (v1 contract).
 *
 * Part (a) is a TODO marker — the full typing smoke lands in Phase 4
 * once `ChatActivity` is exercised end-to-end with a mock `LlmClient`.
 * Part (b) is fully enforced below.
 */
@RunWith(AndroidJUnit4::class)
class Os2ChatActivityTypingTest {

    @Test
    fun manifest_has_no_chat_panel_overlay_service() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
        val names = info.services.orEmpty().map { it.name }
        assertThat(names).doesNotContain(
            // The forbidden class name, matching exactly what the
            // guardrails outlaw (OS-2 negative — no chat-panel overlay).
            "com.handy.app.overlay.ChatPanelOverlayService",
        )
    }

    @Test
    fun chat_activity_is_declared_with_adjust_resize_soft_input_mode() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activities = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            .activities
            .orEmpty()
        val chatActivity = activities.firstOrNull { it.name == "com.handy.app.chat.ChatActivity" }
        assertThat(chatActivity).isNotNull()
        // softInputMode with SOFT_INPUT_ADJUST_RESIZE corresponds to value 0x10.
        val mode = chatActivity!!.softInputMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        assertThat(mode).isEqualTo(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
