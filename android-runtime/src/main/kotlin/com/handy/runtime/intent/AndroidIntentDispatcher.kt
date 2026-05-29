package com.handy.runtime.intent

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.handy.core.action.AssistantAction
import com.handy.core.action.FilePickerMode
import com.handy.core.action.SettingsTarget
import com.handy.core.intent.IntentResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import timber.log.Timber

/**
 * Maps [AssistantAction] → native Android `Intent`.
 *
 * Rules (guardrails → "Intent dispatch contract"):
 *  - Fires only from guarded tool calls or deterministic recipe runners
 *    (enforced at the caller).
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
        is AssistantAction.SearchInApp -> fireAppSearch(action)
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
        is AssistantAction.OpenContact -> fireOpenContact(action.contactUri)
        is AssistantAction.OpenFilePicker -> fireOpenFilePicker(action)
        AssistantAction.OpenPhotos -> fireOpenPhotos()
        AssistantAction.OpenCalculator -> fireOpenCalculator()
        is AssistantAction.StartNavigation -> fireNavigation(action.query)
        is AssistantAction.ShareUrl -> IntentResult.NeedsConfirmation(
            reason = "Share URL${action.title?.let { " \"$it\"" }.orEmpty()}?",
        )
        is AssistantAction.TypeText -> IntentResult.Failed(
            "type_text is handled by ActionPerformer, not dispatch_action",
        )
        is AssistantAction.UiAction -> IntentResult.Failed(
            "ui_action is handled by ActionPerformer, not dispatch_action",
        )
    }

    /**
     * Phase 3's confirmation UI calls this after the user taps
     * "Continue" on the NeedsConfirmation sheet.
     */
    fun dispatchConfirmed(action: AssistantAction): IntentResult = when (action) {
        is AssistantAction.DialNumber -> fireDial(action.number)
        is AssistantAction.ComposeEmail -> fireEmail(action)
        is AssistantAction.ShareText -> fireShare(action.text, action.mimeType)
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
            setDataAndType(
                CalendarContract.Events.CONTENT_URI,
                CALENDAR_EVENT_MIME_TYPE,
            )
            putExtra(CalendarContract.Events.TITLE, action.title)
            action.startEpochMs?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            action.endEpochMs?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            action.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            action.notes?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            action.attendees.takeIf { it.isNotEmpty() }?.let {
                putExtra(Intent.EXTRA_EMAIL, it.toTypedArray())
            }
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

    private fun fireShare(text: String, mimeType: String = "text/plain"): IntentResult {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "text/plain" }
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
        val intent = Intent(openUrlIntentActionForScheme(uri.scheme), uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = start(intent) { "${intent.action} host=${uri.host.orEmpty()}" }
        if (result == IntentResult.NoHandler) {
            url.foodDeliveryWebFallbackUrl()?.let { fallback ->
                Timber.d("AndroidIntentDispatcher: food deep link unavailable, falling back to HTTPS")
                return fireViewUrl(fallback)
            }
        }
        return result
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

    private fun fireAppSearch(action: AssistantAction.SearchInApp): IntentResult {
        val query = action.query.trim()
        if (query.isBlank()) return IntentResult.Failed("empty app search query")
        val resolved = launchableApps.resolve(action.packageHint)
            ?: return IntentResult.NoHandler
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(resolved.packageName)
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent) {
            "ACTION_SEARCH package=${resolved.packageName} queryChars=${query.length}"
        }
    }

    private fun fireWebSearch(query: String): IntentResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Timber.d("AndroidIntentDispatcher: ACTION_WEB_SEARCH unavailable, falling back to browser search")
            return fireWebSearchFallback(query)
        }
        return try {
            context.startActivity(intent)
            Timber.d("AndroidIntentDispatcher: dispatched ACTION_WEB_SEARCH queryChars=%d", query.length)
            IntentResult.Dispatched(component = intent.component?.flattenToString())
        } catch (notFound: ActivityNotFoundException) {
            Timber.d(notFound, "AndroidIntentDispatcher: ACTION_WEB_SEARCH handler disappeared, falling back")
            fireWebSearchFallback(query)
        } catch (t: Throwable) {
            Timber.w(t, "AndroidIntentDispatcher: failed ACTION_WEB_SEARCH queryChars=%d", query.length)
            IntentResult.Failed(t.message ?: "web search dispatch failed")
        }
    }

    private fun fireWebSearchFallback(query: String): IntentResult {
        val url = "https://www.google.com/search?q=${query.encodeUriComponent()}"
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val chooser = Intent.createChooser(view, "Search the web")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            IntentResult.ChooserShown
        }.getOrElse {
            Timber.w(it, "AndroidIntentDispatcher: failed web search browser fallback")
            IntentResult.Failed(it.message ?: "web search fallback failed")
        }
    }

    private fun fireOpenContact(contactUri: String): IntentResult {
        if (!contactUri.isAllowedContactsUri()) {
            return IntentResult.Failed("invalid contact uri")
        }
        val uri = runCatching { Uri.parse(contactUri) }.getOrNull()
            ?: return IntentResult.Failed("invalid contact uri")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent) { "ACTION_VIEW contactUri" }
    }

    private fun fireOpenFilePicker(action: AssistantAction.OpenFilePicker): IntentResult {
        val intentAction = when (action.mode) {
            FilePickerMode.SEARCH -> Intent.ACTION_GET_CONTENT
            FilePickerMode.OPEN -> Intent.ACTION_OPEN_DOCUMENT
        }
        val intent = Intent(intentAction).apply {
            type = action.mimeType.ifBlank { "*/*" }
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(intent) { "$intentAction type=${intent.type.orEmpty()}" }
    }

    private fun fireOpenPhotos(): IntentResult {
        val gallery = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_GALLERY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = start(gallery) { "ACTION_MAIN CATEGORY_APP_GALLERY" }
        if (result != IntentResult.NoHandler) return result
        val images = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(images) { "ACTION_VIEW images" }
    }

    private fun fireOpenCalculator(): IntentResult {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALCULATOR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent) { "ACTION_MAIN CATEGORY_APP_CALCULATOR" }
    }

    private inline fun start(intent: Intent, label: () -> String): IntentResult {
        if (intent.resolveActivity(context.packageManager) == null) return IntentResult.NoHandler
        return runCatching {
            context.startActivity(intent)
            Timber.d("AndroidIntentDispatcher: dispatched %s", label())
            IntentResult.Dispatched(component = intent.component?.flattenToString())
        }.getOrElse {
            Timber.w(it, "AndroidIntentDispatcher: failed %s", label())
            IntentResult.Failed(it.dispatchFailureReason())
        }
    }

    private fun Throwable.dispatchFailureReason(): String =
        when {
            this is SecurityException &&
                message.orEmpty().contains(SET_ALARM_PERMISSION) ->
                "missing_manifest_permission:$SET_ALARM_PERMISSION"
            else -> message ?: "dispatch failed"
        }

    private companion object {
        const val SET_ALARM_PERMISSION = "com.android.alarm.permission.SET_ALARM"
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

internal fun openUrlIntentActionForScheme(scheme: String?): String =
    when (scheme?.lowercase()) {
        "mailto", "sms", "smsto" -> Intent.ACTION_SENDTO
        else -> Intent.ACTION_VIEW
    }

internal fun String.isAllowedContactsUri(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("content://com.android.contacts/contacts/lookup/") ||
        normalized.startsWith("content://com.android.contacts/contacts/")
}

internal fun String.foodDeliveryWebFallbackUrl(): String? {
    val normalized = trim()
    val lower = normalized.lowercase()
    return when {
        lower.startsWith("swiggy://search") ->
            "https://www.swiggy.com/search?query=${normalized.queryParamOrNull("query") ?: return null}"
        lower == "swiggy://orders" ->
            "https://www.swiggy.com/my-account/orders"
        lower.startsWith("zomato://search") ->
            "https://www.zomato.com/search?q=${normalized.queryParamOrNull("q") ?: return null}"
        lower == "zomato://orders" ->
            "https://www.zomato.com/orders"
        else -> null
    }
}

private fun String.queryParamOrNull(name: String): String? {
    val query = substringAfter('?', missingDelimiterValue = "")
    if (query.isBlank()) return null
    return query.split('&')
        .firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', missingDelimiterValue = "")
            value.takeIf { key == name && it.isNotBlank() }
        }
}

private const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
private const val CALENDAR_EVENT_MIME_TYPE = "vnd.android.cursor.item/event"
