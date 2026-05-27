package com.handy.app.accessibility

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

object ActionAuditSuppression {
    private val suppressed = ThreadLocal.withInitial { false }

    fun isSuppressed(): Boolean = suppressed.get() == true

    suspend fun <T> suppress(block: suspend () -> T): T =
        withContext(suppressed.asContextElement(true)) {
            block()
        }
}
