package com.luckyzyx.luckytool.hook.scopes.opluskeyboard

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate

/**
 * 强制启用小布输入法的液态玻璃模糊。
 *
 * 宿主原本要求同时满足：系统「质感模糊」开关已打开（`system_material_blur_enable` == 1）、
 * 当前输入应用命中白名单（`d.f13652b`）、非折叠屏展开态、OS 版本 >= 40，
 * 并且键盘使用内置默认主题 + 跟随系统深浅色。这些条件最终全部收敛到 `f.b()`（dump 里显示为 `AbstractC0864f`）：
 * 键盘内所有模糊渲染分支（容器背景模糊 delegate、各 view 在 `*Blur` 资源之间的二选一、
 * ImeService 的窗口标记）都以它为唯一总闸，所以改写它的返回值即可一次性解除全部限制。
 *
 * 另两个 hook 点是让状态自洽，而不是模糊本身所需：
 * - `c()` 是主题条件，`b()` 之外仍被 `s.a()` 直接调用；
 * - `l.i()` 是系统开关，`ImeApp` / `system_material_blur_enable` 观察者据此驱动 `f11731a`
 *   这个 LiveData，伪造之后告警/观察者路径不会出现「总闸为真但状态为假」的矛盾。
 *
 * 模糊强度由 `ViewRootManager.setBlurRadius()` 决定（宿主固定请求 150，0 表示关闭），
 * 模块设置里的滑条就改写这里的正数入参。
 *
 * 刻意不碰 `utils.h.j()`（折叠判断被分屏键盘、悬浮工具栏复用，`b()` 拦截后已无需它）与
 * `reflect.a.v()`（版本号还喂给 appcompat、应用更新等处，改它风险大于收益）。
 *
 * 类名/方法名是混淆名，不同机型 ROM 可能不同：这里刻意用 `toClass()` + `firstMethod()`，
 * 找不到时直接抛由框架记录到模块日志，便于确认是否生效，不做静默兜底。
 *
 * 注意 `f` 这个单字母名：jadx 把 dump 写到大小写不敏感的文件系统时，它与同包的 `F` 冲突，
 * 会被改名成 `AbstractC0864f`。那个名字只存在于反编译产物里，dex 中并不存在，
 * 写进 hook 只会让 `toClass()` 抛异常、整个 onHook 中断。改类名前先看 dump 开头的
 * `JADX INFO: renamed from:` 注释确认真实名字。
 */
@Obfuscate
object ForceEnableOplusKeyboardBlur : YukiBaseHooker() {

    /** 宿主内置的模糊半径，作为自定义项的默认值 */
    private const val DEFAULT_BLUR_RADIUS = 150

    override fun onHook() {
        //自定义模糊强度
        val blurRadius =
            preferences(ModulePrefs).getInt("oplus_keyboard_blur_radius", DEFAULT_BLUR_RADIUS)

        //Source f (jadx 里被改名成 AbstractC0864f，不要用后者)
        "com.oplus.keyboard.base.util.f".toClass().resolve().apply {
            // 模糊总开关：OS 版本 + 系统开关 + 白名单 + 主题 + 折叠态全在此收敛
            firstMethod { name = "b"; emptyParameters() }.hook { intercept(true) }
            // 主题条件（内置默认主题且跟随系统），让总闸之外的调用方也保持一致
            firstMethod { name = "c"; emptyParameters() }.hook { intercept(true) }
        }
        //Source l
        // 系统「质感模糊」开关，驱动 f11731a 这个 LiveData
        "com.oplus.keyboard.base.data.l".toClass().resolve().apply {
            firstMethod { name = "i"; emptyParameters() }.hook { intercept(true) }
        }
        //Source ViewRootManager
        // 模糊半径的唯一入口：宿主请求正数时才替换，保留 0 的关闭语义
        "com.oplus.view.ViewRootManager".toClass().resolve().apply {
            firstMethod { name = "setBlurRadius" }.hook {
                before {
                    val requested = args[0] as? Int ?: return@before
                    if (requested > 0 && requested != blurRadius) args[0] = blurRadius
                }
            }
        }
    }
}
