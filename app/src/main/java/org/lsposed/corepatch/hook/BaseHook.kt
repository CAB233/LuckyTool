package org.lsposed.corepatch.hook

import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.corepatch.Config
import com.luckyzyx.luckytool.hook.core.*

/**
 * 上游 CorePatch 各子 hook 的基类。
 *
 * YukiHook 的钩子入口，由 HookCorePatch 逐个装载；出错时由 Hooker 统一记录日志。
 */
abstract class BaseHook : Hooker {
    open val name = "BaseHook"

    final override fun onHook() {
        //LuckyTool：子 hook 读取配置前先绑定 ModulePrefs
        Config.bind(prefs(ModulePrefs))
        hook()
    }

    open fun hook() {

    }
}
