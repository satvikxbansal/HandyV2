package com.handy.app.chat

import com.handy.core.llm.ConfirmationPrompter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Thread-safe rendezvous between the [HandyToolRunner] and the chat UI
 * for destructive-action confirmations.
 *
 * The runner calls [confirm] (via its [ConfirmationPrompter] binding);
 * the call suspends until [respond] is invoked. The chat ViewModel
 * observes [pending] to render a bottom sheet / dialog and calls
 * [respond] when the user taps Continue / Cancel.
 *
 * Scope is `@Singleton` because the tool runner is singleton-scoped
 * (bound from `:android-runtime`) and must see the same broker as the
 * ViewModel that hosts the UI.
 *
 * Only one confirmation is active at a time; the runner awaits serially
 * because Claude emits tool calls sequentially per turn.
 */
@Singleton
class ChatConfirmationBroker @Inject constructor() : ConfirmationPrompter {

    data class Request(val id: Long, val reason: String) {
        override fun toString(): String =
            "Request(id=$id, reason=[redacted:${reason.length} chars])"
    }

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    private val lock = Any()
    private var idCounter = 0L
    private var current: Pair<Long, CompletableDeferred<Boolean>>? = null

    override suspend fun confirm(reason: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        var previous: CompletableDeferred<Boolean>? = null
        val requestId = synchronized(lock) {
            previous = current?.second
            val id = ++idCounter
            current = id to deferred
            _pending.value = Request(id = id, reason = reason)
            id
        }
        previous?.complete(false)
        Timber.d("ChatConfirmationBroker: suspending reasonChars=%d", reason.length)
        return try {
            deferred.await()
        } finally {
            synchronized(lock) {
                if (current?.first == requestId) {
                    _pending.value = null
                    current = null
                }
            }
        }
    }

    /**
     * Called by the UI when the user taps Continue / Cancel. The
     * [requestId] must match the current pending request to avoid races
     * — e.g. the user tapping the old prompt after a new one has
     * already fired.
     */
    fun respond(requestId: Long, approved: Boolean) {
        val deferred = synchronized(lock) {
            val snapshot = current
            if (snapshot == null || snapshot.first != requestId) {
                Timber.d("ChatConfirmationBroker.respond: stale id=%d (current=%d)", requestId, snapshot?.first ?: -1)
                null
            } else {
                snapshot.second
            }
        }
        deferred?.complete(approved)
    }

    /** Cancel any in-flight confirmation (e.g. ViewModel clearing). */
    fun clear() {
        val deferred = synchronized(lock) {
            val snapshot = current
            _pending.value = null
            current = null
            snapshot?.second
        }
        deferred?.complete(false)
    }
}
