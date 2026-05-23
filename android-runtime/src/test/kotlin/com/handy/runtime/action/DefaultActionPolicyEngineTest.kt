package com.handy.runtime.action

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ActionAppPolicy
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.SettingsTarget
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.model.HandySettings
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.PrivacyFlags
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import org.junit.Test

class DefaultActionPolicyEngineTest {

    @Test fun `denylisted banking payments or password package is blocked`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.google.android.apps.nbu.paisa.user"),
            target = null,
            grounding = grounding(packageName = "com.google.android.apps.nbu.paisa.user"),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("denylisted")
        assertThat(decision.risk).isEqualTo(ActionRisk.CRITICAL)
    }

    @Test fun `secure window is blocked`() {
        val decision = engine().decide(
            action = AssistantAction.OpenUrl("https://example.com"),
            target = null,
            grounding = grounding(capture = CaptureResult.SecureWindow),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("secure")
    }

    @Test fun `chrome incognito actions are blocked by default setting`() {
        val decision = engine().decide(
            action = AssistantAction.OpenUrl("https://example.com"),
            target = null,
            grounding = grounding(
                packageName = "com.android.chrome",
                marks = listOf(mark("Incognito tab", intArrayOf(0, 0, 100, 40))),
            ),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("incognito-actions-disabled")
    }

    @Test fun `chrome incognito action block can be disabled by setting`() {
        val decision = engine(
            settings = openSettings.copy(noActionsInIncognito = false),
        ).decide(
            action = AssistantAction.OpenUrl("https://example.com"),
            target = null,
            grounding = grounding(
                packageName = "com.android.chrome",
                marks = listOf(mark("Incognito tab", intArrayOf(0, 0, 100, 40))),
            ),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(decision.allowed).isTrue()
    }

    @Test fun `low resolver confidence is blocked`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.example.app"),
            target = node(text = "Continue", resolverConfidence = 0.69f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("low-confidence")
    }

    @Test fun `middle resolver confidence below normal action threshold is blocked`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.example.app"),
            target = node(text = "Continue", resolverConfidence = 0.89f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("low-confidence")
    }

    @Test fun `duplicate target in grounding is blocked as ambiguous`() {
        val marks = listOf(
            mark(text = "Continue", bounds = intArrayOf(0, 0, 100, 40)),
            mark(text = "Continue", bounds = intArrayOf(0, 60, 100, 100)),
        )
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.example.app"),
            target = node(markId = null, text = "Continue", resolverConfidence = 0.95f),
            grounding = grounding(marks = marks),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("ambiguous")
    }

    @Test fun `untrusted tool source is suggestion only and cannot act`() {
        val decision = engine().decide(
            action = AssistantAction.StartTimer(seconds = 60),
            target = null,
            grounding = grounding(),
            sourceTrust = SourceTrust.UNTRUSTED_TOOL,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("tool-suggestion-only")
    }

    @Test fun `send call and navigation start require strong hold with fresh snapshot`() {
        val actions = listOf(
            AssistantAction.ComposeSms(to = "5551234", body = "on my way"),
            AssistantAction.DialNumber("5551234"),
            AssistantAction.StartNavigation("airport"),
        )

        actions.forEach { action ->
            val decision = engine().decide(
                action = action,
                target = null,
                grounding = grounding(),
                sourceTrust = SourceTrust.TRUSTED_USER,
            )

            assertThat(decision.allowed).isTrue()
            assertThat(decision.confirmation).isEqualTo(ConfirmationLevel.STRONG_HOLD)
            assertThat(decision.requireFreshSnapshot).isTrue()
        }
    }

    @Test fun `payment urls and personal-data submit are blocked in beta`() {
        val decisions = listOf(
            engine().decide(
                action = AssistantAction.OpenUrl("upi://pay?pa=merchant@example&am=100"),
                target = null,
                grounding = grounding(),
                sourceTrust = SourceTrust.TRUSTED_USER,
            ),
            engine().decide(
                action = AssistantAction.OpenUrl("https://shop.example/complete-purchase"),
                target = null,
                grounding = grounding(),
                sourceTrust = SourceTrust.TRUSTED_RECIPE,
            ),
            engine().decide(
                action = AssistantAction.OpenApp("com.example.app"),
                target = node(text = "Submit address", resolverConfidence = 0.95f),
                grounding = grounding(),
                sourceTrust = SourceTrust.TRUSTED_RECIPE,
            ),
        )

        decisions.forEach { decision ->
            assertThat(decision.allowed).isFalse()
            assertThat(decision.reason).isEqualTo("beta-blocked")
        }
    }

    @Test fun `gmail recipe delete email is allowed for trusted recipe`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.google.android.gm"),
            target = node(
                text = "Delete email",
                expectedPackage = "com.google.android.gm",
                resolverConfidence = 0.95f,
            ),
            grounding = grounding(packageName = "com.google.android.gm"),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.confirmation).isEqualTo(ConfirmationLevel.NORMAL)
    }

    @Test fun `delete account target is blocked regardless of source trust`() {
        val decisions = listOf(
            SourceTrust.TRUSTED_RECIPE,
            SourceTrust.TRUSTED_USER,
            SourceTrust.UNTRUSTED_TOOL,
        ).associateWith { sourceTrust ->
            engine().decide(
                action = AssistantAction.OpenApp("com.example.app"),
                target = node(text = "Delete account", resolverConfidence = 0.95f),
                grounding = grounding(),
                sourceTrust = sourceTrust,
            )
        }

        decisions.values.forEach { decision ->
            assertThat(decision.allowed).isFalse()
        }
        assertThat(decisions[SourceTrust.TRUSTED_RECIPE]?.reason).isEqualTo("beta-blocked")
        assertThat(decisions[SourceTrust.TRUSTED_USER]?.reason).isEqualTo("beta-blocked")
    }

    @Test fun `shopping buy now target is blocked in beta`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.shopping.example"),
            target = node(
                text = "Buy now",
                expectedPackage = "com.shopping.example",
                resolverConfidence = 0.95f,
            ),
            grounding = grounding(packageName = "com.shopping.example"),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("beta-blocked")
    }

    @Test fun `harmless native intent text is not blocked by commerce words`() {
        val decision = engine().decide(
            action = AssistantAction.StartTimer(seconds = 600, label = "buy milk"),
            target = null,
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.confirmation).isEqualTo(ConfirmationLevel.NONE)
    }

    @Test fun `high risk settings deep links are blocked`() {
        val blockedTargets = listOf(
            SettingsTarget.WIFI,
            SettingsTarget.BLUETOOTH,
            SettingsTarget.ACCESSIBILITY,
            SettingsTarget.SECURITY,
            SettingsTarget.BIOMETRIC,
        )

        blockedTargets.forEach { target ->
            val decision = engine().decide(
                action = AssistantAction.OpenSettings(target),
                target = null,
                grounding = grounding(packageName = "com.android.settings"),
                sourceTrust = SourceTrust.TRUSTED_USER,
            )

            assertThat(decision.allowed).isFalse()
            assertThat(decision.reason).isEqualTo("settings-too-sensitive")
            assertThat(decision.risk).isEqualTo(ActionRisk.HIGH)
        }
    }

    @Test fun `safe settings recipe deep links are allowed`() {
        val allowedTargets = listOf(
            SettingsTarget.RINGTONE,
            SettingsTarget.DND,
            SettingsTarget.BRIGHTNESS,
            SettingsTarget.SCREEN_TIMEOUT,
        )

        allowedTargets.forEach { target ->
            val decision = engine().decide(
                action = AssistantAction.OpenSettings(target),
                target = null,
                grounding = grounding(packageName = "com.android.settings"),
                sourceTrust = SourceTrust.TRUSTED_USER,
            )

            assertThat(decision.allowed).isTrue()
            assertThat(decision.confirmation).isEqualTo(ConfirmationLevel.NONE)
            assertThat(decision.risk).isEqualTo(ActionRisk.LOW)
        }
    }

    @Test fun `install app opens Play Store with normal confirmation`() {
        val decision = engine().decide(
            action = AssistantAction.InstallApp(packageHint = "com.spotify.music"),
            target = null,
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.confirmation).isEqualTo(ConfirmationLevel.NORMAL)
        assertThat(decision.risk).isEqualTo(ActionRisk.MEDIUM)
    }

    @Test fun `play store urls are not blocked by payment words in package names`() {
        val decision = engine().decide(
            action = AssistantAction.OpenUrl(
                "https://play.google.com/store/apps/details?id=com.example.payments",
            ),
            target = null,
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_USER,
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.confirmation).isEqualTo(ConfirmationLevel.NONE)
    }

    @Test fun `otp password and card fields are blocked`() {
        val decisions = listOf(
            engine().decide(
                action = AssistantAction.OpenApp("com.example.app"),
                target = node(desc = "OTP code", resolverConfidence = 0.95f),
                grounding = grounding(),
                sourceTrust = SourceTrust.TRUSTED_RECIPE,
            ),
            engine().decide(
                action = AssistantAction.ComposeSms(body = "my card number is 4111 1111 1111 1111"),
                target = null,
                grounding = grounding(),
                sourceTrust = SourceTrust.TRUSTED_USER,
            ),
        )

        decisions.forEach { decision ->
            assertThat(decision.allowed).isFalse()
            assertThat(decision.reason).isEqualTo("sensitive-field")
        }
    }

    @Test fun `type text into nearby sensitive field is blocked`() {
        val marks = listOf(
            mark("OTP", bounds = intArrayOf(0, 0, 120, 40), markId = "m1"),
            mark("", bounds = intArrayOf(0, 48, 220, 100), markId = "m2", role = "EditText"),
        )
        val decision = engine().decide(
            action = AssistantAction.TypeText("123456"),
            target = node(markId = "m2", text = null, resolverConfidence = 0.95f),
            grounding = grounding(marks = marks),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("sensitive-field")
    }

    @Test fun `type text with card pattern is blocked before dispatch`() {
        val decision = engine().decide(
            action = AssistantAction.TypeText("4111 1111 1111 1111"),
            target = node(markId = "m1", text = "Search", resolverConfidence = 0.95f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("sensitive-field")
    }

    @Test fun `type text with otp pattern is blocked in verification context`() {
        val decision = engine().decide(
            action = AssistantAction.TypeText("123456"),
            target = node(markId = "m1", desc = "Verification code", resolverConfidence = 0.95f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("sensitive-field")
    }

    @Test fun `type text with pure otp shaped code is blocked even without field context`() {
        val decision = engine().decide(
            action = AssistantAction.TypeText("123456"),
            target = node(markId = "m1", text = "Search", resolverConfidence = 0.95f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("sensitive-field")
    }

    @Test fun `app changed after grounding is blocked`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.example.old"),
            target = node(expectedPackage = "com.example.old", resolverConfidence = 0.95f),
            grounding = grounding(packageName = "com.example.new"),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("screen-changed")
    }

    @Test fun `tree changed after grounding is blocked`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.example.app"),
            target = node(resolverConfidence = 0.95f, treeHash = "old-tree"),
            grounding = grounding(treeHash = "new-tree"),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("screen-changed")
    }

    @Test fun `normal visible button requires node action for unknown app`() {
        val decision = engine().decide(
            action = AssistantAction.OpenApp("com.example.app"),
            target = node(text = "Continue", resolverConfidence = 0.95f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.confirmation).isEqualTo(ConfirmationLevel.NORMAL)
        assertThat(decision.requireNodeActionOnly).isTrue()
        assertThat(decision.allowGestureFallback).isFalse()
    }

    @Test fun `normal visible button allows gesture fallback only for learned app`() {
        val decision = engine(learned = { it == "com.example.app" }).decide(
            action = AssistantAction.OpenApp("com.example.app"),
            target = node(text = "Continue", resolverConfidence = 0.95f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isTrue()
        assertThat(decision.requireNodeActionOnly).isFalse()
        assertThat(decision.allowGestureFallback).isTrue()
    }

    @Test fun `tap-for-me mute blocks ui actions`() {
        val decision = engine(
            settings = openSettings.copy(tapForMeMutedUntilEpochMs = 2_000L),
            now = 1_000L,
        ).decide(
            action = AssistantAction.OpenApp("com.example.app"),
            target = node(text = "Continue", resolverConfidence = 0.95f),
            grounding = grounding(),
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
        )

        assertThat(decision.allowed).isFalse()
        assertThat(decision.reason).isEqualTo("muted")
    }

    private fun engine(
        settings: HandySettings = openSettings,
        userDenylist: Set<String> = emptySet(),
        learned: (String?) -> Boolean = { false },
        now: Long = 1_000L,
    ): DefaultActionPolicyEngine =
        DefaultActionPolicyEngine(
            appPolicy = ActionAppPolicy { userDenylist },
            settingsProvider = { settings },
            learnedAllowlistProvider = learned,
            clock = { now },
        )

    private fun grounding(
        packageName: String = "com.example.app",
        marks: List<AccessibilityMark> = emptyList(),
        capture: CaptureResult? = null,
        privacyFlags: PrivacyFlags = PrivacyFlags(),
        treeHash: String? = null,
    ): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = packageName, appLabel = packageName),
            panelSnapshot = marks.takeIf { it.isNotEmpty() }?.let {
                PanelSnapshot(
                    toolContext = ToolContext(packageName = packageName, appLabel = packageName),
                    capturedAtEpochMs = 1_000L,
                    marks = it,
                )
            },
            capture = capture,
            windowId = WINDOW_ID,
            rootBoundsHash = ROOT_HASH,
            treeHash = treeHash,
            capturedAtMs = 1_000L,
            privacyFlags = privacyFlags,
        )

    private fun node(
        markId: String? = "m1",
        text: String? = null,
        desc: String? = null,
        expectedPackage: String = "com.example.app",
        resolverConfidence: Float? = null,
        treeHash: String? = null,
    ): TapTarget.AtNode =
        TapTarget.AtNode(
            markId = markId,
            role = "Button",
            text = text,
            viewId = null,
            desc = desc,
            expectedPackage = expectedPackage,
            expectedWindowId = WINDOW_ID,
            snapshotHash = ROOT_HASH,
            resolverConfidence = resolverConfidence,
            treeHash = treeHash,
        )

    private fun mark(
        text: String,
        bounds: IntArray,
        markId: String? = null,
        role: String = "Button",
    ): AccessibilityMark =
        AccessibilityMark(
            markId = markId,
            role = role,
            text = text,
            bounds = bounds,
            clickable = true,
        )

    private companion object {
        const val WINDOW_ID = 7
        const val ROOT_HASH = "root"
        val openSettings = HandySettings(
            tapForMeEnabled = true,
            actionDisclosureVersionAccepted = ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION,
        )
    }
}
