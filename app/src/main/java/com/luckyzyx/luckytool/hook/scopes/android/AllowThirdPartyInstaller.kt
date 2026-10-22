package com.luckyzyx.luckytool.hook.scopes.android

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.intercept
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate
import java.util.concurrent.atomic.AtomicBoolean

/** 恢复 APK 的原生默认应用选择，不改写 Intent、调用方身份或 URI 授权。 */
@Obfuscate
object AllowThirdPartyInstaller : YukiBaseHooker() {
    override fun onHook() {
        val enabled = AtomicBoolean(
            preferences(ModulePrefs).getBoolean(
                InstallerRoutingPolicy.PREFERENCE_KEY, false
            )
        )
        dataChannel.wait<Boolean>(InstallerRoutingPolicy.PREFERENCE_KEY) { enabled.set(it) }

        //Source OplusDefaultAppPolicyManager
        "com.android.server.pm.OplusDefaultAppPolicyManager".toClass().resolve().apply {
            firstMethod {
                name = "getForceAppBeforeChooseBestActivity"
                parameterCount = 2
            }.intercept {
                if (enabled.get() && InstallerRoutingPolicy.matches(arg(0) as? Intent)) null
                else proceed()
            }
            firstMethod {
                name = "getDefaultAppAfterChooseBestActivity"
                parameterCount { it in 3..4 }
            }.intercept {
                if (!enabled.get()) return@intercept proceed()
                val intentIndex = method.parameterTypes.indexOf(classOf<Intent>())
                val resultIndex = method.parameterTypes.indexOf(classOf<ResolveInfo>())
                // 保留系统按用户偏好得到的结果；null 继续进入原生选择界面。
                if (intentIndex >= 0 && resultIndex >= 0 &&
                    InstallerRoutingPolicy.matches(arg(intentIndex) as? Intent)
                ) arg(resultIndex) as? ResolveInfo
                else proceed()
            }
            firstMethod {
                name = "isGotDefaultAppBeforeAddPreferredActivity"
                parameterCount = 5
            }.intercept {
                if (enabled.get() && InstallerRoutingPolicy.matches(arg(0) as? IntentFilter))
                    false
                else proceed()
            }
        }
    }
}
