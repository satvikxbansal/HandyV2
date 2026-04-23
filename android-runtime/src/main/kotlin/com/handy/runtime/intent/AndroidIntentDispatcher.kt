package com.handy.runtime.intent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import com.handy.core.action.AssistantAction
import com.handy.core.intent.IntentResult
import timber.log.Timber

/**
 * Maps [AssistantAction] → native Android `Intent`.
 *
 * Rules (guardrails → "Intent dispatch contract"):
 *  - Only fires from `dispatch_action` tool calls (enforced at the caller).
 *  - Returns a structured [IntentResult] — never a raw boolean.
 *  - Destructive actions (call / message / share) return
 *    [IntentResult.NeedsConfirmation]; the UI owns the confirmation sheet.
 *  - Non-destructive actions dispatch directly.
 */
class AndroidIntentDispatcher(
    private val context: Context,
    private val launchableApps: LaunchableAppIndex,
) {

    fun dispatch(action: AssistantAction): IntentResult = when (action) {
        is AssistantAction.StartTimer -> fireTimer(action)
        is AssistantAction.SetAlarm -> fireAlarm(action)
        is AssistantAction.OpenUrl -> fireViewUrl(action.url)
        is AssistantAction.OpenApp -> fireLaunchApp(action.packageHint)
        is AssistantAction.DialNumber -> IntentResult.NeedsConfirmation(
            reason = "Dial ${action.number}?",
        )
        is AssistantAction.MapsSearch -> fireMaps(action.query)
        is AssistantAction.ComposeEmail -> IntentResult.NeedsConfirmation(
            reason = "Open email draft${action.to?.let { " to $it" }.orEmpty()}?",
        )
        is AssistantAction.ShareText -> IntentResult.NeedsConfirmation(
            reason = "Share \"${action.text.take(40)}…\"?",
        )
        is AssistantAction.WebSearchIntent -> fireWebSearch(action.query)
    }

    /**
     * Phase 3's confirmation UI calls this after the user taps
     * "Continue" on the NeedsConfirmation sheet.
     */
    fun dispatchConfirmed(action: AssistantAction): IntentResult = when (action) {
        is AssistantAction.DialNumber -> fireDial(action.number)
        is AssistantAction.ComposeEmail -> fireEmail(action)
        is AssistantAction.ShareText -> fireShare(action.text)
        else -> dispatch(action) // non-destructive: no confirmation needed
    }

    // ---------- destructive — fired after confirmation ----------

    private fun fireDial(number: String): IntentResult {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "ACTION_DIAL $number" }
    }

    private fun fireEmail(action: AssistantAction.ComposeEmail): IntentResult {
        val uri = Uri.Builder()
            .scheme("mailto")
            .apply {
                if (action.to != null) opaquePart(action.to) else opaquePart("")
            }
            .build()
        val intent = Intent(Intent.ACTION_SENDTO, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        action.subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        action.body?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        return start(intent) { "ACTION_SENDTO mailto" }
    }

    private fun fireShare(text: String): IntentResult {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            IntentResult.ChooserShown
        }.getOrElse { IntentResult.Failed(it.message ?: "share failed") }
    }

    // ---------- non-destructive ----------

    private fun fireTimer(action: AssistantAction.StartTimer): IntentResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, action.seconds.coerceAtLeast(1))
            action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent) { "SET_TIMER ${action.seconds}s" }
    }

    private fun fireAlarm(action: AssistantAction.SetAlarm): IntentResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute.coerceIn(0, 59))
            action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent) { "SET_ALARM ${action.hour}:${action.minute}" }
    }

    private fun fireViewUrl(url: String): IntentResult {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return IntentResult.Failed("invalid url")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "ACTION_VIEW $url" }
    }

    private fun fireLaunchApp(packageHint: String): IntentResult {
        val resolved = launchableApps.resolve(packageHint)
            ?: return IntentResult.NoHandler
        val launch = context.packageManager.getLaunchIntentForPackage(resolved.packageName)
            ?: return IntentResult.NoHandler
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launch)
            IntentResult.Dispatched(
                component = ComponentName(resolved.packageName, resolved.activityComponentFlat.substringAfter('/')).flattenToString(),
            )
        }.getOrElse { IntentResult.Failed(it.message ?: "launch failed") }
    }

    private fun fireMaps(query: String): IntentResult {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "geo?q=$query" }
    }

    private fun fireWebSearch(query: String): IntentResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent) { "ACTION_WEB_SEARCH $query" }
    }

    private inline fun start(intent: Intent, label: () -> String): IntentResult {
        if (intent.resolveActivity(context.packageManager) == null) return IntentResult.NoHandler
        return runCatching {
            context.startActivity(intent)
            Timber.d("AndroidIntentDispatcher: dispatched %s", label())
            IntentResult.Dispatched(component = intent.component?.flattenToString())
        }.getOrElse {
            Timber.w(it, "AndroidIntentDispatcher: failed %s", label())
            IntentResult.Failed(it.message ?: "dispatch failed")
        }
    }
}

/** Uri.Builder.opaquePart is deprecated on newer SDKs; small shim for clarity. */
private fun Uri.Builder.opaquePart(value: String): Uri.Builder = encodedOpaquePart(Uri.encode(value))
