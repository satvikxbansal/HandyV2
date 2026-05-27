package com.handy.app.diagnostics

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
class DiagnosticsActivityRedactionScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun audit_rows_render_redacted_tokens_only() {
        compose.setContent {
            HandyDesignTheme {
                DiagnosticsScreen(
                    DiagnosticsUi(
                        auditTail = listOf(
                            AuditEvent(
                                timestampEpochMs = 1L,
                                requestId = "request-1",
                                provider = "test",
                                action = AuditAction.Tap,
                                targetApp = "com.example.login",
                                semanticTarget = "role=EditText;text=hunter2;viewId=login_satvik@example.com;desc=Password",
                                confirmationRequired = false,
                                userConfirmed = false,
                                result = AuditResult.NotFound,
                            ),
                        ),
                    ),
                )
            }
        }

        val screenshot = compose.onRoot().captureToImage()
        assertThat(screenshot.width).isGreaterThan(0)
        assertThat(screenshot.height).isGreaterThan(0)
        compose.onNodeWithText("RECENT ACTIONS").assertExists()
        compose.onNodeWithText("REVIEW").assertExists()
        compose.onNodeWithText("hunter2", substring = true).assertDoesNotExist()
        compose.onNodeWithText("satvik@example.com", substring = true).assertDoesNotExist()
    }
}
