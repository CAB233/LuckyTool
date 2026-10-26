package com.luckyzyx.luckytool.hook.scopes.settings

import android.content.Context
import android.util.ArrayMap
import android.util.ArraySet
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.createInstance
import com.luckyzyx.luckytool.data.DarkModeInfo
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.safeOfNull
import kotlinx.serialization.json.Json
import org.luckypray.dexkit.DexKitBridge
import java.io.InputStream
import java.io.Reader
import java.util.concurrent.atomic.AtomicBoolean
import com.luckyzyx.luckytool.hook.core.*

class DarkModeList(val dexKitBridge: DexKitBridge) : Hooker {

    var isEnable = false
    val list = ArraySet<DarkModeInfo>()

    fun loadData() {
        isEnable = prefs(ModulePrefs).getBoolean("dark_mode_list_enable", false)
        Channel.wait<Boolean>("dark_mode_list_enable") {
            isEnable = it
            XLog.debug("update dark mode configs status -> $it")
        }

        list.clear()
        val enabled = prefs(ModulePrefs).getStringSet("dark_mode_support_list", ArraySet())
        list.addAll(enabled.mapNotNull {
            safeOfNull { Json.decodeFromString<DarkModeInfo>(it) }
        })
        Channel.wait("dark_mode_support_list") {
            val new = prefs(ModulePrefs).getStringSet("dark_mode_support_list", ArraySet())
            XLog.debug("update dark mode whitelist configs -> ${list.size} | ${new.size}")
            list.clear()
            list.addAll(new.mapNotNull {
                safeOfNull { Json.decodeFromString<DarkModeInfo>(it) }
            })
        }
        XLog.debug("init dark mode configs success")
    }

    override fun onHook() {
        loadData()

        //Source DarkModeFileUtils
        dexKitBridge.findClass {
            matcher {
                fields {
                    addForType(classOf<Any>())
                    addForType(classOf<AtomicBoolean>())
                    addForType(classOf<Map<*,*>>())
                }
                methods {
                add { paramTypes(classOf<Reader>()) }
                    add { paramTypes(classOf<InputStream>()) }
                }
                usingStrings("DarkModeFileUtils")
            }
        }.apply {
            checkDataList("DarkModeList")

            val clazz = single().name.toClass()
            val appEntity = clazz.classes[0]?.name?.toClassOrNull() ?: return
            clazz.resolve().apply {
                firstMethod { parameters(Context::class, Map::class) }.hook {
                    after {
                        if (!isEnable) return@after
                        val dataMap = ArrayMap<String, Any>()
                        list.forEach {
                            if (it.curType == 0) dataMap[it.packName] =
                                appEntity.createInstance(isPublic = false)
                            else dataMap[it.packName] =
                                appEntity.createInstance(0L, 0, it.curType, 0, isPublic = false)
                        }
                        firstField { type = Map::class }.set(dataMap.toMap())
                    }
                }
            }
        }
    }
}