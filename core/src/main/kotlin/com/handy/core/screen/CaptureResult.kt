package com.handy.core.screen

import com.handy.core.model.ImagePart

/**
 * Outcome of a screenshot attempt. Crosses every orchestration boundary
 * — the LLM path never accepts a raw `Bitmap` (OS-5).
 *
 * Detection of [SecureWindow] comes from the capture API itself (official
 * failure codes; black-frame heuristic on the `MediaProjection` path).
 * `windowInfo.isSecure` is an optional hint only, never the required
 * source of truth.
 */
sealed class CaptureResult {

    /** Usable screenshot, already JPEG-encoded. */
    data class Image(val image: ImagePart) : CaptureResult()

    /**
     * Active window is `FLAG_SECURE` or otherwise returned an unusable
     * (typically all-black) buffer. Orchestrator must inject the canonical
     * "I can't see this screen" system message instead of calling the LLM.
     */
    data object SecureWindow : CaptureResult()

    /** Missing runtime permission or user denial. */
    data object NotPermitted : CaptureResult()

    /** No backing capture API on this device. */
    data object Unsupported : CaptureResult()

    /** Everything else; [reason] is a short log-friendly string. */
    data class Failed(val reason: String) : CaptureResult()

    val isUsable: Boolean
        get() = this is Image
}

/**
 * Canonical "secure screen" system message injected into the conversation
 * whenever the capture pipeline returns [CaptureResult.SecureWindow] or
 * the tree reader detects a secure window.
 *
 * Ported from the guardrails (OS-5) verbatim so the wording is stable.
 */
const val SECURE_WINDOW_SYSTEM_MESSAGE: String =
    "I can't see this screen — the app is marked secure (e.g. banking, incognito, or password manager). Ask me again from a non-secure screen, or paste the text you want help with."
