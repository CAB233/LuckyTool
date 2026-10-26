package com.luckyzyx.luckytool.hook.core

import com.luckyzyx.luckytool.utils.*
import org.junit.Assert.*
import org.junit.Test

class ChannelTest {
    @Test fun firstValueForPreviouslyAbsentKeyIsDelivered() {
        val env = NativeTestEnvironment()
        val received = mutableListOf<Boolean>()
        Channel.wait<Boolean>("new-key") { received += it }
        assertTrue(received.isEmpty())
        env.group(ModulePrefs).put("new-key", true)
        assertEquals(listOf(true), received)
        assertEquals(1, env.group(SettingsPrefs).listeners.size)
    }

    @Test fun intentPreferenceUpdatesAreDelivered() {
        val env = NativeTestEnvironment()
        var enabled = false
        Channel.wait<Boolean>("intent-switch") { enabled = it }
        env.group(IntentPrefs).put("intent-switch", true)
        assertTrue(enabled)
    }

    @Test fun targetedEventsPreserveTypesAndStoredEventsAreNotReplayed() {
        val env = NativeTestEnvironment()
        val events = env.group(ChannelPrefs)
        val key = ChannelEventCodec.key("test.host", "app-event")
        events.put(key, ChannelEventCodec.encode("test.host", "old.app" to true))
        val received = mutableListOf<Pair<String, Boolean>>()
        Channel.wait<Pair<String, Boolean>>("app-event") { received += it }
        assertTrue(received.isEmpty())
        events.put(ChannelEventCodec.key("other.host", "app-event"), ChannelEventCodec.encode("other.host", "other.app" to true))
        assertTrue(received.isEmpty())
        events.put(key, ChannelEventCodec.encode("test.host", "new.app" to false))
        assertEquals(listOf("new.app" to false), received)
    }

    @Test fun systemAliasAndRefreshSignalsReachSystemServer() {
        val env = NativeTestEnvironment()
        Env.enter("android", javaClass.classLoader!!, null)
        var notifications = 0
        Channel.wait("refresh") { notifications++ }
        env.group(ChannelPrefs).put(ChannelEventCodec.key("system", "refresh"), ChannelEventCodec.encode("system", null))
        assertEquals(1, notifications)
    }

    @Test fun eventCodecPreservesPrimitiveValuesAndUserLists() {
        for (value in listOf(true, 12, 5000000000L, 2.5f, "package", arrayListOf(0, 10))) {
            val decoded = ChannelEventCodec.decode(ChannelEventCodec.encode("android", value))
            assertEquals("android", decoded.target)
            assertEquals(value, decoded.value)
        }
        assertNotEquals(ChannelEventCodec.encode("android", true), ChannelEventCodec.encode("android", true))
    }

    @Test fun queuedDeliveryRetainsEachUpdateValue() {
        val env = NativeTestEnvironment()
        val pending = mutableListOf<() -> Unit>()
        Channel.dispatch = { pending += it }
        val received = mutableListOf<String>()
        Channel.wait<String>("queued") { received += it }
        env.group(ModulePrefs).put("queued", "first")
        env.group(ModulePrefs).put("queued", "second")
        assertTrue(received.isEmpty())
        pending.forEach { it() }
        assertEquals(listOf("first", "second"), received)
    }

    @Test fun sharedProcessDispatchRetainsPackageAndCallbackLoader() {
        NativeTestEnvironment()
        val firstLoader = object : ClassLoader(javaClass.classLoader) {}
        val secondLoader = object : ClassLoader(javaClass.classLoader) {}
        val seen = mutableListOf<String>()
        lateinit var firstContext: PackageContext
        val first = object : Hooker {
            override fun onHook() {
                seen += packageName
                firstContext = HookRouter.currentContext()!!
            }
        }
        val second = object : Hooker {
            override fun onHook() { seen += packageName }
        }
        HookRouter.app("test.shared.first") { first }
        HookRouter.app("test.shared.second") { second }
        HookRouter.dispatch("test.shared.first", firstLoader, null)
        HookRouter.dispatch("test.shared.second", secondLoader, null)
        assertEquals(listOf("test.shared.first", "test.shared.second"), seen)
        assertSame(firstLoader, first.appClassLoader)
        HookRouter.withContext(firstContext) {
            assertSame(firstLoader, first.appClassLoader)
            assertEquals("test.shared.first", first.packageName)
        }
        assertSame(secondLoader, second.appClassLoader)
    }

    @Test fun hookerConstructionWaitsForMatchingPackageContext() {
        NativeTestEnvironment()
        var created = 0
        var constructedFor: String? = null
        HookRouter.app("test.lazy.host") {
            created++
            constructedFor = HookRouter.currentContext()?.packageName
            object : Hooker { override fun onHook() = Unit }
        }
        assertEquals(0, created)
        HookRouter.dispatch("test.unrelated", javaClass.classLoader!!, null)
        assertEquals(0, created)
        HookRouter.dispatch("test.lazy.host", javaClass.classLoader!!, null)
        assertEquals(1, created)
        assertEquals("test.lazy.host", constructedFor)
    }
}
