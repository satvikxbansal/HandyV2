package com.handy.runtime.intent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidIntentDispatcherSafetyTest {

    @Test fun `open contact accepts only ContactsProvider contact uris`() {
        assertThat("content://com.android.contacts/contacts/lookup/mom/1".isAllowedContactsUri())
            .isTrue()
        assertThat("content://com.android.contacts/contacts/1".isAllowedContactsUri())
            .isTrue()

        assertThat("https://example.com/pay".isAllowedContactsUri()).isFalse()
        assertThat("content://com.example.private/contacts/1".isAllowedContactsUri()).isFalse()
        assertThat("tel:+15551234567".isAllowedContactsUri()).isFalse()
    }

    @Test fun `food deep links have https fallback urls`() {
        assertThat("swiggy://search?query=biryani".foodDeliveryWebFallbackUrl())
            .isEqualTo("https://www.swiggy.com/search?query=biryani")
        assertThat("swiggy://orders".foodDeliveryWebFallbackUrl())
            .isEqualTo("https://www.swiggy.com/my-account/orders")
        assertThat("zomato://search?q=pizza%20slice".foodDeliveryWebFallbackUrl())
            .isEqualTo("https://www.zomato.com/search?q=pizza%20slice")
        assertThat("zomato://orders".foodDeliveryWebFallbackUrl())
            .isEqualTo("https://www.zomato.com/orders")
        assertThat("https://www.swiggy.com/search?query=biryani".foodDeliveryWebFallbackUrl())
            .isNull()
    }
}
