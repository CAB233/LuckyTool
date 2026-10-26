package com.luckyzyx.luckytool.hook.scopes.games

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.util.SparseIntArray
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class CompetitionModeSound(val dexKitBridge: DexKitBridge) : Hooker {
    val key = "remove_competition_mode_sound"
    override fun onHook() {
        //Source SoundPoolPlayManager -> competition_mode_sound
        dexKitBridge.findClass {
            matcher {
                fields {
                addForType(classOf<Context>())
                    addForType(classOf<Boolean>())
                    addForType(classOf<SoundPool>())
                    addForType(classOf<AudioManager>())
                    addForType(classOf<SparseIntArray>())
                }
                methods {
                    add {
                        paramCount(0)
                        returnType(Void.TYPE)
                    }
                    add {
                        paramTypes(classOf<Int>())
                        returnType(Void.TYPE)
                    }
                }
            }
        }.apply {
            checkDataList("CompetitionModeSound")
            single().name.toClass().resolve().apply {
                method { parameters(Int::class) }.hookAll {
                    before {
                        if ((args().first().cast<Int>() ?: 0) == 9) result = null
                    }
                }
            }
        }
    }
}