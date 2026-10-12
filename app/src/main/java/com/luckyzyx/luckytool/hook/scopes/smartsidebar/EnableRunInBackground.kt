package com.luckyzyx.luckytool.hook.scopes.smartsidebar

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.Modifiers
import com.highcapable.kavaref.extension.VariousClass
import com.highcapable.kavaref.extension.createInstance
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.injectModuleResources
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.utils.IntentUtils
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.utils.startMirageWindow
import org.lsposed.lsparanoid.Obfuscate

@Obfuscate
object EnableRunInBackground : YukiBaseHooker() {

    override fun onHook() {
        val osCode = getOSVersionCode

        val targetTool = VariousClass(
            "com.oplus.smartsidebar.panelview.edgepanel.data.entrybeans.models.tools.BackgroundRunTool", //C15-
            "com.oplus.smartsidebar.panelview.edgepanel.data.entrybeans.models.tools.GTModelTool", //C16
            "com.oplus.smartsidebar.panelview.edgepanel.data.entrybeans.models.tools.CleanStorageTool" //C16.1
        ).toClass()

        //Source BackgroundRunTool or GTModelTool
        targetTool.resolve().apply {
            if (targetTool.simpleName != "BackgroundRunTool") {
                firstMethod { name = "getIconRes" }.intercept {
                    val context = firstField { type = Context::class; superclass() }.of(instance)
                        .get<Context>() ?: return@intercept proceed()
                    context.injectModuleResources()
                    R.drawable.background_run
                }
                firstMethod { name = "getNameRes" }.intercept {
                    val context = firstField { type = Context::class; superclass() }.of(instance)
                        .get<Context>() ?: return@intercept proceed()
                    context.injectModuleResources()
                    R.string.run_in_background
                }
            }
            firstMethod { name = "handle" }.hook {
                before {
                    if (osCode >= 34) {
                        startMirageWindow(null)
                    } else {
                        val context = firstField { type = Context::class; superclass() }
                            .of(instance).get<Context>() ?: return@before
                        IntentUtils(context).startBackgroundRunServiceV14()
                    }
                    result = null
                }
            }
            firstMethod { name = "isToolAvailable" }.hook {
                intercept(true)
            }
        }

        //Source ToolEntryHelper
        "com.oplus.smartsidebar.panelview.edgepanel.data.entrybeans.ToolEntryHelper".toClass()
            .resolve().apply {
                firstMethod { name = "loadTools" }.hook {
                    after {
                        val context =
                            firstField { type = Context::class; superclass() }.of(instance)
                                .get<Context>() ?: return@after
                        val tool = targetTool.createInstance(context, isPublic = false)
                        firstMethod { name = "put" }.of(instance).invoke(tool)
                    }
                }
            }

        //Source App
        //C17 起 com.coloros.common.App 提供 setStaticContext(Context)/getIconContext()；
        //C16 及以下没有这两个方法，只能在其静态 Context 字段上按需注入模块资源。
        val appResolver = "com.coloros.common.App".toClassOrNull()?.resolve()
        val setStaticContext = appResolver?.firstMethodOrNull {
            name = "setStaticContext"
            parameters(Context::class)
        }
        if (setStaticContext != null) {
            setStaticContext.intercept {
                val context = firstArg() as? Context
                context?.injectModuleResources()
                proceed()
            }
            appResolver?.firstMethodOrNull {
                name = "getIconContext"
                emptyParameters()
                returnType = Context::class
            }?.intercept {
                val context = proceed() as? Context
                context?.injectModuleResources()
                context
            }
        } else {
            //Source ImageDataHandleImpl
            "com.oplus.smartsidebar.panelview.edgepanel.data.viewdatahandlers.ImageDataHandleImpl".toClassOrNull()
                ?.resolve()?.firstMethodOrNull { name = "getToolAppIcon" }?.hook {
                    before {
                        "com.coloros.common.App".toClassOrNull()?.resolve()?.firstFieldOrNull {
                            modifiers(Modifiers.STATIC)
                            type = Context::class
                        }?.get<Context>()?.injectModuleResources()
                    }
                }
        }
    }
}