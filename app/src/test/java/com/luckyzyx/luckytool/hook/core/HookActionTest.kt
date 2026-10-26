package com.luckyzyx.luckytool.hook.core

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test

class HookActionTest {
    class Target {
        fun echo(value: String) = value
    }

    private val target = Target()
    private val member = Target::class.java.getDeclaredMethod("echo", String::class.java)
    private var proceeded = 0

    private fun chain(block: (Array<out Any?>) -> Any? = { it.firstOrNull() }): XposedInterface.Chain =
        proxy { operation, values ->
            when (operation) {
                "getExecutable" -> member
                "getThisObject" -> target
                "getArgs" -> listOf("input")
                "proceed" -> { proceeded++; block(values.firstOrNull() as? Array<out Any?> ?: arrayOf("input")) }
                else -> error("Unexpected chain call: $operation")
            }
        }

    private fun execute(action: HookAction, chain: XposedInterface.Chain = chain()): Any? =
        action.invoke(chain, member, javaClass.classLoader, "host") { error("Unexpected origin call") }

    @Test fun modifiedArgumentsReachOriginalAndAfterSeesResult() {
        val result = execute(HookAction().apply {
            before { args(0).set("modified") }
            after { result = "${result<String>()}-after" }
        })
        assertEquals("modified-after", result)
        assertEquals(1, proceeded)
    }

    @Test fun earlyReturnSkipsOriginalAndRunsAfter() {
        val result = execute(HookAction().apply {
            before { result = "early" }
            after { result = "${result<String>()}-after" }
        })
        assertEquals("early-after", result)
        assertEquals(0, proceeded)
    }

    @Test fun replacementRunsAndNullReplacementSkipsOriginal() {
        assertEquals("replacement", execute(HookAction().apply { replaceAny { "replacement" } }))
        assertNull(execute(HookAction().apply { replaceTo(null) }))
        assertEquals(0, proceeded)
    }

    @Test fun afterCanRecoverOriginalException() {
        val failure = IllegalStateException("original failure")
        val result = execute(HookAction().apply {
            after {
                assertSame(failure, throwable)
                result = "recovered"
            }
        }, chain { throw failure })
        assertEquals("recovered", result)
        assertEquals(1, proceeded)
    }

    @Test fun unhandledOriginalExceptionRetainsIdentity() {
        val failure = IllegalStateException("original failure")
        val observed = runCatching { execute(HookAction(), chain { throw failure }) }.exceptionOrNull()
        assertSame(failure, observed)
        assertEquals(1, proceeded)
    }

    @Test fun registeredReplacementCallsApi101OriginWithModifiedArguments() {
        val env = NativeTestEnvironment()
        env.origin = { "origin:${it[0]}" }
        member.hook {
            replaceAny {
                args(0).set("changed")
                callOriginal()
            }
        }
        assertEquals("origin:changed", env.hooks.getValue(member).intercept(chain()))
        assertEquals(XposedInterface.Invoker.Type.ORIGIN, env.invokerType)
        assertEquals(XposedInterface.ExceptionMode.PASSTHROUGH, env.exceptionMode)
        assertEquals(0, proceeded)
    }

    @Test fun invokeOriginalAcceptsExplicitArgumentsAndUnwrapsInvocationException() {
        val env = NativeTestEnvironment()
        member.hook { replaceAny { invokeOriginal("explicit") } }
        assertEquals("explicit", env.hooks.getValue(member).intercept(chain()))
        val failure = IllegalArgumentException("original")
        env.origin = { throw java.lang.reflect.InvocationTargetException(failure) }
        assertSame(failure, runCatching { env.hooks.getValue(member).intercept(chain()) }.exceptionOrNull())
    }

    @Test fun ordinaryCallbacksUseProtectiveExceptionMode() {
        val env = NativeTestEnvironment()
        member.hook { after { result = "after" } }
        assertEquals(XposedInterface.ExceptionMode.PROTECTIVE, env.exceptionMode)
        assertEquals("after", env.hooks.getValue(member).intercept(chain()))
        assertEquals(1, proceeded)
    }

    @Test fun absentArgumentReadsReturnDefaults() {
        val args = Args(emptyArray(), emptyList())
        assertNull(args.first().cast<String>())
        assertNull(args.last().any())
        assertEquals(0, args.int())
    }

    @Test fun constructorsKeepReceiverAndArguments() {
        val constructor = Target::class.java.getDeclaredConstructor()
        val call = HookCall(constructor, target, emptyArray(), javaClass.classLoader, "host") { null }
        assertSame(target, call.instance<Target>())
        assertEquals("host", call.packageName)
        assertNull(call.callOriginal())
    }
}
