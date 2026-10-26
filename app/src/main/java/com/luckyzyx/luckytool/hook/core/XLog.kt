package com.luckyzyx.luckytool.hook.core

import android.util.Log

/** 应用进程使用 logcat；宿主进程由入口接入框架日志。 */
object XLog {
    const val TAG = "LuckyTool"
    internal var frameworkLogger: ((Int, String, String, Throwable?) -> Unit)? = null

    private fun write(priority: Int, msg: String, tag: String, e: Throwable?) {
        frameworkLogger?.let { logger ->
            runCatching { logger(priority, tag, msg, e) }.onSuccess { return }
        }
        Log.println(priority, tag, msg + (e?.let { "\n${Log.getStackTraceString(it)}" } ?: ""))
    }

    fun v(msg: String, tag: String = TAG, e: Throwable? = null) = write(Log.VERBOSE, msg, tag, e)
    fun d(msg: String, tag: String = TAG, e: Throwable? = null) = write(Log.DEBUG, msg, tag, e)
    fun i(msg: String, tag: String = TAG, e: Throwable? = null) = write(Log.INFO, msg, tag, e)
    fun w(msg: String, tag: String = TAG, e: Throwable? = null) = write(Log.WARN, msg, tag, e)
    fun e(msg: String, tag: String = TAG, e: Throwable? = null) = write(Log.ERROR, msg, tag, e)
    fun verbose(msg: String, e: Throwable? = null, tag: String = TAG) = v(msg, tag, e)
    fun debug(msg: String, e: Throwable? = null, tag: String = TAG) = d(msg, tag, e)
    fun info(msg: String, e: Throwable? = null, tag: String = TAG) = i(msg, tag, e)
    fun warn(msg: String, e: Throwable? = null, tag: String = TAG) = w(msg, tag, e)
    fun error(msg: String, e: Throwable? = null, tag: String = TAG) = e(msg, tag, e)
}
