package com.handy.core

/**
 * Marker for the pure-Kotlin `:core` module.
 *
 * If you find yourself tempted to add `import android.*` in this module,
 * stop. Move that code into `:android-runtime` behind an interface defined
 * in `:core`. A classpath-guard test in Phase 1 will fail the build if any
 * `android.*` reference leaks in.
 */
internal object Handy {
    const val MODULE = "core"
}
