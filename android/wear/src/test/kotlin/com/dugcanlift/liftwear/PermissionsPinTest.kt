package com.dugcanlift.liftwear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every permission LIFT for Wear OS ships with, pinned to an explicit list that the public privacy
 * policy has to match.
 *
 * The page is `www.dugcanlift.com/app/privacy/` (`dugcanlift-site/app-privacy.md`): "The watch
 * apps" > "Wear OS", plus the Wear OS lines in "Location" and "Health Connect and Apple Health".
 * **A new permission fails this test until someone has updated that page.** The page is public copy
 * and Doug reviews it, so change `app-privacy.md` first and this list second, never the list alone.
 *
 * **The page does not match this list yet.** It describes the released 1.0.1, which "Asks for no
 * permissions"; LIFT Link and the guided session added the Bluetooth, heart-rate and vibrate entries
 * below, and the release that ships them needs the page's wording first.
 *
 * [PhoneLinkManifestTest] says why each permission is there and pins the ones that must stay absent;
 * this pins the whole list, and reads the release variant's **merged** manifest rather than
 * `src/main/AndroidManifest.xml`: the merged one is the app's own manifest plus every library's,
 * which is what an installed APK actually declares. `wear/build.gradle.kts` hands its path across as
 * `releaseMergedManifest` and declares it an input of every unit test task, so `testDebugUnitTest`
 * runs `processReleaseManifest` first.
 */
class PermissionsPinTest {

    private val expected: Set<String> = setOf(
        // LIFT Link (docs/LINK-PROTOCOL.md): the watch advertises and accepts a connection, and
        // never scans. The legacy pair exists only for API 30.
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH maxSdkVersion=30",
        "android.permission.BLUETOOTH_ADMIN maxSdkVersion=30",
        // The guided session's heart rate, through Health Services (HeartRateRecorder).
        "android.permission.BODY_SENSORS",
        // The buzz at the end of a rest. Install-time: it asks the person nothing.
        "android.permission.VIBRATE",
        // Not a request to the user or to the system: androidx.core declares this signature-level
        // permission and holds it itself, so a receiver registered NOT_EXPORTED on Android 12 and
        // older is protected from other apps. Only an app signed with LIFT's key can hold it.
        "com.dugcanlift.liftwear.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )

    private val page = "dugcanlift-site/app-privacy.md, \"The watch apps\" > \"Wear OS\""

    private val manifest: Element by lazy {
        val path = requireNotNull(System.getProperty("releaseMergedManifest")) {
            "releaseMergedManifest is not set: see wear/build.gradle.kts"
        }
        val file = File(path)
        assertTrue("merged manifest not found at ${file.absolutePath}", file.isFile)
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun elements(tag: String): List<Element> =
        manifest.getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    /** A permission and the qualifiers that change what it grants, written the way [expected] is. */
    private fun describe(element: Element): String {
        val qualifiers = listOf("maxSdkVersion", "minSdkVersion", "usesPermissionFlags")
            .mapNotNull { name -> element.getAttributeNS(ANDROID, name).takeIf { it.isNotEmpty() }?.let { "$name=$it" } }
        return (listOf(element.getAttributeNS(ANDROID, "name")) + qualifiers).joinToString(" ")
    }

    @Test fun `the release APK asks for exactly what the privacy page describes`() {
        // uses-permission-sdk-23 is the same request, limited to API 23 and up; it counts.
        val declared = (elements("uses-permission") + elements("uses-permission-sdk-23")).map(::describe)
        assertEquals("a permission is declared twice: $declared", declared.size, declared.toSet().size)
        assertEquals(
            "The release APK's permissions changed. Update $page first, then this list.",
            expected.sorted().joinToString("\n"),
            declared.sorted().joinToString("\n"),
        )
    }

    @Test fun `the only permission LIFT defines is androidx's signature-level receiver guard`() {
        val defined = elements("permission").associate {
            it.getAttributeNS(ANDROID, "name") to it.getAttributeNS(ANDROID, "protectionLevel")
        }
        assertEquals(mapOf("com.dugcanlift.liftwear.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" to "signature"), defined)
    }

    @Test fun `this is the release manifest, merged`() {
        // The release application id, not the debug build's ".debug" one: what ships is pinned.
        assertEquals("com.dugcanlift.liftwear", manifest.getAttribute("package"))
        // Merging writes the libraries' components into the file (androidx.startup's provider,
        // Health Services' version service); src/main names none of them.
        assertTrue(
            "this does not look like a merged manifest",
            listOf("activity", "provider", "receiver", "service").flatMap(::elements)
                .any { it.getAttributeNS(ANDROID, "name").startsWith("androidx.") },
        )
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
