package com.handy.app.chat

import com.google.common.truth.Truth.assertThat
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.tool.ToolContext
import org.junit.Test

class ChatTargetHandoffStoreTest {

    @Test
    fun `stores snapshots behind opaque ids`() {
        val store = ChatTargetHandoffStore()
        val snapshot = PanelSnapshot(
            toolContext = ToolContext(
                packageName = "com.google.android.apps.photos",
                appLabel = "Photos",
            ),
            capturedAtEpochMs = 10L,
        )

        val id = store.put(snapshot)

        assertThat(id).isNotEmpty()
        assertThat(store.get(id)).isSameInstanceAs(snapshot)
    }

    @Test
    fun `returns null for missing ids`() {
        val store = ChatTargetHandoffStore()

        assertThat(store.get(null)).isNull()
        assertThat(store.get("")).isNull()
        assertThat(store.get("missing")).isNull()
    }
}
