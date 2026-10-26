package com.luckyzyx.luckytool.hook.core

import android.util.Log

/** 应用进程使用 logcat；宿主进程由入口接入框架日志。 */
object XLog {
    const val TAG = "LuckyTool"
    internal var frameworkLogger: ((Int, String, String, Throwable?) -> Unit)? = null

    private fun write(priority: Int, msg: String, tag: String, t: Throwable?) {
        frameworkLogger?.let { logger ->
            runCatching { logger(priority, tag, msg, t) }.onSuccess { return }
        }
        Log.println(priority, tag, msg + (t?.let { "\n${Log.getStackTraceString(it)}" } ?: ""))
    }

    fun v(msg: String, tag: String = TAG, t: Throwable? = null) = write(Log.VERBOSE, msg, tag, t)
    fun d(msg: String, tag: String = TAG, t: Throwable? = null) = write(Log.DEBUG, msg, tag, t)
    fun i(msg: String, tag: String = TAG, t: Throwable? = null) = write(Log.INFO, msg, tag, t)
    fun w(msg: String, tag: String = TAG, t: Throwable? = null) = write(Log.WARN, msg, tag, t)
    fun e(msg: String, tag: String = TAG, t: Throwable? = null) = write(Log.ERROR, msg, tag, t)
    fun verbose(msg: String, t: Throwable? = null, tag: String = TAG) = v(msg, tag, t)
    fun debug(msg: String, t: Throwable? = null, tag: String = TAG) = d(msg, tag, t)
    fun info(msg: String, t: Throwable? = null, tag: String = TAG) = i(msg, tag, t)
    fun warn(msg: String, t: Throwable? = null, tag: String = TAG) = w(msg, tag, t)
    fun error(msg: String, t: Throwable? = null, tag: String = TAG) = e(msg, tag, t)
}
