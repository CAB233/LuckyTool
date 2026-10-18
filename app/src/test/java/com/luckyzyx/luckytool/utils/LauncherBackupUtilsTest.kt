package com.luckyzyx.luckytool.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 桌面布局备份/恢复的纯逻辑单测：快照命名、指令协议与去重、清单/回传 JSON、保留策略。
 * 这些函数不碰文件系统也不碰设备，设备相关部分只能在真机验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherBackupUtilsTest {

    private fun snapshot(
        name: String,
        tag: LauncherSnapshotTag,
        time: Long,
        size: Long = 0L,
        rows: Int = 0,
    ) = LauncherSnapshot(name, tag, time, size, rows)

    // ---------------- 快照命名 ----------------

    @Test
    fun snapshotNameUsesTimestampAndTag() {
        val now = 1_770_000_000_000L
        val name = LauncherBackupUtils.snapshotName(now, LauncherSnapshotTag.MANUAL)
        assertTrue(name, Regex("^[0-9]{8}_[0-9]{6}_manual$").matches(name))
        assertTrue(LauncherBackupUtils.snapshotName(now, LauncherSnapshotTag.AUTO).endsWith("_auto"))
        assertTrue(
            LauncherBackupUtils.snapshotName(now, LauncherSnapshotTag.PRE_RESTORE).endsWith("_pre_restore"),
        )
    }

    @Test
    fun snapshotNameRoundTripsThroughParse() {
        val now = 1_770_000_000_000L
        LauncherSnapshotTag.entries.forEach { tag ->
            val name = LauncherBackupUtils.snapshotName(now, tag)
            val parsed = LauncherBackupUtils.parseSnapshotName(name)
            assertTrue("应能解析：" + name, parsed != null)
            assertEquals(tag, parsed!!.tag)
            assertEquals(name, parsed.name)
            // 文件名只精确到秒
            assertEquals(now / 1000L, parsed.timeMillis / 1000L)
        }
    }

    @Test
    fun parseSnapshotNameRejectsForeignDirectories() {
        listOf(
            "",
            "luckytool_backup",
            "20260101_120000",
            "20260101_120000_unknown",
            "backup_20260101_120000_auto",
            "20260101_1200_auto",
        ).forEach { name ->
            assertNull(name, LauncherBackupUtils.parseSnapshotName(name))
        }
    }

    // ---------------- 指令协议 ----------------

    @Test
    fun buildCommandProducesThreeFields() {
        assertEquals(
            "backup|auto|t-1",
            LauncherBackupUtils.buildCommand(LauncherBackupUtils.ACTION_BACKUP, "auto", "t-1"),
        )
        assertEquals(
            "list||t-2",
            LauncherBackupUtils.buildCommand(LauncherBackupUtils.ACTION_LIST, "", "t-2"),
        )
    }

    @Test
    fun parseCommandAcceptsValidCommands() {
        val backup = LauncherBackupUtils.parseCommand("backup|auto|123-abcdef")
        assertEquals(LauncherBackupUtils.ACTION_BACKUP, backup!!.action)
        assertEquals("auto", backup.arg)
        assertEquals("123-abcdef", backup.token)

        val restore = LauncherBackupUtils.parseCommand("restore|20260101_120000_manual|tok")
        assertEquals(LauncherBackupUtils.ACTION_RESTORE, restore!!.action)
        assertEquals("20260101_120000_manual", restore.arg)
    }

    @Test
    fun parseCommandRejectsInvalidCommands() {
        listOf(
            null,
            "",
            "   ",
            "backup|auto",                      // 缺 token
            "backup|auto|",                     // token 空
            "unknown|auto|tok",                 // 未知 action
            "restore||tok",                     // restore 缺快照名
            "delete||tok",                      // delete 缺快照名
        ).forEach { raw ->
            assertNull(raw.toString(), LauncherBackupUtils.parseCommand(raw))
        }
    }

    @Test
    fun parseCommandKeepsTokenWithPipes() {
        val command = LauncherBackupUtils.parseCommand("backup|auto|a|b")
        assertEquals("a|b", command!!.token)
    }

    @Test
    fun duplicateTokenDetection() {
        assertFalse(LauncherBackupUtils.isDuplicateToken(null, "t"))
        assertFalse(LauncherBackupUtils.isDuplicateToken("", "t"))
        assertFalse(LauncherBackupUtils.isDuplicateToken("  ", "t"))
        assertFalse(LauncherBackupUtils.isDuplicateToken("other", "t"))
        assertTrue(LauncherBackupUtils.isDuplicateToken("t", "t"))
    }

    @Test
    fun newTokenIsUnique() {
        val first = LauncherBackupUtils.newToken()
        val second = LauncherBackupUtils.newToken()
        assertTrue(first.isNotEmpty())
        assertFalse(first == second)
    }

    // ---------------- 清单 / 回传 JSON ----------------

    @Test
    fun manifestJsonRoundTripsAndSortsByTimeDesc() {
        val list = listOf(
            snapshot("20260101_080000_auto", LauncherSnapshotTag.AUTO, 1_000L, 10L, 1),
            snapshot("20260102_080000_manual", LauncherSnapshotTag.MANUAL, 3_000L, 20L, 2),
            snapshot("20260103_080000_pre_restore", LauncherSnapshotTag.PRE_RESTORE, 2_000L, 30L, 3),
        )
        val parsed = LauncherBackupUtils.parseManifestJson(LauncherBackupUtils.buildManifestJson(list))
        assertEquals(3, parsed.size)
        assertEquals("20260102_080000_manual", parsed[0].name)
        assertEquals("20260103_080000_pre_restore", parsed[1].name)
        assertEquals("20260101_080000_auto", parsed[2].name)
        assertEquals(LauncherSnapshotTag.PRE_RESTORE, parsed[1].tag)
        assertEquals(30L, parsed[1].sizeBytes)
        assertEquals(3, parsed[1].rows)
        assertEquals(2_000L, parsed[1].timeMillis)
    }

    @Test
    fun manifestJsonParsingIsTolerant() {
        assertTrue(LauncherBackupUtils.parseManifestJson(null).isEmpty())
        assertTrue(LauncherBackupUtils.parseManifestJson("").isEmpty())
        assertTrue(LauncherBackupUtils.parseManifestJson("not json").isEmpty())
        assertTrue(LauncherBackupUtils.parseManifestJson("[]").isEmpty())
        // 缺 name 的条目被跳过，tag 缺失时从目录名回退
        val parsed = LauncherBackupUtils.parseManifestJson(
            "[" + "{\"time\":5}," +
                "{\"name\":\"20260101_120000_auto\",\"size\":7,\"rows\":8}" + "]",
        )
        assertEquals(1, parsed.size)
        assertEquals(LauncherSnapshotTag.AUTO, parsed[0].tag)
        assertEquals(7L, parsed[0].sizeBytes)
        assertEquals(8, parsed[0].rows)
        assertTrue(parsed[0].timeMillis > 0L)
    }

    @Test
    fun stateJsonCarriesSnapshotsAsArray() {
        val list = listOf(snapshot("20260101_080000_auto", LauncherSnapshotTag.AUTO, 1_000L))
        val json = LauncherBackupUtils.buildStateJson(
            ok = true,
            action = LauncherBackupUtils.ACTION_LIST,
            message = "list ok",
            backupRoot = "/data/user_de/0/com.android.launcher/luckytool_backup",
            token = "tok-1",
            snapshots = list,
            snapshot = "20260101_080000_auto",
            time = 4_000L,
        )
        val state = LauncherBackupUtils.parseStateJson(json)
        assertTrue(state != null)
        assertEquals(true, state!!.ok)
        assertEquals(LauncherBackupUtils.ACTION_LIST, state.action)
        assertEquals("list ok", state.message)
        assertEquals("/data/user_de/0/com.android.launcher/luckytool_backup", state.backupRoot)
        assertEquals("tok-1", state.token)
        assertEquals("20260101_080000_auto", state.snapshot)
        assertEquals(4_000L, state.time)
        assertEquals(1, state.snapshots.size)
        assertEquals(LauncherSnapshotTag.AUTO, state.snapshots[0].tag)
    }

    @Test
    fun stateJsonParsingIsTolerant() {
        assertNull(LauncherBackupUtils.parseStateJson(null))
        assertNull(LauncherBackupUtils.parseStateJson(""))
        assertNull(LauncherBackupUtils.parseStateJson("garbage"))
        val empty = LauncherBackupUtils.parseStateJson(LauncherBackupUtils.buildStateJson(
            ok = false,
            action = LauncherBackupUtils.ACTION_RESTORE,
            message = "boom",
            backupRoot = "",
            token = "t",
            snapshots = emptyList(),
        ))
        assertTrue(empty != null)
        assertEquals(false, empty!!.ok)
        assertTrue(empty.snapshots.isEmpty())
    }

    // ---------------- 保留策略 ----------------

    @Test
    fun planEvictionsKeepsEverythingUnderLimit() {
        val list = (0 until LauncherBackupUtils.MAX_SNAPSHOTS).map { index ->
            snapshot("20260101_0000" + index.toString().padStart(2, '0') + "_manual", LauncherSnapshotTag.MANUAL, index.toLong())
        }
        assertTrue(LauncherBackupUtils.planEvictions(list).isEmpty())
    }

    @Test
    fun planEvictionsDeletesOldestAutoFirst() {
        // 19 份 auto + 3 份 manual，超限 2：只该删最旧的两份 auto
        val autos = (0 until 19).map { index ->
            snapshot("20260101_0000" + index.toString().padStart(2, '0') + "_auto", LauncherSnapshotTag.AUTO, index.toLong())
        }
        val manuals = listOf(
            snapshot("20250101_000000_manual", LauncherSnapshotTag.MANUAL, 0L),
            snapshot("20250102_000000_manual", LauncherSnapshotTag.MANUAL, 1L),
            snapshot("20250103_000000_manual", LauncherSnapshotTag.MANUAL, 2L),
        )
        val evicted = LauncherBackupUtils.planEvictions(autos + manuals)
        assertEquals(2, evicted.size)
        assertTrue(evicted.all { it.tag == LauncherSnapshotTag.AUTO })
        assertEquals(listOf(autos[0].name, autos[1].name), evicted.map { it.name })
        // 最旧的 manual 时间更早也不会被删：auto 优先
        assertFalse(evicted.any { it.tag == LauncherSnapshotTag.MANUAL })
    }

    @Test
    fun planEvictionsFallsBackToManualAndPreRestore() {
        val autos = (0 until 2).map { index ->
            snapshot("20260101_00010" + index + "_auto", LauncherSnapshotTag.AUTO, 100L + index)
        }
        val others = (0 until 20).map { index ->
            val tag = if (index % 2 == 0) LauncherSnapshotTag.MANUAL else LauncherSnapshotTag.PRE_RESTORE
            snapshot(
                "20260102_0000" + index.toString().padStart(2, '0') + "_" + tag.id,
                tag,
                index.toLong(),
            )
        }
        // 共 22 份，超限 2：先删光 2 份 auto 就够，不动 manual/pre_restore
        val evicted = LauncherBackupUtils.planEvictions(autos + others)
        assertEquals(2, evicted.size)
        assertTrue(evicted.all { it.tag == LauncherSnapshotTag.AUTO })

        // 超限 4：2 份 auto 全删，再补 2 份最旧的 manual/pre_restore
        val autosFar = listOf(
            snapshot("20260101_000000_auto", LauncherSnapshotTag.AUTO, 900L),
            snapshot("20260101_000001_auto", LauncherSnapshotTag.AUTO, 901L),
        )
        val many = (0 until 22).map { index ->
            val tag = if (index % 2 == 0) LauncherSnapshotTag.MANUAL else LauncherSnapshotTag.PRE_RESTORE
            snapshot(
                "20260102_0000" + index.toString().padStart(2, '0') + "_" + tag.id,
                tag,
                index.toLong(),
            )
        }
        val evicted2 = LauncherBackupUtils.planEvictions(autosFar + many)
        assertEquals(4, evicted2.size)
        assertEquals(2, evicted2.count { it.tag == LauncherSnapshotTag.AUTO })
        // 剩下 2 份是时间最早的 manual/pre_restore
        assertEquals(listOf("20260102_000000_manual", "20260102_000001_pre_restore"), evicted2.drop(2).map { it.name })
    }

    @Test
    fun planEvictionsHandlesDegenerateLimits() {
        val list = (0 until 5).map {
            snapshot("20260101_00000" + it + "_manual", LauncherSnapshotTag.MANUAL, it.toLong())
        }
        assertTrue(LauncherBackupUtils.planEvictions(list, limit = 0).isEmpty())
        assertTrue(LauncherBackupUtils.planEvictions(emptyList()).isEmpty())
        // limit = 1 时超限 4 份，全部是 manual，按时间从旧到新删
        val evicted = LauncherBackupUtils.planEvictions(list, limit = 1)
        assertEquals(4, evicted.size)
        assertEquals(listOf("20260101_000000_manual", "20260101_000001_manual"), evicted.take(2).map { it.name })
    }

    @Test
    fun planEvictionsNeverDeletesExcludedSnapshot() {
        // 21 份（20 份 auto + 1 份 pre_restore），最旧的两份是 auto
        val autos = (0 until 20).map {
            snapshot("20260101_0000" + (10 + it) + "_auto", LauncherSnapshotTag.AUTO, it.toLong())
        }
        val target = snapshot("20260101_000100_manual", LauncherSnapshotTag.MANUAL, 100L)
        val all = autos + target
        assertEquals(21, all.size)

        // 不禁删时：删掉最旧的 auto
        val plain = LauncherBackupUtils.planEvictions(all)
        assertEquals(listOf("20260101_000010_auto"), plain.map { it.name })

        // 把最旧的 auto 设为受保护（模拟 restore 正在用的目标快照）→ 它不能被选中，改删第二旧的 auto
        val guarded = LauncherBackupUtils.planEvictions(all, excluded = setOf("20260101_000010_auto"))
        assertEquals(1, guarded.size)
        assertEquals("20260101_000011_auto", guarded.first().name)

        // 受保护的恰好是最旧的 auto，且超限 2 份 → 删第二、第三旧的 auto，仍然不含受保护的
        val guarded2 = LauncherBackupUtils.planEvictions(
            all + snapshot("20260101_000101_auto", LauncherSnapshotTag.AUTO, 101L),
            excluded = setOf("20260101_000010_auto"),
        )
        assertEquals(2, guarded2.size)
        assertFalse(guarded2.any { it.name == "20260101_000010_auto" })

        // 全部被排除时什么都不删（宁可暂时超限，也不能删掉正在恢复的那份）
        val allExcluded = all.map { it.name }.toSet()
        assertTrue(LauncherBackupUtils.planEvictions(all, excluded = allExcluded).isEmpty())
    }

    // ---------------- 自动备份周期 ----------------

    @Test
    fun intervalHoursFallsBackToDefault() {
        assertEquals(6, LauncherBackupPrefs.intervalHours("6"))
        assertEquals(48, LauncherBackupPrefs.intervalHours(" 48 "))
        assertEquals(LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS, LauncherBackupPrefs.intervalHours(null))
        assertEquals(LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS, LauncherBackupPrefs.intervalHours(""))
        assertEquals(LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS, LauncherBackupPrefs.intervalHours("7"))
        assertEquals(LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS, LauncherBackupPrefs.intervalHours("-6"))
    }

    // ---------------- 隐藏应用状态 ----------------

    @Test
    fun hiddenKeyRoundTrip() {
        assertEquals("com.a#0", LauncherBackupUtils.hiddenKey("com.a", 0))
        assertEquals("com.a#0", LauncherBackupUtils.hiddenKey("com.a", 0))
        val app = LauncherBackupUtils.parseHiddenKey("com.a.b#10")
        assertEquals("com.a.b", app?.packageName)
        assertEquals(10, app?.userId)
        // 包名里带 # 时按最后一个 # 切分
        assertEquals("weird#name", LauncherBackupUtils.parseHiddenKey("weird#name#3")?.packageName)
        assertNull(LauncherBackupUtils.parseHiddenKey(null))
        assertNull(LauncherBackupUtils.parseHiddenKey(""))
        assertNull(LauncherBackupUtils.parseHiddenKey("com.a"))
        assertNull(LauncherBackupUtils.parseHiddenKey("#0"))
        assertNull(LauncherBackupUtils.parseHiddenKey("com.a#"))
        assertNull(LauncherBackupUtils.parseHiddenKey("com.a#x"))
    }

    @Test
    fun parseHiddenPrefsXmlKeepsRawRank() {
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <int name="com.a#0" value="0" />
                <int name="com.b#0" value="2" />
                <int name="com.c#10" value="-1" />
                <string name="not_an_int">x</string>
                <boolean name="flag" value="true" />
                <int name="no_user_id" value="1" />
            </map>
        """.trimIndent()
        val parsed = LauncherBackupUtils.parseHiddenPrefsXml(xml)
        // 值必须原样保留（rank 被归一化会打乱隐藏列表顺序），-1 也不能丢
        assertEquals(0, parsed["com.a#0"])
        assertEquals(2, parsed["com.b#0"])
        assertEquals(-1, parsed["com.c#10"])
        assertFalse(parsed.containsKey("no_user_id"))
        assertEquals(3, parsed.size)
        assertTrue(LauncherBackupUtils.parseHiddenPrefsXml(null).isEmpty())
        assertTrue(LauncherBackupUtils.parseHiddenPrefsXml("not xml").isEmpty())
    }

    @Test
    fun planHiddenSyncDiffsBothDirections() {
        val snapshot = mapOf("com.a#0" to 0, "com.b#0" to 1, "com.c#0" to 2)
        val current = mapOf("com.a#0" to 0, "com.d#0" to 0)
        val plan = LauncherBackupUtils.planHiddenSync(snapshot, current)
        // 快照有、当前没有 → 需要隐藏
        assertEquals(listOf("com.b", "com.c"), plan.toHide.map { it.packageName })
        // 当前有、快照没有 → 需要取消隐藏
        assertEquals(listOf("com.d"), plan.toUnhide.map { it.packageName })
        assertEquals(0, plan.toUnhide.first().userId)

        val same = LauncherBackupUtils.planHiddenSync(snapshot, snapshot)
        assertTrue(same.toHide.isEmpty())
        assertTrue(same.toUnhide.isEmpty())

        // 空快照 → 全部取消隐藏；空当前 → 全部隐藏
        assertEquals(3, LauncherBackupUtils.planHiddenSync(snapshot, emptyMap()).toHide.size)
        assertEquals(3, LauncherBackupUtils.planHiddenSync(emptyMap(), snapshot).toUnhide.size)
    }
}
