package com.luckyzyx.luckytool.hook.core

import android.content.pm.ApplicationInfo

/** 每个包回调保存的宿主快照，供 Hook 和配置回调使用。 */
class PackageContext internal constructor(
    val packageName: String,
    val classLoader: ClassLoader?,
    val appInfo: ApplicationInfo?
) {

    val appClassLoader: ClassLoader
        get() = classLoader
            ?: Env.hostContext()?.classLoader
            ?: error("appClassLoader unavailable")
}