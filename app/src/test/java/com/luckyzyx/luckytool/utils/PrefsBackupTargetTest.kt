package com.luckyzyx.luckytool.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 备份 JSON 的落点字段：恢复必须按备份记录的 target 写回，旧备份没有 target 时回落到当前落点。
 *
 * 这两种格式要能同时读出来 —— 用户手里还留着迁移前导出的备份，
 * 读不出 data 就会被当成「没有数据」静默跳过一个 prefs 文件。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsBackupTargetTest {

    @Test
    fun `target key maps both ways`() {
        assertEquals(PrefsTarget.REMOTE, PrefsTarget.ofKey("remote"))
        assertEquals(PrefsTarget.LOCAL, PrefsTarget.ofKey("local"))
        assertNull(PrefsTarget.ofKey("Remote"))
        assertNull(PrefsTarget.ofKey(null))
    }

    @Test
    fun `new format reports recorded target and data`() {
        val json = JSONObject().apply {
            put(
                ModulePrefs, JSONObject().apply {
                    put("target", PrefsTarget.LOCAL.key)
                    put("data", JSONObject().apply { put("alpha", 7) })
                },
            )
        }

        assertEquals(PrefsTarget.LOCAL, json.recordedTarget(ModulePrefs))
        assertEquals(7, json.prefsData(ModulePrefs)?.getInt("alpha"))
    }

    @Test
    fun `legacy flat format has no target but still yields data`() {
        val json = JSONObject().apply {
            put(ModulePrefs, JSONObject().apply {
                put("flag", true)
                put("names", JSONArray().apply { put("com.a") })
            })
        }

        assertNull(json.recordedTarget(ModulePrefs))
        val data = json.prefsData(ModulePrefs)
        assertEquals(true, data?.getBoolean("flag"))
        assertEquals("com.a", data?.getJSONArray("names")?.getString(0))
    }

    @Test
    fun `missing prefs name yields null for both helpers`() {
        val json = JSONObject()

        assertNull(json.recordedTarget(OtherPrefs))
        assertNull(json.prefsData(OtherPrefs))
    }

    @Test
    fun `unknown target string reads as not recorded`() {
        val json = JSONObject().apply {
            put(IntentPrefs, JSONObject().apply {
                put("target", "cloud")
                put("data", JSONObject())
            })
        }

        assertNull(json.recordedTarget(IntentPrefs))
    }
}
