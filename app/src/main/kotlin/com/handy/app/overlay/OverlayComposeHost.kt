package com.handy.app.overlay

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts a `ComposeView` inside `WindowManager` by supplying the three
 * owners Compose demands.
 *
 * Required by OS-4 (guardrails). Without this, `setContent { }` on a
 * ComposeView inflated into a Service's `WindowManager` crashes with
 * "ViewTreeLifecycleOwner not found".
 *
 * Usage:
 * ```
 * val host = OverlayComposeHost(service)
 * val view = host.createView { MyOverlayContent() }
 * windowManager.addView(view, params)
 * // ... later
 * host.release()
 * windowManager.removeView(view)
 * ```
 */
class OverlayComposeHost(private val context: Context) :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this).apply {
        performAttach()
        performRestore(null)
    }
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun createView(content: @Composable () -> Unit): View {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayComposeHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayComposeHost)
            setViewTreeViewModelStoreOwner(this@OverlayComposeHost)
            setContent(content)
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return view
    }

    fun release() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
