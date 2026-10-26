package com.luckyzyx.luckytool.hook.scopes.android

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.ModulePrefs
import java.util.concurrent.atomic.AtomicBoolean
import com.luckyzyx.luckytool.hook.core.*

/** 恢复 APK 的原生默认应用选择，不改写 Intent、调用方身份或 URI 授权。 */
object AllowThirdPartyInstaller : Hooker {
    override fun onHook() {
        val enabled = AtomicBoolean(
            prefs(ModulePrefs).getBoolean(
                InstallerRoutingPolicy.PREFERENCE_KEY, false
            )
        )
        Channel.wait<Boolean>(InstallerRoutingPolicy.PREFERENCE_KEY) { enabled.set(it) }

        //Source OplusDefaultAppPolicyManager
        "com.android.server.pm.OplusDefaultAppPolicyManager".toClass().resolve().apply {
            firstMethod {
                name = "getForceAppBeforeChooseBestActivity"
                parameterCount = 2
            }.replaceAny {
                if (enabled.get() && InstallerRoutingPolicy.matches(args(0).any() as? Intent)) null
                else callOriginal()
            }
            firstMethod {
                name = "getDefaultAppAfterChooseBestActivity"
                parameterCount { it in 3..4 }
            }.replaceAny {
                if (!enabled.get()) return@replaceAny callOriginal()
                val intentIndex = method.parameterTypes.indexOf(classOf<Intent>())
                val resultIndex = method.parameterTypes.indexOf(classOf<ResolveInfo>())
                // 保留系统按用户偏好得到的结果；null 继续进入原生选择界面。
                if (intentIndex >= 0 && resultIndex >= 0 &&
                    InstallerRoutingPolicy.matches(args(intentIndex).any() as? Intent)
                ) args(resultIndex).any() as? ResolveInfo
                else callOriginal()
            }
            firstMethod {
                name = "isGotDefaultAppBeforeAddPreferredActivity"
                parameterCount = 5
            }.replaceAny {
                if (enabled.get() && InstallerRoutingPolicy.matches(args(0).any() as? IntentFilter))
                    false
                else callOriginal()
            }
        }
    }
}
