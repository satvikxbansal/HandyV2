package com.handy.app.diagnostics

import com.google.common.truth.Truth.assertThat
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import java.util.Calendar
import org.junit.Test

class AuditReviewActivityHelpersTest {

    @Test
    fun dayBucket_mapsTodayYesterdayAndOlder() {
        assertThat(invokePrivateHelper("dayBucket", eventAt(Calendar.getInstance().timeInMillis)).toString())
            .isEqualTo("TODAY")

        val yesterday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }
        assertThat(invokePrivateHelper("dayBucket", eventAt(yesterday.timeInMillis)).toString())
            .isEqualTo("YESTERDAY")

        val older = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -2)
        }
        assertThat(invokePrivateHelper("dayBucket", eventAt(older.timeInMillis)).toString())
            .isEqualTo("OLDER")
    }

    @Test
    fun resultTone_mapsResultLabels() {
        assertThat(resultToneLabel(AuditResult.Dispatched(component = "tap-for-me")))
            .isEqualTo("Done")
        assertThat(resultToneLabel(AuditResult.Cancelled))
            .isEqualTo("Cancelled")
        assertThat(resultToneLabel(AuditResult.Failed(reason = "View no longer visible")))
            .isEqualTo("Failed")
    }

    private fun resultToneLabel(result: AuditResult): String {
        val tone = invokePrivateHelper("resultTone", eventAt(Calendar.getInstance().timeInMillis, result))
        val field = tone::class.java.getDeclaredField("label")
        field.isAccessible = true
        return field.get(tone) as String
    }

    private fun eventAt(
        timestampEpochMs: Long,
        result: AuditResult = AuditResult.Dispatched(component = "tap-for-me"),
    ) = AuditEvent(
        timestampEpochMs = timestampEpochMs,
        requestId = "request-helper-test",
        provider = "tap-for-me",
        action = AuditAction.Tap,
        targetApp = "com.example.target",
        semanticTarget = "role=Button;text=Continue",
        confirmationRequired = false,
        userConfirmed = false,
        result = result,
    )

    private fun invokePrivateHelper(name: String, event: AuditEvent): Any {
        val method = Class.forName("com.handy.app.diagnostics.AuditReviewActivityKt")
            .declaredMethods
            .single { it.name == name && it.parameterTypes.contentEquals(arrayOf(AuditEvent::class.java)) }
        method.isAccessible = true
        return method.invoke(null, event)!!
    }
}
