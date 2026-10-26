package com.luckyzyx.luckytool.ui.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 激活检测的等待语义：MainActivity.checkXposed 靠它决定「继续显示加载层」还是「弹未激活阻塞门」。
 *
 * 三点必须成立：已激活立即返回、绑定迟到但未超时算激活、一直未激活则超时返回 false。
 * 超时之后仍继续观察、激活迟到时撤掉阻塞门的那部分在 Activity 里，本测试不覆盖。
 */
class XposedServiceBridgeActivationTest {

    @Test
    fun `already active returns without waiting`() = runBlocking {
        val active = MutableStateFlow(true)
        val start = System.currentTimeMillis()
        assertTrue("已激活必须返回 true", active.awaitActivation(400))
        val cost = System.currentTimeMillis() - start
        assertTrue("已激活不该等满超时，实际等待 ${cost}ms", cost < 200)
    }

    @Test
    fun `activation arriving before the timeout wins`() = runBlocking {
        val active = MutableStateFlow(false)
        launch {
            delay(30)
            active.value = true
        }
        assertTrue("绑定迟到但未超时，必须算激活", active.awaitActivation(5_000))
    }

    @Test
    fun `staying inactive ends with the timeout`() = runBlocking {
        val active = MutableStateFlow(false)
        assertFalse("一直未激活必须超时返回 false", active.awaitActivation(50))
    }
}
