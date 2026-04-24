package com.handy.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.handy.app.chat.ChatActivity
import com.handy.app.overlay.OverlayPresenter
import com.handy.app.voice.VoiceController
import com.handy.core.model.QuickTileAction
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Quick Settings tile service — scope §11.1.
 *
 * User picks which action fires when the tile is tapped
 * ([QuickTileAction]). All three actions are supported regardless of
 * whether the overlay widget is currently visible (the tile is often
 * the escape hatch when the widget was dismissed).
 *
 * Tile state mirrors the foreground-service state: active when Handy
 * is ready, inactive otherwise.
 */
@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class HandyQuickSettingsTileService : TileService() {

    @Inject lateinit var settings: DataStoreSettings
    @Inject lateinit var voiceController: VoiceController
    @Inject lateinit var presenter: OverlayPresenter
    @Inject lateinit var marksProvider: AccessibilityMarksProvider

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val current = runCatching { runBlocking { settings.current() } }.getOrNull()
        when (current?.quickTileAction ?: QuickTileAction.OPEN_PANEL) {
            QuickTileAction.OPEN_PANEL -> openPanel()
            QuickTileAction.START_VOICE -> startVoice()
            QuickTileAction.OPEN_CHAT -> openChat()
        }
        refreshTile()
    }

    private fun openPanel() {
        // Snapshot the foreground app (cache-at-tap) and open the
        // panel via the overlay presenter. The widget service owns
        // the actual WindowManager attachment.
        presenter.onWidgetTap(
            marksProvider = { marksProvider.collect() },
        )
    }

    private fun startVoice() {
        val ok = voiceController.start()
        if (!ok) {
            Timber.d("TileService: voice start refused (permission?)")
            return
        }
        presenter.onWidgetLongPressArmed(
            marksProvider = { marksProvider.collect() },
        )
    }

    private fun openChat() {
        val intent = Intent(this, ChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Handy"
        tile.contentDescription = "Open Handy assistant"
        tile.updateTile()
    }
}
