package com.handy.core.overlay

/**
 * Flag-set selector for the single Handy overlay window.
 *
 * V1 had one mode (`IdleWidget`). V2 adds surfaces for the overlay
 * chat panel, the Unified Buddy flight, pointing, and action
 * execution. Each mode maps to a distinct `WindowManager.LayoutParams`
 * flag set computed by `overlayFlagsFor(mode)` in `:app`.
 *
 * Rules (scope §2, OS-2):
 *  - `IdleWidget` keeps `FLAG_NOT_FOCUSABLE` — never steals touches.
 *  - `ChatPanel` drops `FLAG_NOT_FOCUSABLE` so the IME can focus the
 *    input field. Restored on dismiss.
 *  - `Flying`, `Pointing`, `ManualTargetSelection`, `Acting` keep the
 *    idle flag set (no focus or IME input required).
 */
enum class OverlayMode {
    IdleWidget,
    ChatPanel,
    Flying,
    Pointing,
    ManualTargetSelection,
    Acting,
}
