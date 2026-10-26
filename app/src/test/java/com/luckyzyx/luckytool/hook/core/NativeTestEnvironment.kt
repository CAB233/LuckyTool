package com.luckyzyx.luckytool.hook.core

import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy

internal inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        call(method.name, args ?: emptyArray())
    } as T

internal class TestPreferences {
    val values = mutableMapOf<String, Any?>()
    val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    val shared: SharedPreferences = proxy { method, args ->
        when (method) {
            "getAll" -> values.toMutableMap()
            "contains" -> values.containsKey(args[0])
            "registerOnSharedPreferenceChangeListener" -> {
                listeners += args[0] as SharedPreferences.OnSharedPreferenceChangeListener
                null
            }
            "unregisterOnSharedPreferenceChangeListener" -> {
                listeners.remove(args[0])
                null
            }
            else -> if (method.startsWith("get")) values[args[0]] ?: args[1]
                else error("Unexpected preferences call: $method")
        }
    }

    fun put(key: String, value: Any?) {
        values[key] = value
        listeners.toList().forEach { it.onSharedPreferenceChanged(shared, key) }
    }
}

internal class NativeTestEnvironment {
    val groups = mutableMapOf<String, TestPreferences>()
    val hooks = mutableMapOf<Executable, XposedInterface.Hooker>()
    var exceptionMode: XposedInterface.ExceptionMode? = null
    var invokerType: XposedInterface.Invoker.Type? = null
    var origin: (Array<out Any?>) -> Any? = { it.firstOrNull() }

    val base: XposedInterface = proxy { method, args ->
        when (method) {
            "getRemotePreferences" -> group(args[0] as String).shared
            "log" -> null
            "hook" -> {
                val executable = args[0] as Executable
                lateinit var builder: XposedInterface.HookBuilder
                builder = proxy { operation, values ->
                    when (operation) {
                        "setExceptionMode" -> { exceptionMode = values[0] as XposedInterface.ExceptionMode; builder }
                        "setPriority" -> builder
                        "intercept" -> {
                            hooks[executable] = values[0] as XposedInterface.Hooker
                            proxy<XposedInterface.HookHandle> { _, _ -> null }
                        }
                        else -> error("Unexpected builder call: $operation")
                    }
                }
                builder
            }
            "getInvoker" -> {
                lateinit var invoker: XposedInterface.Invoker<*, *>
                invoker = proxy { operation, values ->
                    when (operation) {
                        "setType" -> { invokerType = values[0] as XposedInterface.Invoker.Type; invoker }
                        "invoke" -> origin(values[1] as Array<out Any?>)
                        else -> error("Unexpected invoker call: $operation")
                    }
                }
                invoker
            }
            else -> error("Unexpected framework call: $method")
        }
    }

    init {
        Channel.dispatch = { it() }
        Env.attach(base, "test", XposedInterface.PROP_CAP_REMOTE)
        Env.enter("test.host", javaClass.classLoader!!, null)
    }

    fun group(name: String) = groups.getOrPut(name) { TestPreferences() }
}
