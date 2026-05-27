package com.handy.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric.CaptureInfo
import androidx.benchmark.macro.Metric.Measurement
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class FlightJankBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun longPressAssistantPointFlow_jankUnderFivePercent() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                FrameTimelineJankPercentMetric(maxJankPercent = 5.0),
            ),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                allowBenchmarkPermissions()
                startActivityAndWait()
                startOverlayService()
                waitForBuddy()
            },
        ) {
            val buddy = waitForBuddy()
            buddy.longPress(device)
            device.wait(Until.hasObject(By.descContains("Handy is listening")), SHORT_WAIT_MS)
            triggerBenchmarkPoint()
            device.wait(Until.hasObject(By.descContains("Handy is pointing at a control")), FLOW_WAIT_MS)
        }
    }

    private fun MacrobenchmarkScope.allowBenchmarkPermissions() {
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.RECORD_AUDIO")
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("appops set $TARGET_PACKAGE SYSTEM_ALERT_WINDOW allow")
    }

    private fun MacrobenchmarkScope.startOverlayService() {
        device.executeShellCommand(
            "am startservice -n $TARGET_PACKAGE/com.handy.app.overlay.FloatingWidgetOverlayService",
        )
    }

    private fun MacrobenchmarkScope.triggerBenchmarkPoint() {
        val x = (device.displayWidth * 0.72f).toInt()
        val y = (device.displayHeight * 0.42f).toInt()
        device.executeShellCommand(
            "am broadcast -a $ACTION_BENCHMARK_POINT --ei x $x --ei y $y --es label Benchmark",
        )
    }

    private fun MacrobenchmarkScope.waitForBuddy(): UiObject2 =
        device.waitForBuddy()

    private fun UiDevice.waitForBuddy(): UiObject2 =
        wait(Until.findObject(By.descContains("Handy is")), FLOW_WAIT_MS)
            ?: throw AssertionError("Handy widget was not visible for flight benchmark")

    private fun UiObject2.longPress(device: UiDevice) {
        val bounds = visibleBounds
        val x = bounds.centerX()
        val y = bounds.centerY()
        device.executeShellCommand("input swipe $x $y $x $y $LONG_PRESS_MS")
    }

    private class FrameTimelineJankPercentMetric(
        private val maxJankPercent: Double,
    ) : TraceMetric() {
        override fun getMeasurements(
            captureInfo: CaptureInfo,
            traceSession: TraceProcessor.Session,
        ): List<Measurement> {
            val row = traceSession.query(
                """
                SELECT
                    COUNT(1) AS total,
                    SUM(
                        CASE
                            WHEN jank_type IS NOT NULL AND jank_type != 'None' THEN 1
                            ELSE 0
                        END
                    ) AS janky
                FROM actual_frame_timeline_slice
                LEFT JOIN process USING(upid)
                WHERE process.name = '${captureInfo.targetPackageName}'
                """.trimIndent(),
            ).firstOrNull()
            val total = row?.long("total") ?: 0L
            if (total == 0L) {
                throw AssertionError("No frame timeline data captured for ${captureInfo.targetPackageName}")
            }
            val janky = row?.long("janky") ?: 0L
            val jankPercent = janky * 100.0 / total
            assertThat(jankPercent).isLessThan(maxJankPercent)
            return listOf(
                Measurement("buddyFlightJankPercent", jankPercent),
                Measurement("buddyFlightFrameCount", total.toDouble()),
            )
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.handy.android"
        const val ACTION_BENCHMARK_POINT = "com.handy.android.debug.FLIGHT_BENCHMARK_POINT"
        const val SHORT_WAIT_MS = 2_000L
        const val FLOW_WAIT_MS = 10_000L
        const val LONG_PRESS_MS = 700L
    }
}
