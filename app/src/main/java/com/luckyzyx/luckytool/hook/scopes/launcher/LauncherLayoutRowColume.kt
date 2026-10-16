package com.luckyzyx.luckytool.hook.scopes.launcher

import android.util.Pair
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.ArrayClass
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.utils.DexkitUtils
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

/**
 * 安全执行锚点解析
 *
 * 解析数量不等于 1 由 [checkDataList] 打印错误日志并返回 null, 解析过程本身抛出的异常在这里打印日志后吞掉,
 * 两者都由调用处直接跳过, 不会导致宿主崩溃
 */
private fun <T> safeResolve(instance: String, block: () -> T): T? = runCatching(block).onFailure {
    YLog.error(msg = "LauncherLayoutRowColume: $instance", e = it, tag = DexkitUtils.tag)
}.getOrNull()

@Obfuscate
class LauncherLayoutRowColume(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 40) loadHooker(LayoutRowColume(dexKitBridge))
        else if (osCode >= 37) loadHooker(LayoutRowColumeV16)
        else loadHooker(LayoutRowColumeV15)
    }

    @Obfuscate
    class LayoutRowColume(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
        override fun onHook() {
            val maxRows = preferences(ModulePrefs).getInt("launcher_layout_max_rows", 6)
            val maxColumns = preferences(ModulePrefs).getInt("launcher_layout_max_columns", 4)

            //Source UiConfig (混淆类名逐版本变化, 用源文件名做锚点)
            //isSupportLayout(II)Z 校验行列数组合, getSupportLayout() 返回支持的组合列表
            safeResolve("UiConfig") {
                val uiConfig = dexKitBridge.findClass {
                    matcher { source("UiConfig.java") }
                }.apply { checkDataList("LauncherLayoutRowColume UiConfig") }.singleOrNull()
                    ?: return@safeResolve
                //同类中 static + 无参返回 ArrayList 的方法有多个, 只有被桌面布局设置弹窗适配器直接调用的那个是 getSupportLayout()
                val supportLayout = uiConfig.findMethod {
                    matcher {
                        modifiers(Modifier.STATIC)
                        paramCount(0)
                        returnType(classOf<ArrayList<*>>())
                        addCaller { declaredClass { source("ToggleBarDialogLayoutAdapter.kt") } }
                    }
                }.apply { checkDataList("LauncherLayoutRowColume getSupportLayout") }.singleOrNull()
                    ?: return@safeResolve
                //同类中 static + (II)Z 的方法有两个, 只有内部调用 getSupportLayout() 的那个是 isSupportLayout()
                val isSupportLayout = uiConfig.findMethod {
                    matcher {
                        modifiers(Modifier.STATIC)
                        paramTypes(classOf<Int>(), classOf<Int>())
                        returnType(classOf<Boolean>())
                        //被调方钉死到已解析的 UiConfig 类与形状: 这里同为 0 参同名的还有 x0.h()Z, 只按名字匹配会被误接受
                        addInvoke {
                            declaredClass { source("UiConfig.java") }
                            name(supportLayout.methodName)
                            paramCount(0)
                            returnType(classOf<ArrayList<*>>())
                        }
                    }
                }.apply { checkDataList("LauncherLayoutRowColume isSupportLayout") }.singleOrNull()
                    ?: return@safeResolve

                //返回自定义的行列数组合
                supportLayout.className.toClass().resolve().firstMethod {
                    name = supportLayout.methodName
                    emptyParameters()
                }.hook {
                    before {
                        result = ArrayList<Pair<Int, Pair<Int, Int>>>().apply {
                            for (col in 4..maxColumns) {
                                for (row in 6..maxRows) {
                                    add(Pair(col, Pair(row, row + 1)))
                                }
                            }
                        }
                    }
                }

                //放行所有行列数组合
                isSupportLayout.className.toClass().resolve().firstMethod {
                    name = isSupportLayout.methodName
                    parameters(Int::class, Int::class)
                    returnType = Boolean::class
                }.hook {
                    intercept(true)
                }
            }

            //Source IconFallenUtils (混淆类名逐版本变化, 用源文件名做锚点)
            //getLogicCellX(ItemInfo)I 计算桌面图标所在列数
            safeResolve("IconFallenUtils") {
                val iconFallenUtils = dexKitBridge.findClass {
                    matcher { source("IconFallenUtils.java") }
                }.apply { checkDataList("LauncherLayoutRowColume IconFallenUtils") }.singleOrNull()
                    ?: return@safeResolve
                //同类中 static + 单参返回 int 的只有这一个 (同名同参的 g(ItemInfo)Z 靠返回类型排除)
                val getLogicCellX = iconFallenUtils.findMethod {
                    matcher {
                        modifiers(Modifier.STATIC)
                        paramCount(1)
                        returnType(classOf<Int>())
                    }
                }.apply { checkDataList("LauncherLayoutRowColume getLogicCellX") }.singleOrNull()
                    ?: return@safeResolve

                //列数超过 4 时按 4 处理
                getLogicCellX.className.toClass().resolve().firstMethod {
                    name = getLogicCellX.methodName
                    parameterCount = 1
                    returnType = Int::class
                }.hook {
                    after {
                        val res = result<Int>() ?: return@after
                        if (res > 4) result = 4
                    }
                }
            }
        }
    }

    @Obfuscate
    object LayoutRowColumeV16 : YukiBaseHooker() {
        override fun onHook() {
            val maxRows = preferences(ModulePrefs).getInt("launcher_layout_max_rows", 6)
            val maxColumns = preferences(ModulePrefs).getInt("launcher_layout_max_columns", 4)

            //Source UiConfig
            "com.android.launcher.UiConfig".toClass().resolve().apply {
                firstMethod { name = "isSupportLayout" }.hook {
                    intercept(true)
                }
                firstMethod { name = "getSupportLayout" }.hook {
                    before {
                        result = ArrayList<Pair<Int, Pair<Int, Int>>>().apply {
                            for (col in 4..maxColumns) {
                                for (row in 6..maxRows) {
                                    add(Pair(col, Pair(row, row + 1)))
                                }
                            }
                        }
                    }
                }
            }

            //Source IconFallenUtils
            "com.android.launcher.iconfallen.IconFallenUtils".toClass().resolve().apply {
                firstMethod {
                    name = "getLogicCellX"
                    returnType = Int::class
                }.hook {
                    after {
                        val res = result<Int>() ?: return@after
                        if (res > 4) result = 4
                    }
                }
            }
        }
    }

    @Obfuscate
    object LayoutRowColumeV15 : YukiBaseHooker() {
        override fun onHook() {
            //Source UiConfig
            "com.android.launcher.UiConfig".toClass().resolve().apply {
                firstMethod { name = "isSupportLayout" }.hook {
                    intercept(true)
                }
            }
            val maxRows = preferences(ModulePrefs).getInt("launcher_layout_max_rows", 6)
            val maxColumns = preferences(ModulePrefs).getInt("launcher_layout_max_columns", 4)
            //Source ToggleBarLayoutAdapter
            "com.android.launcher.togglebar.adapter.ToggleBarLayoutAdapter".toClass().resolve()
                .apply {
                    firstMethod { name = "initToggleBarLayoutConfigs" }.hook {
                        before {
                            firstField {
                                name = "MIN_MAX_COLUMN"
                                type = ArrayClass(Int::class)
                            }.get<IntArray>()?.set(1, maxColumns)
                            firstField {
                                name = "MIN_MAX_ROW"
                                type = ArrayClass(Int::class)
                            }.get<IntArray>()?.set(1, maxRows)
                        }
                    }
                }
        }
    }
}
