package com.luckyzyx.luckytool.ui.activity

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.highcapable.betterandroid.system.extension.component.Intent
import com.highcapable.betterandroid.ui.extension.view.toast
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.BuildConfig
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.service.ActivityManagerService
import com.luckyzyx.luckytool.service.AdbService
import com.luckyzyx.luckytool.service.GlobalFuncService
import com.luckyzyx.luckytool.service.PackagesService
import com.luckyzyx.luckytool.service.PowerService
import com.luckyzyx.luckytool.service.RefreshRateService
import com.luckyzyx.luckytool.service.TilesService
import com.luckyzyx.luckytool.service.UserService
import com.luckyzyx.luckytool.ui.compose.FunctionRequest
import com.luckyzyx.luckytool.ui.compose.LuckySplashHost
import com.luckyzyx.luckytool.ui.compose.MainShell
import com.luckyzyx.luckytool.ui.compose.components.GateDialogSpec
import com.luckyzyx.luckytool.ui.service.XposedServiceBridge
import com.luckyzyx.luckytool.ui.theme.LuckyAppTheme
import com.luckyzyx.luckytool.utils.A12
import com.luckyzyx.luckytool.utils.BiometricUtils
import com.luckyzyx.luckytool.utils.DeviceUtils
import com.luckyzyx.luckytool.utils.IntentPrefs
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.OtherPrefs
import com.luckyzyx.luckytool.utils.PermissionUtils
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.utils.SettingsPrefs
import com.luckyzyx.luckytool.utils.ThemeUtils
import com.luckyzyx.luckytool.utils.getBoolean
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.utils.putBoolean
import com.luckyzyx.luckytool.utils.verityPackage
import kotlinx.coroutines.flow.MutableStateFlow
import org.lsposed.lsparanoid.Obfuscate

@Obfuscate
@Suppress("PrivatePropertyName")
open class MainActivity : AppCompatActivity() {
    //检测Prefs状态
    private val KEY_PREFIX = classOf<MainActivity>().name + '.'
    private val EXTRA_SAVED_INSTANCE_STATE = KEY_PREFIX + "SAVED_INSTANCE_STATE"

    /** 当前是否位于 Function tab（由 MainShell 维护，供 onResume 显示恢复） */
    var isOnFunctionTab by mutableStateOf(false)

    /** 当前是否位于 Home tab（冷启动默认 true = 起始目的地；checkOs 判断用） */
    var isOnHomeTab by mutableStateOf(true)

    /** 跨 tab 跳转请求队列：Compose 页面 → Function 子树作用域页（MainShell 切 tab，FunctionPage 消费） */
    val functionNavRequests = MutableStateFlow<FunctionRequest?>(null)

    /** 启动阻塞门对话框（Xposed 未激活 / 无 root / 系统不支持）：非空即由 MainShell 渲染，见 GateDialog */
    var gateDialog by mutableStateOf<GateDialogSpec?>(null)

    private fun newIntent(context: Context): Intent {
        return Intent<MainActivity>(context)
    }

    private fun newIntent(savedInstanceState: Bundle, context: Context): Intent {
        return newIntent(context).putExtra(EXTRA_SAVED_INSTANCE_STATE, savedInstanceState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeUtils.initTheme(this)

        verityPackage()
        checkXposed()
        checkOs()
        checkUserId()
        checkBiometric()

        setContent {
            LuckyAppTheme {
                // 启动闪屏覆盖在主界面之上：主界面同时组合，闪屏淡出后立即可用
                // 启动阻塞门是场景内弹层，闪屏淡出后即露出，不需要为它跳过闪屏
                LuckySplashHost {
                    MainShell(this)
                }
            }
        }
    }

    /** Compose 页面跨 tab 跳转到 Function 子树作用域页（pageKey 见 ScopePageRegistry） */
    fun requestFunctionNavigation(pageKey: String, title: CharSequence?) {
        functionNavRequests.value = FunctionRequest(pageKey, title?.toString())
    }

    private fun checkUserId() {
        if ((Process.myUid() / 100000) != 0) {
            toast(getString(R.string.check_app_userid))
            finish()
        }
    }

    private fun checkXposed() {
        XposedServiceBridge.awaitReady()
        if (!XposedServiceBridge.isModuleActive) {
            gateDialog = GateDialogSpec(message = getString(R.string.unsupported_xposed))
            return
        }
    }

    private fun checkSu() {
        val isSu = DeviceUtils.getRootStatus()
        putBoolean(SettingsPrefs, "is_su", isSu)
        putBoolean(SettingsPrefs, "settings_prefs", isSu)
        putBoolean(ModulePrefs, "module_prefs", isSu)
        putBoolean(IntentPrefs, "intent_prefs", isSu)
        putBoolean(OtherPrefs, "other_prefs", isSu)
        if (!isSu && gateDialog == null) {
            gateDialog = GateDialogSpec(
                title = getString(R.string.no_root),
                message = getString(R.string.no_root_summary),
            )
            return
        }
        putBoolean(SettingsPrefs, "enable_module_print_logs", BuildConfig.DEBUG)
        PermissionUtils(this).start()
    }

    private fun checkOs() {
        val osCode = getOSVersionCode
        if (osCode < 23 && isOnHomeTab) {
            gateDialog = GateDialogSpec(
                title = getString(R.string.unsupported_os),
                message = getString(R.string.unsupported_os_summary),
                // 与迁移前一致：osCode > 0 时额外提供「忽略」，点击只关闭对话框
                neutralLabel = if (osCode > 0) getString(R.string.ignore) else null,
            )
        }
    }

    private fun checkBiometric() {
        val enable = getBoolean(SettingsPrefs, "enable_biometric_unlock_verification", false)
        val manager = getSystemService(classOf<KeyguardManager>())
        if (enable && manager.isDeviceSecure) {
            BiometricUtils.showBiometricPrompt(
                this,
                onError = { _, _ -> finish() },
                onFailed = { finish() })
        }
    }

    private fun initAllService() {
        GlobalFuncService.init(this)
        PackagesService.init(this)
        TilesService.init(this)
        RefreshRateService.init(this)
        AdbService.init(this)
        ActivityManagerService.init(this)
        UserService.init(this)
        PowerService.init(this)
    }

    override fun onResume() {
        super.onResume()
        checkSu()
        initAllService()
    }

    @Suppress("DEPRECATION")
    fun restart() {
        if (SDK >= A12 || !Process.isApplicationUid(Process.myUid())) {
            recreate()
        } else {
            try {
                val savedInstanceState = Bundle()
                onSaveInstanceState(savedInstanceState)
                finish()
                startActivity(newIntent(savedInstanceState, this))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } catch (_: Throwable) {
                recreate()
            }
        }
    }
}
