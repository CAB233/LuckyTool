package com.luckyzyx.luckytool.hook.utils.sysui

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

@Suppress("unused")
class AbsSettingsValueProxyUtils(val classLoader: ClassLoader?) {

    val clazz =
        "com.oplusos.systemui.common.settingsvalue.AbsSettingsValueProxy".toClass(classLoader)

    fun getGlobalIntValue(context: Context, key: String, defValue: Int): Int {
        return clazz.resolve().firstMethod {
            name = "getGlobalIntValue"
            parameters(Context::class, String::class, Int::class)
            returnType = Int::class
        }.invoke<Int>(context, key, defValue) ?: defValue
    }

    fun getSecureIntValue(context: Context, key: String, defValue: Int): Int {
        return clazz.resolve().firstMethod {
            name = "getSecureIntValue"
            parameters(Context::class, String::class, Int::class)
            returnType = Int::class
        }.invoke<Int>(context, key, defValue) ?: defValue
    }

    fun getSecureIntValue(context: Context, key: String, defValue: Int, userId: Int): Int {
        return clazz.resolve().firstMethod {
            name = "getSecureIntValue"
            parameters(Context::class, String::class, Int::class)
            returnType = Int::class
        }.invoke<Int>(context, key, defValue, userId) ?: defValue
    }

    fun getSecureStringValue(context: Context, key: String): String {
        return clazz.resolve().firstMethod {
            name = "getSecureStringValue"
            parameters(Context::class, String::class)
            returnType = String::class
        }.invoke<String>(context, key) ?: ""
    }

    fun getSecureStringValue(context: Context, key: String, userId: Int): String {
        return clazz.resolve().firstMethod {
            name = "getSecureStringValue"
            parameters(Context::class, String::class, Int::class)
            returnType = String::class
        }.invoke<String>(context, key, userId) ?: ""
    }

    fun getSystemFloatValue(context: Context, key: String, defValue: Float): Float {
        return clazz.resolve().firstMethod {
            name = "getSystemFloatValue"
            parameters(Context::class, String::class, Float::class)
            returnType = Float::class
        }.invoke<Float>(context, key, defValue) ?: defValue
    }

    fun getSystemIntValue(context: Context, key: String, defValue: Int): Int {
        return clazz.resolve().firstMethod {
            name = "getSystemIntValue"
            parameters(Context::class, String::class, Int::class)
            returnType = Int::class
        }.invoke<Int>(context, key, defValue) ?: defValue
    }

    fun getSystemIntValue(context: Context, key: String, defValue: Int, userId: Int): Int {
        return clazz.resolve().firstMethod {
            name = "getSystemIntValue"
            parameters(Context::class, String::class, Int::class, Int::class)
            returnType = Int::class
        }.invoke<Int>(context, key, defValue, userId) ?: defValue
    }

    fun getSystemStringValue(context: Context, key: String): String {
        return clazz.resolve().firstMethod {
            name = "getSystemStringValue"
            parameters(Context::class, String::class)
            returnType = String::class
        }.invoke<String>(context, key) ?: ""
    }

    fun getSystemStringValue(context: Context, key: String, userId: Int): String {
        return clazz.resolve().firstMethod {
            name = "getSystemStringValue"
            parameters(Context::class, String::class, Int::class)
            returnType = String::class
        }.invoke<String>(context, key, userId) ?: ""
    }

}