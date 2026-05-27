package com.handy.app.diagnostics

import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.handy.app.design.HandyDesignTheme
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditReviewActivityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun audit_row_can_disable_package_and_emit_feedback_intent() {
        val event = AuditEvent(
            timestampEpochMs = 1_700_000_000_000L,
            requestId = "request-review-test",
            provider = "tap-for-me",
            action = AuditAction.Tap,
            targetApp = "com.example.target",
            semanticTarget = "role=Button;text=Continue;expectedPackage=com.example.target",
            confirmationRequired = true,
            userConfirmed = true,
            result = AuditResult.Dispatched(component = "tap-for-me"),
        )
        val disabledPackages = mutableStateOf(emptySet<String>())
        val reported = mutableStateOf<AuditEvent?>(null)

        compose.setContent {
            HandyDesignTheme {
                AuditReviewScreen(
                    state = AuditReviewUiState(
                        events = listOf(event),
                        disabledPackages = disabledPackages.value,
                    ),
                    onDisablePackage = { packageName ->
                        disabledPackages.value = disabledPackages.value + packageName
                    },
                    onReportWrongTap = { reported.value = it },
                )
            }
        }

        compose.onNodeWithText("Activity").assertExists()
        compose.onNodeWithText("1 event").assertExists()
        compose.onAllNodesWithText("com.example.target", substring = true)
            .assertCountEquals(1)
        compose.onNodeWithText("DISABLE IN THIS APP").performClick()
        compose.onNodeWithText("DISABLED HERE").assertExists()

        compose.onNodeWithText("REPORT WRONG ACTION").performClick()
        compose.runOnIdle {
            assertThat(reported.value).isEqualTo(event)
        }

        val intent = createWrongTapFeedbackIntent(event)
        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("text/plain")
        assertThat(intent.getStringExtra(Intent.EXTRA_SUBJECT)).isEqualTo("Handy wrong action report")
        assertThat(intent.getStringExtra(Intent.EXTRA_TEXT)).contains("request-review-test")
        assertThat(intent.getStringExtra(Intent.EXTRA_TEXT)).contains("com.example.target")
    }

    @Test
    fun unknown_package_cannot_emit_disable_callback() {
        val event = AuditEvent(
            timestampEpochMs = System.currentTimeMillis(),
            requestId = "request-unknown-package",
            provider = "tap-for-me",
            action = AuditAction.Tap,
            targetApp = "unknown",
            semanticTarget = "point(100,200)",
            confirmationRequired = false,
            userConfirmed = false,
            result = AuditResult.NotFound,
        )
        var disableCalls = 0

        compose.setContent {
            HandyDesignTheme {
                AuditReviewScreen(
                    state = AuditReviewUiState(events = listOf(event)),
                    onDisablePackage = { disableCalls++ },
                )
            }
        }

        compose.onNodeWithText("DISABLE IN THIS APP")
            .assertHasNoClickAction()
        compose.runOnIdle {
            assertThat(disableCalls).isEqualTo(0)
        }
    }
}
