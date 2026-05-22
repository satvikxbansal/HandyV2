package com.handy.app

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.app.accessibility.HandyAccessibilityService
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoexistenceSmokeTests {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun talkBackStateListenerWiring_dispatchesCurrentAccessibilityStateWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as AccessibilityManager
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val monitor = AccessibilityStateMonitor(context, scope)
        val listener = stateListenerFrom(monitor)

        try {
            listener.onAccessibilityStateChanged(manager.isEnabled)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            monitor.refreshBlocking()

            assertThat(monitor.isEnabled.value)
                .isEqualTo(isHandyAccessibilityServiceEnabled(context, manager))
            assertThat(monitor.connection.value).isNotNull()
        } finally {
            manager.removeAccessibilityStateChangeListener(listener)
            scope.cancel()
        }
    }

    @Test
    fun rtlLayoutSmoke_rendersPrimaryCoexistenceSurface() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HandyTheme(darkTheme = true) {
                    CoexistenceSmokeSurface()
                }
            }
        }

        compose.onNodeWithTag(ROOT_TAG).assertIsDisplayed()
        compose.onNodeWithText("Accessibility").assertIsDisplayed()
        compose.onNodeWithTag(PRIMARY_ACTION_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun largeFontSmoke_keepsCoreActionsReachable() {
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = 1.8f,
                ),
            ) {
                HandyTheme(darkTheme = true) {
                    CoexistenceSmokeSurface()
                }
            }
        }

        compose.onNodeWithTag(ROOT_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PRIMARY_ACTION_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        compose.onNodeWithTag(FOOTER_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun stateListenerFrom(
        monitor: AccessibilityStateMonitor,
    ): AccessibilityManager.AccessibilityStateChangeListener {
        val field = AccessibilityStateMonitor::class.java.getDeclaredField("stateListener")
        field.isAccessible = true
        return field.get(monitor) as AccessibilityManager.AccessibilityStateChangeListener
    }

    private fun isHandyAccessibilityServiceEnabled(
        context: Context,
        manager: AccessibilityManager,
    ): Boolean {
        if (!manager.isEnabled) return false
        val expected = "${context.packageName}/${HandyAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @Composable
    private fun CoexistenceSmokeSurface() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = HandyColors.PageBg,
        ) {
            Column(
                modifier = Modifier
                    .testTag(ROOT_TAG)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Handy",
                    style = HandyType.TitleLarge,
                    color = HandyColors.TextPrimary,
                )
                Text(
                    text = "Coexistence beta smoke",
                    style = HandyType.Body,
                    color = HandyColors.TextSecondary,
                )
                CoexistenceRow(
                    title = "Accessibility",
                    body = "TalkBack and Switch Access can reach each action.",
                )
                CoexistenceRow(
                    title = "Reading order",
                    body = "Rows mirror under RTL without hiding state.",
                )
                CoexistenceRow(
                    title = "Display scale",
                    body = "Large text keeps the primary action reachable.",
                )
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = {},
                    modifier = Modifier
                        .testTag(PRIMARY_ACTION_TAG)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Ask Handy")
                }
                Text(
                    text = "Manual table required on Pixel and OEM before beta.",
                    modifier = Modifier.testTag(FOOTER_TAG),
                    style = HandyType.CaptionSmall,
                    color = HandyColors.TextMuted,
                )
            }
        }
    }

    @Composable
    private fun CoexistenceRow(
        title: String,
        body: String,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(HandyColors.ChipBg)
                .border(0.5.dp, HandyColors.ChipBorder, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(HandyColors.Accent),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = HandyType.BodyStrong,
                    color = HandyColors.TextPrimary,
                )
                Text(
                    text = body,
                    style = HandyType.CaptionSmall,
                    color = HandyColors.TextSecondary,
                )
            }
        }
    }

    private companion object {
        const val ROOT_TAG = "coexistence-root"
        const val PRIMARY_ACTION_TAG = "coexistence-primary-action"
        const val FOOTER_TAG = "coexistence-footer"
    }
}
