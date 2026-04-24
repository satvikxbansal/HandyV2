package com.handy.app.assist

import android.content.Intent
import com.handy.app.chat.ChatActivity
import com.handy.app.voice.VoiceController
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.LifecycleService
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Optional Assist entry — scope §11.3.
 *
 * We deliberately do NOT extend `VoiceInteractionService` because:
 *  - VIS requires a runtime privilege (`BIND_VOICE_INTERACTION`) that
 *    is inconsistent across OEMs and would gate the entire feature on
 *    grant flows that don't exist on some skins.
 *  - Handy's assistant experience is the overlay panel — "Assist"
 *    should simply route the user there.
 *
 * So this service is a lightweight trampoline registered for the
 * `android.intent.action.ASSIST` filter (below), which routes to
 * the same open-panel path as the Quick Settings tile. On OEMs where
 * the long-press-home assist launcher doesn't honour the filter, the
 * feature silently degrades — the user still has the widget, the
 * tile, and the launcher icon.
 */
@AndroidEntryPoint
class HandyAssistIntentService : LifecycleService() {

    @Inject lateinit var settings: DataStoreSettings
    @Inject lateinit var voiceController: VoiceController

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val enabled = runCatching { runBlocking { settings.current().assistEntryEnabled } }
            .getOrDefault(false)
        if (!enabled) {
            Timber.d("HandyAssistIntentService: assist entry disabled — ignoring")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val chat = Intent(this, ChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(chat)
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
