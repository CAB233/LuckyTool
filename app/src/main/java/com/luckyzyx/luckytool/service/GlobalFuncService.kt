package com.luckyzyx.luckytool.service

import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.os.SystemProperties
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.IGlobalFuncController
import com.luckyzyx.luckytool.service.base.BaseControllerService
import com.luckyzyx.luckytool.utils.LogUtils
import com.luckyzyx.luckytool.utils.formatSpace
import com.luckyzyx.luckytool.utils.replaceSpace
import com.topjohnwu.superuser.ipc.RootService
import java.io.File

object GlobalFuncService : BaseControllerService<IGlobalFuncController>() {
    override val TAG = "GlobalFuncService"
    override var controllerService: Class<*> = classOf<GlobalFuncControllerService>()

    override fun getController(iBinder: IBinder?): IGlobalFuncController? {
        return IGlobalFuncController.Stub.asInterface(iBinder)
    }

    class GlobalFuncControllerService : RootService() {
        private val TAG = "GlobalFuncControllerService"

        companion object {
            private const val SURFACE_FLINGER_SERVICE_KEY = "SurfaceFlinger"
            private const val SURFACE_COMPOSER_INTERFACE_KEY = "android.ui.ISurfaceComposer"

            /** SurfaceFlinger 私有 transaction：HDR/SDR 比率覆盖层 开启(1) / 关闭(0) / 查询(2) */
            private const val SURFACE_FLINGER_CODE_HDR_SDR_RATIO = 1043
            private const val SETTING_VALUE_OFF = 0
            private const val SETTING_VALUE_ON = 1
            private const val SETTING_VALUE_QUERY = 2
        }

        override fun onBind(intent: Intent) = object : IGlobalFuncController.Stub() {

            override fun getFileText(dir: String): String {
                val file = File(dir)
                return if (file.exists() && file.isFile) file.readText() else ""
            }

            override fun getOtaVersion(): String {
                return SystemProperties.get("ro.build.version.ota", "null")
            }

            override fun getManifestVersion(): String {
                val str = SystemProperties.get("ro.oplus.image.my_manifest.version", "")
                val str2 = SystemProperties.get("ro.oplus.version.my_manifest", "")
//            return SystemProperties.get("ro.build.version.ota", "null")
                return str.ifBlank { str2 }
            }

            override fun getMarketName(): String {
                return SystemProperties.get("ro.vendor.oplus.market.name", "null")

            }

            override fun getFlashInfo(): String {
                val text = getFileText("/sys/class/block/sda/device/inquiry")
                return if (text.isBlank()) "null" else formatSpace(text)
            }

            override fun getPcbInfo(): String {
                val gms = SystemProperties.get("gsm.serial", "")
                val vendor = SystemProperties.get("vendor.gsm.serial", "null")
                return (gms + vendor).replaceSpace

            }

            override fun getSnInfo(): String {
                return SystemProperties.get("ro.serialno", "null")
            }

            override fun getChipInfo(): String {
                val chipid = SystemProperties.get("ro.boot.chipid", "null")
                val chipecid = SystemProperties.get("ro.boot.chipecid", "null")
                return chipid
            }

            override fun getPrjNameInfo(): String {
                return SystemProperties.get("ro.boot.prjname", "null")
            }

            override fun getSlotInfo(): String {
                return SystemProperties.get("ro.boot.slot_suffix", "").replace("_", "")
                    .takeIf { e -> e.isNotBlank() }?.uppercase() ?: "NonAB"
            }

            override fun getCpuInfo(): String {
                return SystemProperties.get("ro.product.oplus.cpuinfo", "null")
            }

            override fun getHdrSdrRatioOverlay(): Boolean {
                val surfaceFlinger =
                    ServiceManager.getService(SURFACE_FLINGER_SERVICE_KEY) ?: return false
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE_KEY)
                    data.writeInt(SETTING_VALUE_QUERY)
                    // 更早的 SurfaceFlinger 没有 code 1043，transact 返回 false 且 reply 为空
                    if (!surfaceFlinger.transact(
                            SURFACE_FLINGER_CODE_HDR_SDR_RATIO, data, reply, 0
                        ) || reply.dataSize() < 4
                    ) return false
                    reply.readBoolean()
                } catch (e: Exception) {
                    LogUtils.e(TAG, "getHdrSdrRatioOverlay", "$e", true)
                    false
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun setHdrSdrRatioOverlay(enabled: Boolean) {
                val surfaceFlinger =
                    ServiceManager.getService(SURFACE_FLINGER_SERVICE_KEY) ?: return
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE_KEY)
                    data.writeInt(if (enabled) SETTING_VALUE_ON else SETTING_VALUE_OFF)
                    surfaceFlinger.transact(SURFACE_FLINGER_CODE_HDR_SDR_RATIO, data, null, 0)
                } catch (e: Exception) {
                    LogUtils.e(TAG, "setHdrSdrRatioOverlay", "$e", true)
                } finally {
                    data.recycle()
                }
            }
        }
    }
}