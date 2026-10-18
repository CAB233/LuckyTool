package com.luckyzyx.luckytool.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 自动备份到期闸门的纯逻辑单测：只用时间戳算术，不落盘、不碰 Context。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherBackupGateTest {

    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    /** 淘汰函数只看名字 / 标签 / 时间戳，不依赖 app 的 LauncherSnapshot 具体类型 */
    private data class Candidate(
        override val name: String,
        override val tag: LauncherSnapshotTag,
        override val timeMillis: Long,
    ) : SnapshotCandidate

    @Test
    fun dueWhenNeverBackedUp() {
        assertTrue(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = 0L, lastAttempt = 0L, intervalHours = 24),
        )
    }

    @Test
    fun dueExactlyAtIntervalBoundary() {
        assertTrue(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 24 * hour, lastAttempt = now - 24 * hour, intervalHours = 24),
        )
    }

    @Test
    fun notDueBeforeInterval() {
        assertFalse(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 23 * hour, lastAttempt = now - 23 * hour, intervalHours = 24),
        )
        assertFalse(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - hour, lastAttempt = 0L, intervalHours = 24),
        )
    }

    @Test
    fun notDueBeforeIntervalEvenWhenAttemptIsOld() {
        // 节流只在到期之后起作用：上次尝试很久以前也不会把周期提前
        assertFalse(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 2 * hour, lastAttempt = now - 5 * hour, intervalHours = 24),
        )
    }

    @Test
    fun throttledWhenAttemptedWithinRetryGap() {
        assertFalse(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 25 * hour, lastAttempt = now - 29 * 60_000L, intervalHours = 24),
        )
    }

    @Test
    fun dueAgainAfterRetryGap() {
        assertTrue(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 25 * hour, lastAttempt = now - 31 * 60_000L, intervalHours = 24),
        )
    }

    @Test
    fun retryGapCanBeDisabledForOwnWakeUp() {
        // 这一次是我们自己按周期叫醒的，不需要再等 30 分钟节流
        assertTrue(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 25 * hour, lastAttempt = now - 60_000L, intervalHours = 24, retryGapMs = 0L),
        )
    }

    @Test
    fun invalidIntervalNeverDue() {
        assertFalse(LauncherBackupGate.autoBackupDue(now, 0L, 0L, intervalHours = 0))
        assertFalse(LauncherBackupGate.autoBackupDue(now, now - 100 * hour, 0L, intervalHours = -6))
    }

    @Test
    fun clockRolledBackCountsAsDue() {
        // 用户把时间调回去：不能让时间戳永远在未来把备份彻底卡死
        assertTrue(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now + 10 * 60_000L, lastAttempt = now + 10 * 60_000L, intervalHours = 24),
        )
        assertTrue(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 30 * hour, lastAttempt = now + 10 * 60_000L, intervalHours = 24),
        )
        // 一分钟以内的抖动不算回拨，仍按正常节流判定
        assertFalse(
            LauncherBackupGate.autoBackupDue(now, lastSuccess = now - 25 * hour, lastAttempt = now - 30_000L, intervalHours = 24),
        )
    }

    @Test
    fun planEvictionsOnlyNeedsNameTagAndTime() {
        val rows = listOf(
            Candidate("launcher_20260101_000000_auto", LauncherSnapshotTag.AUTO, 100L),
            Candidate("launcher_20260102_000000_auto", LauncherSnapshotTag.AUTO, 200L),
            Candidate("launcher_20260103_000000_manual", LauncherSnapshotTag.MANUAL, 300L),
        )
        val picked = LauncherBackupUtils.planEvictions(rows, limit = 1)
        assertEquals(
            listOf("launcher_20260101_000000_auto", "launcher_20260102_000000_auto"),
            picked.map { it.name },
        )
        assertTrue(LauncherBackupUtils.planEvictions(rows, limit = 3).isEmpty())
        assertEquals(
            listOf("launcher_20260102_000000_auto", "launcher_20260103_000000_manual"),
            LauncherBackupUtils.planEvictions(rows, limit = 1, excluded = setOf("launcher_20260101_000000_auto")).map { it.name },
        )
    }
}
