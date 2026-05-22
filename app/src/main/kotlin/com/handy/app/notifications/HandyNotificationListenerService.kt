package com.handy.app.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.handy.core.notification.NotificationSnapshot
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Narrow V2 notification listener — scope §8.
 *
 * What we do:
 *  - Expose a `StateFlow<List<NotificationSnapshot>>` of posted
 *    notifications, updated on post / remove.
 *  - Honour the settings gate (`notificationListenerEnabled`) — when
 *    disabled the service still binds (it has to, or Android revokes
 *    the listener permission) but publishes an empty list and skips
 *    any processing.
 *
 * Notification reply / dismiss are deferred to A4 / Phase 6 (RemoteInput) and will require STRONG_HOLD policy confirmation.
 *
 * What we explicitly DO NOT do:
 *  - Ambient triage, rule engines, auto-reply, auto-dismiss — scope
 *    §8.3 fence.
 *  - Read redacted lock-screen notifications beyond what the system
 *    already exposes.
 */
@AndroidEntryPoint
class HandyNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var settings: DataStoreSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<List<NotificationSnapshot>>(emptyList())
    val state: StateFlow<List<NotificationSnapshot>> = _state.asStateFlow()

    fun canReplyTo(snapshot: NotificationSnapshot): Boolean = snapshot.canReply

    override fun onCreate() {
        super.onCreate()
        active = this
    }

    override fun onDestroy() {
        active = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.d("HandyNotificationListenerService: connected")
        refresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Timber.d("HandyNotificationListenerService: disconnected")
        _state.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refresh()
    }

    private fun refresh() {
        scope.launch {
            val enabled = runCatching { settings.current().notificationListenerEnabled }
                .getOrDefault(false)
            if (!enabled) {
                _state.value = emptyList()
                return@launch
            }
            val active = runCatching { activeNotifications.orEmpty() }.getOrElse { emptyArray() }
            val snapshots = active.mapNotNull { sbn -> toSnapshot(sbn) }
            _state.value = snapshots
        }
    }

    private fun toSnapshot(sbn: StatusBarNotification): NotificationSnapshot? {
        return try {
            val extras = sbn.notification?.extras ?: return null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            val flags = sbn.notification?.flags ?: 0
            val isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0
            val isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0
            val canReply = sbn.notification?.actions?.any { it.remoteInputs?.isNotEmpty() == true }
                ?: false
            val appLabel = runCatching { loadAppLabel(sbn.packageName) }.getOrNull()
            NotificationSnapshot(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = title,
                text = text,
                subText = subText,
                whenEpochMs = sbn.postTime,
                isOngoing = isOngoing,
                isGroupSummary = isGroupSummary,
                groupKey = sbn.groupKey,
                // We never handle lock-screen redacted copies specially —
                // if the OS exposes text we use it; if not, text is null.
                isRedacted = text == null && subText == null,
                canReply = canReply,
                canDismiss = sbn.isClearable,
            )
        } catch (t: Throwable) {
            Timber.w(t, "toSnapshot failed for %s", sbn.key)
            null
        }
    }

    private fun loadAppLabel(packageName: String): String? = try {
        val pm = packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (t: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        @Volatile
        var active: HandyNotificationListenerService? = null
            private set

        /**
         * True when Handy's listener is enabled by the user AND bound
         * by the system. `NotificationManagerCompat.getEnabledListenerPackages`
         * returns packages; we check our own.
         */
        fun isGranted(context: Context): Boolean {
            val listeners = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context)
            return context.packageName in listeners
        }

        /** Toggle the service on/off — used when the user disables the feature mid-session. */
        fun rebind(context: Context) {
            val pm = context.packageManager
            val component = ComponentName(
                context,
                HandyNotificationListenerService::class.java,
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
