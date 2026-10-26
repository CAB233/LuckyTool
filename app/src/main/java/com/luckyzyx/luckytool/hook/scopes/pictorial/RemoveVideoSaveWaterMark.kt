package com.luckyzyx.luckytool.hook.scopes.pictorial

import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveVideoSaveWaterMark : Hooker {
    override fun onHook() {
        //Source VideoWaterMarkView -> view_video_water_mark
        "com.heytap.pictorial.data.VideoWaterMarkView".toClass().resolve().apply {
            constructor {}.hookAll {
                after {
                    instance<LinearLayout>().removeAllViews()
                }
            }
        }
    }
}