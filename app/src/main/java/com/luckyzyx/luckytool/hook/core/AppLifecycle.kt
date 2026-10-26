package com.luckyzyx.luckytool.hook.core

import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.highcapable.kavaref.KavaRef.Companion.resolve
import java.util.concurrent.atomic.AtomicBoolean

/** 宿主 Application 创建完成后执行，每次注册执行一次。 */
fun Hooker.onAppLifecycle(block: AppLifecycleScope.() -> Unit) {
    val context = HookRouter.contextOf(this)
    fun initialize(app: Context) {
        runCatching { HookRouter.withContext(context) { AppLifecycleScope(app, context).block() } }
            .onFailure { XLog.e("Application lifecycle initialization failed", e = it) }
    }
    Env.hostContext()?.let { initialize(it); return }
    val initialized = AtomicBoolean()
    // 宿主通常覆盖 Application.onCreate，通过 Instrumentation 捕获完整的创建回调。
    Instrumentation::class.resolve().firstMethod {
        name = "callApplicationOnCreate"
        parameters(Application::class)
    }.hook {
        after {
            args().first().cast<Application>()?.let { app ->
                if (initialized.compareAndSet(false, true)) initialize(app)
            }
        }
    }
}

class AppLifecycleScope internal constructor(
    private val context: Context,
    private val host: PackageContext?
) {
    private val receivers = mutableListOf<BroadcastReceiver>()

    fun onCreate(block: Context.() -> Unit) {
        runCatching { block(context) }
            .onFailure { XLog.e("Application onCreate callback failed", e = it) }
    }

    fun registerReceiver(action: String, block: (Context, Intent) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                runCatching { HookRouter.withContext(host) { block(ctx, intent) } }
                    .onFailure { XLog.e("Broadcast callback failed: $action", e = it) }
            }
        }
        receivers += receiver
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(action))
            }
        }.onFailure { XLog.e("Broadcast registration failed: $action", e = it) }
    }
}
