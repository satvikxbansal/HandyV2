package com.handy.runtime

/**
 * Marker for the `:android-runtime` module.
 *
 * This module owns Android adapters and wrappers only:
 *  - No UI (no Compose, no Activities, no Fragments).
 *  - No manifest-declared Android components (Phase 3 puts those in `:app`).
 *
 * Phase 2 fills this module with `ClaudeLlmClient`, capture pipeline, STT/TTS
 * adapters, JSON history store, intent dispatcher, `LaunchableAppIndex`,
 * `EncryptedKeyStore`, `SemanticPointerResolver`, and their Hilt bindings.
 */
internal object RuntimeModuleMarker {
    const val MODULE = "android-runtime"
}
