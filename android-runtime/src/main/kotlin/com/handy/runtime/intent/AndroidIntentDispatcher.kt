package com.handy.runtime.intent

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.handy.core.action.AssistantAction
import com.handy.core.action.SettingsTarget
import com.handy.core.intent.IntentResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
        is AssistantAction.InstallApp -> fireInstallApp(action)
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
        // V2 additions
        is AssistantAction.ComposeSms -> IntentResult.NeedsConfirmation(
            reason = "Open SMS draft${action.to?.let { " to $it" }.orEmpty()}?",
        )
        is AssistantAction.CreateCalendarEvent -> fireCreateEvent(action)
        is AssistantAction.OpenSettings -> fireOpenSettings(action.target)
        is AssistantAction.OpenAppInfo -> fireOpenAppInfo(action.packageHint)
        is AssistantAction.StartNavigation -> fireNavigation(action.query)
        is AssistantAction.ShareUrl -> IntentResult.NeedsConfirmation(
            reason = "Share URL${action.title?.let { " \"$it\"" }.orEmpty()}?",
        )
        is AssistantAction.TypeText -> IntentResult.Failed(
            "type_text is handled by ActionPerformer, not dispatch_action",
        )
    }

    /**
     * Phase 3's confirmation UI calls this after the user taps
     * "Continue" on the NeedsConfirmation sheet.
     */
    fun dispatchConfirmed(action: AssistantAction): IntentResult = when (action) {
        is AssistantAction.DialNumber -> fireDial(action.number)
        is AssistantAction.ComposeEmail -> fireEmail(action)
        is AssistantAction.ShareText -> fireShare(action.text)
        is AssistantAction.ComposeSms -> fireSms(action)
        is AssistantAction.ShareUrl -> fireShareUrl(action)
        else -> dispatch(action) // non-destructive: no confirmation needed
    }

    // ---------- V2 destructive — fired after confirmation ----------

    private fun fireSms(action: AssistantAction.ComposeSms): IntentResult {
        val uri = Uri.parse("smsto:${action.to.orEmpty()}")
        val intent = Intent(Intent.ACTION_SENDTO, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        action.body?.let { intent.putExtra("sms_body", it) }
        return start(intent) { "sms toChars=${action.to?.length ?: 0}" }
    }

    private fun fireShareUrl(action: AssistantAction.ShareUrl): IntentResult {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, action.url)
            action.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        val chooser = Intent.createChooser(send, action.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            IntentResult.ChooserShown
        }.getOrElse { IntentResult.Failed(it.message ?: "share URL failed") }
    }

    // ---------- V2 non-destructive ----------

    private fun fireCreateEvent(action: AssistantAction.CreateCalendarEvent): IntentResult {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, action.title)
            action.startEpochMs?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            action.endEpochMs?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            action.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            action.notes?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent) { "calendar event titleChars=${action.title.length}" }
    }

    private fun fireOpenSettings(target: SettingsTarget): IntentResult {
        val action = when (target) {
            SettingsTarget.APP_INFO -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            SettingsTarget.ACCESSIBILITY -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            SettingsTarget.NOTIFICATIONS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            SettingsTarget.BATTERY_OPTIMIZATION -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            SettingsTarget.DARK_MODE -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsTarget.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsTarget.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsTarget.SECURITY -> Settings.ACTION_SECURITY_SETTINGS
            SettingsTarget.BIOMETRIC -> Settings.ACTION_BIOMETRIC_ENROLL
            SettingsTarget.APPS -> Settings.ACTION_APPLICATION_SETTINGS
            SettingsTarget.RINGTONE -> Settings.ACTION_SOUND_SETTINGS
            SettingsTarget.DND -> ACTION_ZEN_MODE_SETTINGS
            SettingsTarget.BRIGHTNESS -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsTarget.SCREEN_TIMEOUT -> Settings.ACTION_DISPLAY_SETTINGS
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (target == SettingsTarget.APP_INFO) {
            intent.data = Uri.parse("package:${context.packageName}")
        }
        return start(intent) { "settings:$target" }
    }

    private fun fireOpenAppInfo(packageHint: String): IntentResult {
        val resolved = launchableApps.resolve(packageHint) ?: return IntentResult.NoHandler
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${resolved.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "app_info ${resolved.packageName}" }
    }

    private fun fireNavigation(query: String): IntentResult {
        // Google Maps turn-by-turn: `google.navigation:q=…` is the
        // documented scheme; other maps apps pattern-match on it.
        val uri = Uri.parse("google.navigation:q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "navigation queryChars=${query.length}" }
    }

    // ---------- destructive — fired after confirmation ----------

    private fun fireDial(number: String): IntentResult {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "ACTION_DIAL numberChars=${number.length}" }
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
        return start(intent) { "ACTION_VIEW host=${uri.host.orEmpty()}" }
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

    private fun fireInstallApp(action: AssistantAction.InstallApp): IntentResult {
        val target = installAppIntentTarget(action)
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target.marketUri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(marketIntent)
            Timber.d("AndroidIntentDispatcher: dispatched Play Store market URI")
            IntentResult.Dispatched()
        } catch (notFound: ActivityNotFoundException) {
            Timber.d(notFound, "AndroidIntentDispatcher: market URI unavailable, falling back to HTTPS")
            fireInstallAppFallback(target)
        } catch (t: Throwable) {
            Timber.w(t, "AndroidIntentDispatcher: failed Play Store market URI")
            IntentResult.Failed(t.message ?: "install app dispatch failed")
        }
    }

    private fun fireInstallAppFallback(target: InstallAppIntentTarget): IntentResult {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(target.httpsUrl))
        val chooser = Intent.createChooser(view, "Open Play Store")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            IntentResult.ChooserShown
        }.getOrElse {
            Timber.w(it, "AndroidIntentDispatcher: failed Play Store HTTPS fallback")
            IntentResult.Failed(it.message ?: "install app fallback failed")
        }
    }

    private fun fireMaps(query: String): IntentResult {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "geo queryChars=${query.length}" }
    }

    private fun fireWebSearch(query: String): IntentResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent) { "ACTION_WEB_SEARCH queryChars=${query.length}" }
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

internal data class InstallAppIntentTarget(
    val marketUri: String,
    val httpsUrl: String,
)

internal fun installAppIntentTarget(action: AssistantAction.InstallApp): InstallAppIntentTarget {
    val packageHint = action.packageHint?.trim()?.takeIf { it.isNotBlank() }
    val searchQuery = action.searchQuery?.trim()?.takeIf { it.isNotBlank() }
    return if (packageHint != null) {
        val encodedPackage = packageHint.encodeUriComponent()
        InstallAppIntentTarget(
            marketUri = "market://details?id=$encodedPackage",
            httpsUrl = "https://play.google.com/store/apps/details?id=$encodedPackage",
        )
    } else {
        val encodedQuery = requireNotNull(searchQuery).encodeUriComponent()
        InstallAppIntentTarget(
            marketUri = "market://search?q=$encodedQuery&c=apps",
            httpsUrl = "https://play.google.com/store/search?q=$encodedQuery&c=apps",
        )
    }
}

private fun String.encodeUriComponent(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

private const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
