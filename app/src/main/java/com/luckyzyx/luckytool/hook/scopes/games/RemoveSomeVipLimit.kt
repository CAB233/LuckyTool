package com.luckyzyx.luckytool.hook.scopes.games

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveSomeVipLimit : Hooker {
    override fun onHook() {
        //network_speed_vip -> oppo_acc

        //Source VipInfoBean -> VipInfosDTO
        //<string name="magic_voice_buy_vip_tip">开启游戏变声，尽享全部变声效果</string>
        "com.oplus.games.account.bean.VipInfoBean\$VipInfosDTO".toClass().resolve().apply {
            firstMethod { name = "getVip" }.hook {
                replaceTo(true)
            }
            firstMethod { name = "getExpiredVip" }.hook {
                replaceTo(false)
            }
            firstMethod { name = "getExpireTime" }.hook {
                replaceTo("2999-12-31")
            }
            firstMethod { name = "getSign" }.hook {
                replaceTo(true)
            }
        }
        //Source VipAccelearateResponse
        "com.oplus.games.account.bean.VipAccelearateResponse".toClass().resolve().apply {
            firstMethod { name = "getSuperBooster" }.hook {
                replaceTo(true)
            }
            firstMethod { name = "isSuperBooster" }.hook {
                replaceTo(true)
            }
        }
        //Source VIPStateBean
        "com.oplus.games.account.bean.VIPStateBean".toClass().resolve().apply {
            firstMethod { name = "getVipState" }.hook {
                replaceTo(5)
            }
            firstMethod { name = "getExpireTime" }.hook {
                replaceTo("2999-12-31")
            }
        }
        //Source UserInfo
        "com.coloros.gamespaceui.module.magicvoice.oplus.data.UserInfo".toClass().resolve().apply {
            firstMethod { name = "getExpireTime" }.hook {
                replaceTo("2999-12-31")
            }
            firstMethod { name = "getHasTrialQualifications" }.hook {
                replaceTo(true)
            }
            firstMethod { name = "getUserIdentity" }.hook {
                replaceTo(3)
            }
        }
    }
}