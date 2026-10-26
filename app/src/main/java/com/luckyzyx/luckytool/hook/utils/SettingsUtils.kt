package com.luckyzyx.luckytool.hook.utils

import android.content.ContentResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve

@Suppress("unused")
object SettingsUtils {
    fun getIntForUser(type: Class<*>, cr: ContentResolver, key: String, userHandle: Int): Int? {
        return type.resolve().firstMethod {
            name = "getIntForUser"
            parameterCount = 3
        }.invoke<Int>(cr, key, userHandle)
    }

    fun getIntForUser(
        type: Class<*>, cr: ContentResolver, key: String, def: Int, userHandle: Int
    ): Int? {
        return type.resolve().firstMethod {
            name = "getIntForUser"
            parameterCount = 4
        }.invoke<Int>(cr, key, def, userHandle)
    }

    fun getLongForUser(
        type: Class<*>, cr: ContentResolver, key: String, userHandle: Int
    ): Long? {
        return type.resolve().firstMethod {
            name = "getLongForUser"
            parameterCount = 3
        }.invoke<Long>(cr, key, userHandle)
    }

    fun getLongForUser(
        type: Class<*>, cr: ContentResolver, key: String, def: Long, userHandle: Int
    ): Long? {
        return type.resolve().firstMethod {
            name = "getLongForUser"
            parameterCount = 4
        }.invoke<Long>(cr, key, def, userHandle)
    }

    fun getFloatForUser(
        type: Class<*>, cr: ContentResolver, key: String, userHandle: Int
    ): Float? {
        return type.resolve().firstMethod {
            name = "getFloatForUser"
            parameterCount = 3
        }.invoke<Float>(cr, key, userHandle)
    }

    fun getFloatForUser(
        type: Class<*>, cr: ContentResolver, key: String, def: Float, userHandle: Int
    ): Float? {
        return type.resolve().firstMethod {
            name = "getFloatForUser"
            parameterCount = 4
        }.invoke<Float>(cr, key, def, userHandle)
    }

    fun getStringForUser(
        type: Class<*>, cr: ContentResolver, key: String, userHandle: Int
    ): String? {
        return type.resolve().firstMethod {
            name = "getStringForUser"
            parameterCount = 3
        }.invoke<String>(cr, key, userHandle)
    }

    fun putIntForUser(
        type: Class<*>, cr: ContentResolver, key: String, value: Int, userHandle: Int
    ): Boolean? {
        return type.resolve().firstMethod {
            name = "putIntForUser"
            parameterCount = 4
        }.invoke<Boolean>(cr, key, value, userHandle)
    }

    fun putLongForUser(
        type: Class<*>, cr: ContentResolver, key: String, value: Long, userHandle: Int
    ): Boolean? {
        return type.resolve().firstMethod {
            name = "putLongForUser"
            parameterCount = 4
        }.invoke<Boolean>(cr, key, value, userHandle)
    }

    fun putFloatForUser(
        type: Class<*>, cr: ContentResolver, key: String, value: Float, userHandle: Int
    ): Boolean? {
        return type.resolve().firstMethod {
            name = "putFloatForUser"
            parameterCount = 4
        }.invoke<Boolean>(cr, key, value, userHandle)
    }

    fun putString(
        type: Class<*>, cr: ContentResolver, key: String, value: String,
        overrideableByRestore: Boolean
    ): Boolean? {
        return type.resolve().firstMethod {
            name = "putString"
            parameterCount = 4
        }.invoke<Boolean>(cr, key, value, overrideableByRestore)
    }

    fun putStringForUser(
        type: Class<*>, cr: ContentResolver, key: String, value: String, userHandle: Int
    ): Boolean? {
        return type.resolve().firstMethod {
            name = "putStringForUser"
            parameterCount = 4
        }.invoke<Boolean>(cr, key, value, userHandle)
    }

    fun putStringForUser(
        type: Class<*>, cr: ContentResolver, key: String, value: String, userHandle: Int,
        overrideableByRestore: Boolean
    ): Boolean? {
        return type.resolve().firstMethod {
            name = "putStringForUser"
            parameterCount = 5
        }.invoke<Boolean>(cr, key, value, userHandle, overrideableByRestore)
    }

    fun putStringForUser(
        type: Class<*>, cr: ContentResolver, key: String, value: String, tag: String,
        makeDefault: Boolean, userHandle: Int, overrideableByRestore: Boolean
    ): Boolean? {
        return type.resolve().firstMethod {
            name = "putStringForUser"
            parameterCount = 7
        }.invoke<Boolean>(cr, key, value, tag, makeDefault, userHandle, overrideableByRestore)
    }
}