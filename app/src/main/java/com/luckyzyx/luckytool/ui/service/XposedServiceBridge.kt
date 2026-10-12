package com.luckyzyx.luckytool.ui.service

import android.content.SharedPreferences
import com.luckyzyx.luckytool.utils.LogUtils
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 模块 App 侧接入 libxposed service 的唯一入口：
 * - 绑定成功后持有 [XposedService]，并把激活态发布为 [isModuleActive]
 * - [preferences] 返回 remote prefs（宿主进程 YukiHookPreferences 的同一数据源），
 *   绑定不可用（未激活/无框架）时回落 null，由调用方走本地 prefs
 *
 * 激活检测是「观察」而不是「问一次」：框架经 XposedProvider 把 service binder 下发到本进程，
 * 绑定/断开都只经 [XposedServiceHelper] 回调（绑定是异步的，若在 [init] 注册监听前就已下发完毕，
 * registerListener 会立即把 onServiceBind 重放给我们）。所以 UI 侧要等 [isModuleActive] 出结果再读 prefs，
 * 等待期间只组合加载层（见 ActivationLoadingLayer），检测到激活即取消。
 */
object XposedServiceBridge {

    private const val TAG = "Libxposed Service Bridge"

    @Volatile
    private var initialized = false

    @Volatile
    var xposedService: XposedService? = null
        private set

    private val _isModuleActive = MutableStateFlow(false)

    /**
     * 模块激活态：框架服务已绑定且 scope 非空。
     * false 同时覆盖「未激活」与「绑定还没完成」（异步），调用方按超时区分两者。
     */
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    fun init() {
        if (initialized) return
        initialized = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                updateActiveState()
                LogUtils.d(
                    TAG,
                    "onServiceBind",
                    "framework ${service.frameworkName} API ${service.apiVersion}",
                    true
                )
            }

            override fun onServiceDied(service: XposedService) {
                xposedService = null
                updateActiveState()
                LogUtils.w(TAG, "onServiceDied", "framework service disconnected", true)
            }
        })
    }

    /** scope 是 Binder 调用：框架进程异常时会抛，读失败按未激活处理并记日志 */
    private fun updateActiveState() {
        val service = xposedService
        _isModuleActive.value = service != null && runCatching { service.scope.isNotEmpty() }
            .onFailure { LogUtils.e(TAG, "updateActiveState", "read module scope failed: $it", true) }
            .getOrDefault(false)
    }

    fun preferences(name: String): SharedPreferences? =
        xposedService?.getRemotePreferences(name)
}

/**
 * 等激活检测出结果：检测到激活返回 true，[timeoutMillis] 内仍未激活返回 false。
 *
 * UI 侧（MainActivity）靠它决定「继续显示加载层」还是「弹未激活阻塞门」。
 * [first] 只在值变化时唤醒，不轮询；超时返回 false 不代表就此结束——调用方仍继续观察，
 * 激活迟到到达时撤掉阻塞门。
 */
internal suspend fun StateFlow<Boolean>.awaitActivation(timeoutMillis: Long): Boolean =
    withTimeoutOrNull(timeoutMillis) { first { it } } != null
