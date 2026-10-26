package com.luckyzyx.luckytool.hook.scopes.android

import android.util.SparseArray
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookGMSRestrict : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        val isEnable = prefs(ModulePrefs).getBoolean("remove_gms_usage_restrictions", false)
        if (!isEnable) return

        loadHooker(GMSRestrictCommon)

        if (osCode > 30) loadHooker(GMSRestrict)
        else loadHooker(GMSRestrictV13)
    }

    object GMSRestrictCommon : Hooker {
        override fun onHook() {
            //Source OplusAppStartupManager -> OplusStartupStrategy -> google_restric_info
            "com.android.server.am.OplusAppStartupManager\$OplusStartupStrategy".toClass().resolve()
                .apply {
                    firstMethod { name = "isGoogleRestricInfoOn" }.hook {
                        replaceTo(false)
                    }
                }

            //Source OplusHansDBConfig -> sys_elsa_config_list -> Athena
            "com.android.server.hans.OplusHansDBConfig".toClass().resolve().apply {
                method { name = "updateManagedMap" }.hookAll {
                    after {
                        firstField { name = "mGMSList" }.of(instance).get<SparseArray<Any>>()
                            ?.clear()
                    }
                }
                firstMethod { name = "updateTargetList" }.hook {
                    after {
                        firstField { name = "mGMSList" }.of(instance).get<SparseArray<Any>>()
                            ?.clear()
                    }
                }
            }
        }
    }

    object GMSRestrict : Hooker {
        override fun onHook() {
            //Source OplusBgSceneManager -> google_restric_info
            "com.android.server.hans.scene.OplusBgSceneManager".toClass().resolve().apply {
                firstMethod { name = "setGmsRestricted" }.hook {
                    before {
                        args().first().set(false)
                    }
                }
                firstMethod { name = "isGmsRestricted" }.hook {
                    replaceTo(false)
                }
                firstMethod { name = "registerGmsRestrictObserver" }.hook {
                    intercept()
                }
            }
        }
    }

    object GMSRestrictV13 : Hooker {
        override fun onHook() {
            //Source OplusHansManager -> HansConfig -> google_restric_info
            "com.android.server.am.OplusHansManager\$HansConfig".toClass().resolve().apply {
                firstMethod { name = "setGmsRestricted" }.hook {
                    before {
                        args().first().set(false)
                    }
                }
                firstMethod { name = "isGmsRestricted" }.hook {
                    replaceTo(false)
                }
            }
            //Source OplusHansManager -> HansTrigger -> google_restric_info
            "com.android.server.am.OplusHansManager\$HansTrigger".toClass().resolve().apply {
                firstMethod { name = "registerGmsRestrictObserver" }.hook {
                    intercept()
                }
            }
        }
    }
}