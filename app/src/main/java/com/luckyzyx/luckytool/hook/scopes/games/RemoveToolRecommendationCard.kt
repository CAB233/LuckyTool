package com.luckyzyx.luckytool.hook.scopes.games

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveToolRecommendationCard : Hooker {
    override fun onHook() {
        //Source ToolsRecommendCardLayout
        "business.module.toolsrecommend.ToolsRecommendCardLayout".toClassOrNull()?.resolve()
            ?.apply {
                firstMethod {
                    parameters(List::class)
                    returnType = Void.TYPE
                }.hook {
                    before {
                        args().first().set(ArrayList<Any>())
                    }
                }
            }

        //Source GameToolTileAdapter V9.0.0+
//        "business.toolpanel.adapter.GameToolTileAdapter".toClassOrNull()?.apply {
//            method { name = "onCreateViewHolder" }.hook {
//                after {
//                    val parent = args().first().cast<ViewGroup>() ?: return@after
//                    val id = args().last().cast<Int>() ?: 0
//                    if (id == 10005) result<ViewHolder>()?.itemView?.isVisible = false
//                }
//            }
//        }
    }
}