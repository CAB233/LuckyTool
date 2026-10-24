package com.luckyzyx.luckytool.ui.compose.special

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 锁定 Memc「Activity 选择」弹层的搜索语义。
 *
 * 背景：该弹层原先只列类名且没有搜索框，添加/修改配置时只能靠肉眼翻找；
 * 现在 label 与类名都参与匹配，且两侧统一 lowercase —— label 常以大写开头或为中文，
 * 若只 lowercase 一侧，"MainActivity" 这类输入就会漏掉自己的目标。
 * 空/纯空白查询返回全量（弹层打开时不做预过滤）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemcActivityOptionFilterTest {

    private val options = listOf(
        MemcActivityOption("com.example.app.MainActivity", "Example"),
        MemcActivityOption("com.example.app.SettingsActivity", "设置"),
        MemcActivityOption("com.example.app.PlayerActivity", "Video Player"),
    )

    @Test
    fun `blank query keeps every option`() {
        assertEquals(options, filterMemcActivityOptions(options, ""))
        assertEquals(options, filterMemcActivityOptions(options, "   "))
    }

    @Test
    fun `query matches the label`() {
        assertEquals(
            listOf(options[1]),
            filterMemcActivityOptions(options, "设置"),
        )
        assertEquals(
            listOf(options[2]),
            filterMemcActivityOptions(options, "video player"),
        )
    }

    @Test
    fun `query matches the class name ignoring case`() {
        assertEquals(
            listOf(options[0]),
            filterMemcActivityOptions(options, "mainactivity"),
        )
        assertEquals(
            listOf(options[0]),
            filterMemcActivityOptions(options, "MainActivity"),
        )
    }

    @Test
    fun `no match returns empty`() {
        assertEquals(emptyList<MemcActivityOption>(), filterMemcActivityOptions(options, "zzz"))
    }
}
