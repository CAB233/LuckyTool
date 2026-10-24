package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.highcapable.yukihookapi.extension.kavaref.intercept
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.lsposed.lsparanoid.Obfuscate
import java.util.concurrent.atomic.AtomicBoolean

@Obfuscate
object ForceDisplayOfDeviceControlsTiles : YukiBaseHooker() {
    override fun onHook() {
        //Source ControlsModule
        //设备控制器磁贴的总开关是 ControlsComponent.featureEnabled，由 Dagger 在这里注入，
        //构造期定死并连带决定 controlsController 等 Optional 是否存在；
        //只拦 OplusDeviceControlsTile.isAvailable 会得到灰磁贴（状态位 state = 0，点击直接 return）
        "com.android.systemui.controls.dagger.ControlsModule".toClass().resolve().apply {
            firstMethod { name = "providesControlsFeatureEnabled" }.intercept {
                true
            }
        }
        //C14 起磁贴另有 isAvailable 门，C16 起还叠加地区判断（FeatureOption.isExpRegion()），
        //总开关拦不到这两处，需要单独放行；C12/C13 没有 Oplus 子类，回落基类
        //注意只能拦子类里的 override，基类方法被子类覆盖后 hook 基类不会生效
        if (getOSVersionCode >= 30) {
            VariousClass(
                "com.android.systemui.qs.tiles.DeviceControlsTile", //C13
                "com.oplus.systemui.qs.tiles.OplusDeviceControlsTile"
            ).toClass().resolve().apply {
                firstMethod { name = "isAvailable" }.intercept {
                    true
                }
            }
        }
        //Source DeviceControlsTile
        //磁贴还要求「本机存在提供设备控制器的应用」（getHasControlsApps），一个都没有时同样是灰磁贴；
        //每次返回新的 AtomicBoolean，避免 onServicesUpdated 里的 compareAndSet 把它改回 false
        "com.android.systemui.qs.tiles.DeviceControlsTile".toClass().resolve().apply {
            firstMethod { name = "getHasControlsApps" }.intercept {
                AtomicBoolean(true)
            }
        }
    }
}
