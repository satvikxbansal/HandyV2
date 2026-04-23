package com.handy.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Classpath guard for the `:core` module.
 *
 * `:core` must be pure Kotlin / JVM — no `android.*`, no `androidx.*`.
 * If any of those leak into the module's dependencies (e.g. someone
 * accidentally adds `coreLibraryDesugaring` or an Android library), the
 * assertions below will start to find the classes.
 *
 * Add a new assertion here whenever the build plan gains a new category
 * of Android-only API we must keep out of `:core`.
 */
class NoAndroidImportsTest {

    @Test fun `android framework is not on the core classpath`() {
        assertThrows<ClassNotFoundException> {
            Class.forName("android.content.Context")
        }
        assertThrows<ClassNotFoundException> {
            Class.forName("android.app.Activity")
        }
        assertThrows<ClassNotFoundException> {
            Class.forName("android.view.View")
        }
        assertThrows<ClassNotFoundException> {
            Class.forName("android.speech.SpeechRecognizer")
        }
    }

    @Test fun `androidx is not on the core classpath`() {
        assertThrows<ClassNotFoundException> {
            Class.forName("androidx.core.app.ActivityCompat")
        }
        assertThrows<ClassNotFoundException> {
            Class.forName("androidx.compose.ui.Modifier")
        }
        assertThrows<ClassNotFoundException> {
            Class.forName("androidx.datastore.preferences.core.Preferences")
        }
    }

    @Test fun `Android-coroutines dispatcher is not on the core classpath`() {
        assertThrows<ClassNotFoundException> {
            Class.forName("kotlinx.coroutines.android.AndroidDispatcherFactory")
        }
    }

    @Test fun `core package marker is loadable`() {
        // Sanity check: the module itself is actually on the classpath
        // so the negative assertions above are meaningful.
        val handy = Class.forName("com.handy.core.Handy")
        assertThat(handy.packageName).isEqualTo("com.handy.core")
    }
}
