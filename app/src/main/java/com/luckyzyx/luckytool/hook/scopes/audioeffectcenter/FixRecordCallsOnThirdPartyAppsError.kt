package com.luckyzyx.luckytool.hook.scopes.audioeffectcenter

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.luckyzyx.luckytool.hook.core.*

object FixRecordCallsOnThirdPartyAppsError : Hooker {
    private const val SpatializerDefine = "com.oplus.audio.effectcenter.manager.SpatializerDefine"
    override fun onHook() {
        //Source com.oplus.audio.effectcenter.manager.SpatializerManager
        "com.oplus.audio.effectcenter.manager.SpatializerManager".toClassOrNull()?.resolve()
            ?.apply {
                firstMethod { name = "setSpkVolParam" }.hook {
                    before {
                        val level = args().first().cast<Int>() ?: 0
                        val mSpatializerMode = firstField {
                            name = "mSpatializerMode"
                        }.of(instance).get<AtomicBoolean>() ?: return@before
                        val mSpatializerSpkVol = firstField {
                            name = "mSpatializerSpkVol"
                        }.of(instance).get<AtomicInteger>() ?: return@before
                        val mSpatDeviceManager = firstField {
                            name = "mSpatDeviceManager"
                        }.of(instance).get() ?: return@before
                        val getDeviceForMusicStream = mSpatDeviceManager.asResolver().firstMethod {
                            name = "getDeviceForMusicStream"
                        }.invoke<Int>() ?: return@before
                        if (level == mSpatializerSpkVol.get()) return@before
                        if (getDeviceForMusicStream != 2 && mSpatializerMode.get()) return@before
                        val index = SpatializerDefine.toClass().resolve().firstField {
                            name = "PARAM_SET_SPAT_VOLUME_INDEX"
                        }.get<Int>()
                        firstMethod { name = "setParameterImp";parameterCount = 3 }.of(instance)
                            .invoke(index, level, mSpatializerSpkVol.get())
                        result = null
                    }
                }
            }
    }
}