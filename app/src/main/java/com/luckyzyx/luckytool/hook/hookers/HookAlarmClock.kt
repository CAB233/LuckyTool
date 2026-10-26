package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.alarmclock.AlarmClockWidget
import com.luckyzyx.luckytool.utils.DexkitUtils
import com.luckyzyx.luckytool.hook.core.*

object HookAlarmClock : Hooker {
    override fun onHook() {

        DexkitUtils.create(appInfo.sourceDir) { dexKitBridge ->
            //桌面时钟组件红一
            loadHooker(AlarmClockWidget(dexKitBridge))
        }

    }
}