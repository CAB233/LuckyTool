package com.luckyzyx.luckytool.hook.utils.preferences

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.condition.MethodCondition

@Suppress("unused")
object PreferenceReflections {

    object Companion {

        fun callFinder(
            preference: Any, vararg args: Any, condition: MethodCondition<Any>.() -> Unit = {}
        ): Any? {
            val isSuper = preference.asResolver().firstMethodOrNull(condition) == null
            return preference.asResolver().firstMethod {
                apply(condition)
                if (isSuper) superclass()
            }.invoke(*args)
        }

        fun <T> invokeFinder(
            preference: Any, vararg args: Any, condition: MethodCondition<Any>.() -> Unit = {}
        ): T? {
            val isSuper = preference.asResolver().firstMethodOrNull(condition) == null
            return preference.asResolver().firstMethod {
                apply(condition)
                if (isSuper) superclass()
            }.invoke<T>(*args)
        }

    }

    fun addPreference(preferenceScreen: Any, preference: Any): Boolean {
        return Companion.invokeFinder<Boolean>(preferenceScreen, preference) {
            name = "addPreference"
            parameters("androidx.preference.Preference")
            returnType = Boolean::class
        } ?: false
    }

    fun findPreference(preferenceScreen: Any, charSequence: CharSequence): Any? {
        return Companion.callFinder(preferenceScreen, charSequence) {
            name = "findPreference"
            parameters(CharSequence::class)
        }
    }

    fun getPreferenceCount(preferenceScreen: Any): Any? {
        return Companion.callFinder(preferenceScreen) {
            name = "getPreferenceCount"
            emptyParameters()
            returnType = Int::class
        }
    }

    fun removeAll(preferenceScreen: Any) {
        Companion.callFinder(preferenceScreen) {
            name = "removeAll"
            emptyParameters()
            returnType = Void.TYPE
        }
    }

    fun removePreference(preferenceScreen: Any, preference: Any): Boolean {
        return Companion.invokeFinder<Boolean>(preferenceScreen, preference) {
            name = "removePreference"
            parameters("androidx.preference.Preference")
            returnType = Void.TYPE
        } ?: false
    }

    fun getContext(preference: Any): Context? {
        return Companion.invokeFinder<Context>(preference) {
            name = "getContext"
            emptyParameters()
            returnType = Context::class
        }
    }

    fun getIcon(preference: Any): Drawable? {
        return Companion.invokeFinder<Drawable>(preference) {
            name = "getIcon"
            emptyParameters()
            returnType = Drawable::class
        }
    }

    fun setIcon(preference: Any, resId: Int) {
        Companion.callFinder(preference, resId) {
            name = "setIcon"
            parameters(Int::class)
            returnType = Void.TYPE
        }
    }

    fun setIcon(preference: Any, drawable: Drawable) {
        Companion.callFinder(preference, drawable) {
            name = "setIcon"
            parameters(Drawable::class)
            returnType = Void.TYPE
        }
    }

    fun getIntent(preference: Any): Intent? {
        return Companion.invokeFinder<Intent>(preference) {
            name = "getIntent"
            emptyParameters()
            returnType = Intent::class
        }
    }

    fun setIntent(preference: Any, intent: Intent) {
        Companion.callFinder(preference, intent) {
            name = "setIntent"
            parameters(Intent::class)
            returnType = Void.TYPE
        }
    }

    fun getKey(preference: Any): String {
        return Companion.invokeFinder<String>(preference) {
            name = "getKey"
            emptyParameters()
            returnType = String::class
        } ?: ""
    }

    fun setKey(preference: Any, key: String) {
        Companion.callFinder(preference, key) {
            name = "setKey"
            parameters(String::class)
            returnType = Void.TYPE
        }
    }

    fun getSummary(preference: Any): CharSequence? {
        return Companion.invokeFinder<CharSequence>(preference) {
            name = "getSummary"
            emptyParameters()
            returnType = CharSequence::class
        }
    }

    fun setSummary(preference: Any, resId: Int) {
        Companion.callFinder(preference, resId) {
            name = "setSummary"
            parameters(Int::class)
            returnType = Void.TYPE
        }
    }

    fun setSummary(preference: Any, charSequence: CharSequence) {
        Companion.callFinder(preference, charSequence) {
            name = "setSummary"
            parameters(CharSequence::class)
            returnType = Void.TYPE
        }
    }

    fun isVisible(preference: Any): Boolean {
        return Companion.invokeFinder<Boolean>(preference) {
            name = "isVisible"
            emptyParameters()
            returnType = Boolean::class
        } ?: false
    }

    fun setVisible(preference: Any, visible: Boolean) {
        Companion.callFinder(preference, visible) {
            name = "setVisible"
            parameters(Boolean::class)
            returnType = Void.TYPE
        }
    }

    fun isEnabled(preference: Any): Boolean {
        return Companion.invokeFinder<Boolean>(preference) {
            name = "isEnabled"
            emptyParameters()
            returnType = Boolean::class
        } ?: false
    }

    fun setEnabled(preference: Any, enable: Boolean) {
        Companion.callFinder(preference, enable) {
            name = "setEnabled"
            parameters(Boolean::class)
            returnType = Void.TYPE
        }
    }

    fun isPersistent(preference: Any): Boolean {
        return Companion.invokeFinder<Boolean>(preference) {
            name = "isPersistent"
            emptyParameters()
            returnType = Boolean::class
        } ?: false
    }

    fun setPersistent(preference: Any, enable: Boolean) {
        Companion.callFinder(preference, enable) {
            name = "setPersistent"
            parameters(Boolean::class)
            returnType = Void.TYPE
        }
    }

    fun isSelectable(preference: Any): Boolean {
        return Companion.invokeFinder<Boolean>(preference) {
            name = "isSelectable"
            emptyParameters()
            returnType = Boolean::class
        } ?: false
    }

    fun setSelectable(preference: Any, enable: Boolean) {
        Companion.callFinder(preference, enable) {
            name = "setSelectable"
            parameters(Boolean::class)
            returnType = Void.TYPE
        }
    }

    fun getTitle(preference: Any): CharSequence? {
        return Companion.invokeFinder<CharSequence>(preference) {
            name = "getTitle"
            emptyParameters()
            returnType = CharSequence::class
        }
    }

    fun setTitle(preference: Any, resId: Int) {
        Companion.callFinder(preference, resId) {
            name = "setTitle"
            parameters(Int::class)
            returnType = Void.TYPE
        }
    }

    fun setTitle(preference: Any, charSequence: CharSequence) {
        Companion.callFinder(preference, charSequence) {
            name = "setTitle"
            parameters(CharSequence::class)
            returnType = Void.TYPE
        }
    }

    fun isCopyingEnabled(preference: Any): Boolean {
        return Companion.invokeFinder<Boolean>(preference) {
            name = "isCopyingEnabled"
            emptyParameters()
            returnType = Boolean::class
        } ?: false
    }

    fun setCopyingEnabled(preference: Any, copy: Boolean) {
        Companion.callFinder(preference, copy) {
            name = "setCopyingEnabled"
            parameters(Boolean::class)
            returnType = Void.TYPE
        }
    }

}