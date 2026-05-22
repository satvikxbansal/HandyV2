package com.handy.app.chat

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatConfirmationBrokerTest {

    @Test
    fun `new confirmation cancels previous request without clearing latest`() = runTest {
        val broker = ChatConfirmationBroker()

        val first = async { broker.confirm("first") }
        runCurrent()
        assertThat(broker.pending.value?.id).isEqualTo(1L)

        val second = async { broker.confirm("second") }
        runCurrent()

        assertThat(first.await()).isFalse()
        assertThat(broker.pending.value?.id).isEqualTo(2L)
        assertThat(broker.pending.value?.reason).isEqualTo("second")

        broker.respond(requestId = 2L, approved = true)

        assertThat(second.await()).isTrue()
        assertThat(broker.pending.value).isNull()
    }
}
