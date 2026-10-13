package com.luckyzyx.luckytool.hook.scopes.opluskeyboard

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge

/**
 * 强制启用小布输入法的液态玻璃模糊。
 *
 * 宿主原本要求同时满足：系统「质感模糊」开关已打开（`system_material_blur_enable` == 1）、
 * 当前输入应用命中白名单、非折叠屏展开态、OS 版本 >= 40，并且键盘使用内置默认主题 + 跟随系统深浅色。
 * 这些条件最终全部收敛到总闸 `f.b()`：键盘内所有模糊渲染分支（容器背景模糊 delegate、
 * 各 view 在 `*Blur` 资源之间的二选一、ImeService 的窗口标记）都以它为唯一总闸。
 *
 * 总闸类里的 `b()`（模糊总开关）与 `c()`（主题条件）都是「无参 + 返回 boolean」，`l.i()`
 * （系统「质感模糊」开关，`ImeApp` 与设置观察者据此驱动 `f11731a` 这个 LiveData）同样如此。
 * 这些类名/方法名都是混淆名，ROM 更新后可能变化，所以用 DexKit 按稳定特征定位：
 * - `c()` 是宿主中唯一使用 `key_day_night_mode` 的无参 boolean 方法，命中它即可拿到总闸类；
 * - `l.i()` 是唯一同时使用 `system_material_blur_enable` 与日志标签 `SettingsPrefs` 的无参 boolean 方法。
 * 定位到类后按「无参 + 返回 boolean」结构 hook，不再依赖具体方法名。
 *
 * 模糊强度由 `ViewRootManager.setBlurRadius()` 决定（宿主固定请求 150，0 表示关闭），
 * 模块设置里的滑条只改写这里的正数入参。`ViewRootManager` 属于框架而不是宿主 dex，
 * 名字不受宿主混淆影响，仍按类名定位。
 *
 * 刻意不碰 `utils.h.j()`（折叠判断被分屏键盘、悬浮工具栏复用，`b()` 拦截后已无需它）与
 * `reflect.a.v()`（版本号还喂给 appcompat、应用更新等处，改它风险大于收益）。
 */
@Obfuscate
class ForceEnableOplusKeyboardBlur(private val dexKitBridge: DexKitBridge) : YukiBaseHooker() {

    companion object {
        /** 宿主内置的模糊半径，作为自定义项的默认值 */
        private const val DEFAULT_BLUR_RADIUS = 150
    }

    override fun onHook() {
        //自定义模糊强度
        val blurRadius =
            preferences(ModulePrefs).getInt("oplus_keyboard_blur_radius", DEFAULT_BLUR_RADIUS)

        //Source f
        // 模糊总闸类的定位特征：其中只有 c() 是无参、返回 boolean 且使用 key_day_night_mode 的方法
        val gateClass = dexKitBridge.findMethod {
            matcher {
                paramCount(0)
                returnType(classOf<Boolean>())
                usingStrings("key_day_night_mode")
            }
        }.checkDataList("ForceEnableOplusKeyboardBlur Gate").single().className

        gateClass.toClass().resolve().apply {
            // b() 与 c()：该类里只有这两个无参 boolean 方法，命中即全部返回 true
            method { emptyParameters(); returnType = Boolean::class }.interceptAll {
                true
            }
        }

        //Source SettingsPrefs
        // 系统「质感模糊」开关，驱动 f11731a 这个 LiveData
        val materialBlurSetting = dexKitBridge.findMethod {
            matcher {
                paramCount(0)
                returnType(classOf<Boolean>())
                usingStrings("system_material_blur_enable", "SettingsPrefs")
            }
        }.checkDataList("ForceEnableOplusKeyboardBlur MaterialBlurSetting").single()

        materialBlurSetting.className.toClass().resolve().apply {
            firstMethod { name = materialBlurSetting.methodName; emptyParameters() }
                .hook { intercept(true) }
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
