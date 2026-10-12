package com.luckyzyx.luckytool.hook.scopes.opluskeyboard

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import org.lsposed.lsparanoid.Obfuscate

/**
 * 强制启用小布输入法的液态玻璃模糊。
 *
 * 宿主原本要求同时满足：系统「质感模糊」开关已打开（`system_material_blur_enable` == 1）、
 * 当前输入应用命中白名单（`d.f13652b`）、非折叠屏展开态、OS 版本 >= 40，
 * 并且键盘使用内置默认主题 + 跟随系统深浅色。这些条件最终全部收敛到 `AbstractC0864f.b()`：
 * 键盘内所有模糊渲染分支（容器背景模糊 delegate、各 view 在 `*Blur` 资源之间的二选一、
 * ImeService 的窗口标记）都以它为唯一总闸，所以改写它的返回值即可一次性解除全部限制。
 *
 * 另两个 hook 点是让状态自洽，而不是模糊本身所需：
 * - `c()` 是主题条件，`b()` 之外仍被 `s.a()` 直接调用；
 * - `l.i()` 是系统开关，`ImeApp` / `system_material_blur_enable` 观察者据此驱动 `f11731a`
 *   这个 LiveData，伪造之后告警/观察者路径不会出现「总闸为真但状态为假」的矛盾。
 *
 * 刻意不碰 `utils.h.j()`（折叠判断被分屏键盘、悬浮工具栏复用，`b()` 拦截后已无需它）与
 * `reflect.a.v()`（版本号还喂给 appcompat、应用更新等处，改它风险大于收益）。
 */
@Obfuscate
object ForceEnableOplusKeyboardBlur : YukiBaseHooker() {
    override fun onHook() {
        //Source AbstractC0864f
        "com.oplus.keyboard.base.util.AbstractC0864f".toClassOrNull()?.resolve()?.apply {
            // 模糊总开关：OS 版本 + 系统开关 + 白名单 + 主题 + 折叠态全在此收敛
            firstMethodOrNull { name = "b"; emptyParameters() }?.hook { intercept(true) }
            // 主题条件（内置默认主题且跟随系统），让总闸之外的调用方也保持一致
            firstMethodOrNull { name = "c"; emptyParameters() }?.hook { intercept(true) }
        }
        //Source l
        // 系统「质感模糊」开关，驱动 f11731a 这个 LiveData
        "com.oplus.keyboard.base.data.l".toClassOrNull()?.resolve()?.apply {
            firstMethodOrNull { name = "i"; emptyParameters() }?.hook { intercept(true) }
        }
    }
}
