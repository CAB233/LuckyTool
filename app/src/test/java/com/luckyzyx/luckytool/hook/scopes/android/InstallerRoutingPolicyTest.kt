package com.luckyzyx.luckytool.hook.scopes.android

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class InstallerRoutingPolicyTest {
    private val mime = InstallerRoutingPolicy.APK_MIME_TYPE

    @Test
    fun `only local APK view and install requests are unlocked`() {
        for (scheme in listOf("content", "file")) {
            assertTrue(InstallerRoutingPolicy.matches(apk(Intent.ACTION_VIEW, scheme)))
            assertTrue(InstallerRoutingPolicy.matches(apk("android.intent.action.INSTALL_PACKAGE", scheme)))
            assertTrue(InstallerRoutingPolicy.matches(Intent("android.intent.action.INSTALL_PACKAGE", Uri.parse("$scheme://sample/item"))))
        }
        for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_DELETE, "android.intent.action.UNINSTALL_PACKAGE", "android.content.pm.action.CONFIRM_INSTALL", "android.content.pm.action.CONFIRM_PERMISSIONS")) {
            assertFalse(InstallerRoutingPolicy.matches(apk(action, "content")))
        }
        for (scheme in listOf("https", "http", "package")) {
            assertFalse(InstallerRoutingPolicy.matches(apk(Intent.ACTION_VIEW, scheme)))
        }
        assertFalse(InstallerRoutingPolicy.matches(Intent(Intent.ACTION_VIEW).setType(mime)))
        assertFalse(InstallerRoutingPolicy.matches(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://sample/item.apk"), "text/plain")))
        assertFalse(InstallerRoutingPolicy.matches(null as Intent?))
    }

    @Test
    fun `matching leaves explicit targets payload and URI grants untouched`() {
        val intent = apk(Intent.ACTION_VIEW, "content").apply {
            component = ComponentName("test.installer", "test.installer.InstallActivity")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("sample", "value")
        }
        val original = intent.toUri(0)
        assertTrue(InstallerRoutingPolicy.matches(intent))
        assertEquals(original, intent.toUri(0))
        intent.selector = Intent(Intent.ACTION_SEND)
        assertFalse(InstallerRoutingPolicy.matches(intent))
    }

    @Test
    fun `remembering a choice only bypasses policy for exact APK filters`() {
        for (action in listOf(Intent.ACTION_VIEW, "android.intent.action.INSTALL_PACKAGE")) {
            val filter = IntentFilter(action, mime)
            assertTrue(InstallerRoutingPolicy.matches(filter))
        }
        assertFalse(InstallerRoutingPolicy.matches(IntentFilter(Intent.ACTION_VIEW, "*/*")))
        assertFalse(InstallerRoutingPolicy.matches(IntentFilter(Intent.ACTION_VIEW, "text/plain")))
        assertFalse(InstallerRoutingPolicy.matches(IntentFilter(Intent.ACTION_SEND, mime)))
        assertFalse(InstallerRoutingPolicy.matches(null as IntentFilter?))
    }

    private fun apk(action: String, scheme: String) = Intent(action)
        .setDataAndType(Uri.parse("$scheme://sample/item.apk"), mime)
}
