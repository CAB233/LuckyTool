package com.luckyzyx.luckytool.hook.scopes.android

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClassOrNull
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate
import java.util.concurrent.atomic.AtomicBoolean

/** 恢复 APK 的原生默认应用选择，不改写 Intent、调用方身份或 URI 授权。 */
@Obfuscate
object AllowThirdPartyInstaller : YukiBaseHooker() {
    override fun onHook() {
        val enabled = AtomicBoolean(preferences(ModulePrefs).getBoolean(InstallerRoutingPolicy.PREFERENCE_KEY, false))
        val policyClass = "com.android.server.pm.OplusDefaultAppPolicyManager".toClassOrNull() ?: return
        val policy = policyClass.resolve()
        val forceChoice = policy.firstMethodOrNull {
            name = "getForceAppBeforeChooseBestActivity"
            parameterCount = 2
        }
        val defaultChoice = policy.firstMethodOrNull {
            name = "getDefaultAppAfterChooseBestActivity"
            parameterCount { it in 3..4 }
        }
        val preferredChoice = policy.firstMethodOrNull {
            name = "isGotDefaultAppBeforeAddPreferredActivity"
            parameterCount = 5
        }
        if (forceChoice == null || defaultChoice == null || preferredChoice == null) {
            YLog.warn("Installer routing policy is unavailable on this system")
            return
        }
        dataChannel.wait<Boolean>(InstallerRoutingPolicy.PREFERENCE_KEY) { enabled.set(it) }

        forceChoice.hook {
            before {
                if (enabled.get() && InstallerRoutingPolicy.matches(arg(0).get<Intent>())) result = null
            }
        }
        defaultChoice.hook {
            before {
                if (!enabled.get()) return@before
                val intentIndex = method.parameterTypes.indexOf(Intent::class.java)
                val resultIndex = method.parameterTypes.indexOf(ResolveInfo::class.java)
                if (intentIndex >= 0 && resultIndex >= 0 &&
                    InstallerRoutingPolicy.matches(arg(intentIndex).get<Intent>())
                ) {
                    // 保留系统按用户偏好得到的结果；null 继续进入原生选择界面。
                    result = arg(resultIndex).get<ResolveInfo>()
                }
            }
        }
        preferredChoice.hook {
            before {
                if (enabled.get() && InstallerRoutingPolicy.matches(arg(0).get<IntentFilter>())) result = false
            }
        }
    }
}
