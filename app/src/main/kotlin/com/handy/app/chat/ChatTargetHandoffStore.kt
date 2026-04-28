package com.handy.app.chat

import com.handy.core.overlay.PanelSnapshot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local handoff from overlay/widget launch points into ChatActivity.
 *
 * Intent extras carry only an opaque id; the potentially large accessibility
 * marks stay in memory and are ignored if the process has been killed.
 */
@Singleton
class ChatTargetHandoffStore @Inject constructor() {
    private val snapshots = ConcurrentHashMap<String, PanelSnapshot>()

    fun put(snapshot: PanelSnapshot): String {
        val id = UUID.randomUUID().toString()
        snapshots[id] = snapshot
        return id
    }

    fun get(id: String?): PanelSnapshot? =
        id?.takeIf { it.isNotBlank() }?.let(snapshots::get)
}
