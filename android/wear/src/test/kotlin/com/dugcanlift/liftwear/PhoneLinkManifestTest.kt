package com.dugcanlift.liftwear

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The manifest, read as XML rather than through Robolectric, so the assertions are about the file
 * that ships.
 *
 * The permissions this pins are the reason the Wear app can talk to a phone at all — and the ones
 * it pins as *absent* are the reason it is allowed to. No internet and no Play Services is the
 * whole premise of this app; a link that quietly reintroduced either would have undone it.
 *
 * `wear/build.gradle.kts` declares the manifest as an input of the test task, so editing it alone
 * reruns this.
 */
class PhoneLinkManifestTest {
    private val manifest: Element by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
            .documentElement
    }

    private fun named(tag: String, attribute: String = "name"): Map<String, Element> =
        manifest.getElementsByTagName(tag).let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
                .associateBy { it.getAttributeNS(ANDROID, attribute) }
        }

    private val permissions get() = named("uses-permission")

    @Test fun `the watch may advertise and may connect`() {
        assertTrue(permissions.containsKey("android.permission.BLUETOOTH_ADVERTISE"))
        assertTrue(permissions.containsKey("android.permission.BLUETOOTH_CONNECT"))
    }

    @Test fun `the watch never scans, so it asks for no scan permission`() {
        // The peripheral is advertised *to*. A scan permission here would be a permission nothing
        // uses, and on this platform it drags a location question along with it.
        assertFalse(permissions.containsKey("android.permission.BLUETOOTH_SCAN"))
        assertFalse(permissions.containsKey("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse(permissions.containsKey("android.permission.ACCESS_COARSE_LOCATION"))
    }

    @Test fun `the legacy pair is capped at the last API level that needs them`() {
        listOf("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN").forEach { name ->
            val element = requireNotNull(permissions[name]) { "$name is missing from the manifest" }
            assertEquals(name, "30", element.getAttributeNS(ANDROID, "maxSdkVersion"))
        }
    }

    @Test fun `there is still no internet permission`() {
        // The premise of this app. A phone link over Bluetooth is what lets that stay true.
        assertFalse(permissions.containsKey("android.permission.INTERNET"))
        assertFalse(permissions.containsKey("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test fun `there is still no Play Services`() {
        val text = File("src/main/AndroidManifest.xml").readText()
        assertFalse(text.contains("com.google.android.gms"))
        assertFalse(text.contains("com.google.android.wearable.datalayer"))
    }

    @Test fun `the app is still declared standalone`() {
        val standalone = named("meta-data")["com.google.android.wearable.standalone"]
        assertNotNull(standalone)
        assertEquals("true", standalone!!.getAttributeNS(ANDROID, "value"))
    }

    @Test fun `Bluetooth LE is not required, because the watch works without it`() {
        val feature = named("uses-feature")["android.hardware.bluetooth_le"]
        assertNotNull(feature)
        assertEquals("false", feature!!.getAttributeNS(ANDROID, "required"))
    }

    @Test fun `nothing is backed up off the watch`() {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("false", application.getAttributeNS(ANDROID, "allowBackup"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
