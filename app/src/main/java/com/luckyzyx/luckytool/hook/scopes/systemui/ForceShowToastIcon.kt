package com.luckyzyx.luckytool.hook.scopes.systemui

import android.content.Context
import android.widget.ImageView
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.PackageUtils
import com.luckyzyx.luckytool.hook.core.*

object ForceShowToastIcon : Hooker {
    override fun onHook() {
        //Source OplusSystemUIToast
        "com.oplus.systemui.toast.OplusSystemUIToast".toClass().resolve().apply {
            firstConstructor { parameterCount = 7 }.hook {
                after {
                    val context = firstField { type = Context::class }.of(instance)
                        .get<Context>() ?: return@after
                    val packName = args(3).cast<String>() ?: ""
                    val mIconView = firstField { type = ImageView::class }.of(instance)
                        .get<ImageView>() ?: return@after
                    val icon = PackageUtils(context.packageManager).getApplicationIcon(packName)
                        ?: return@after
                    mIconView.setImageDrawable(icon)
                    mIconView.isVisible = true
                }
            }
        }
    }
}