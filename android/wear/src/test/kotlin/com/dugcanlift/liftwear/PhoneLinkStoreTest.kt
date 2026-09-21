package com.dugcanlift.liftwear

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneLinkStoreTest {
    private fun store() = PhoneLinkStore(ApplicationProvider.getApplicationContext())

    @Test fun `a fresh watch is paired with nothing`() {
        val store = store()
        assertFalse(store.isPaired)
        assertNull(store.pairedAddress)
        assertNull(store.pairedName)
        assertFalse(store.isPaired("AA:BB:CC:DD:EE:FF"))
    }

    @Test fun `a remembered phone survives a new instance, which is the point of remembering it`() {
        store().remember("AA:BB:CC:DD:EE:FF", "Pixel 8", 1758441600)
        val reopened = store()
        assertTrue(reopened.isPaired)
        assertEquals("AA:BB:CC:DD:EE:FF", reopened.pairedAddress)
        assertEquals("Pixel 8", reopened.pairedName)
        assertEquals(1758441600L, reopened.pairedAtEpochSeconds)
    }

    @Test fun `addresses match whatever case the platform hands back`() {
        store().remember("aa:bb:cc:dd:ee:ff", "Pixel 8")
        assertTrue(store().isPaired("AA:BB:CC:DD:EE:FF"))
    }

    @Test fun `another phone is not this phone`() {
        store().remember("AA:BB:CC:DD:EE:FF", "Pixel 8")
        assertFalse(store().isPaired("11:22:33:44:55:66"))
        assertFalse(store().isPaired(null))
    }

    @Test fun `a second pairing replaces the first rather than joining it`() {
        val store = store()
        store.remember("AA:BB:CC:DD:EE:FF", "Pixel 8")
        store.remember("11:22:33:44:55:66", "Galaxy S24")
        assertEquals("11:22:33:44:55:66", store.pairedAddress)
        assertFalse(store.isPaired("AA:BB:CC:DD:EE:FF"))
    }

    @Test fun `forgetting takes the watch back to standalone`() {
        val store = store()
        store.remember("AA:BB:CC:DD:EE:FF", "Pixel 8")
        store.forget()
        assertFalse(store().isPaired)
        assertNull(store().pairedName)
    }
}
