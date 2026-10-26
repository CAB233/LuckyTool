package com.luckyzyx.luckytool.hook.utils.appcompat.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.EditText
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.createInstance
import com.luckyzyx.luckytool.hook.core.*

@Suppress("unused")
class COUIAlertDialogBuilder {

    private val clazzString = "com.coui.appcompat.dialog.COUIAlertDialogBuilder"
    var context: Context
    var clazz: Class<*>
    var builder: Any?

    constructor(context: Context, classloader: ClassLoader?) {
        this.context = context
        this.clazz = clazzString.toClass(classloader)
        builder = clazz.createInstance(context, isPublic = false)
    }

    constructor(context: Context, themeResId: Int, classloader: ClassLoader?) {
        this.context = context
        this.clazz = clazzString.toClass(classloader)
        builder = clazz.createInstance(context, themeResId, isPublic = false)
    }

    constructor(context: Context, themeResName: String, classloader: ClassLoader?) {
        this.context = context
        this.clazz = clazzString.toClass(classloader)
        val themeResId = getStyle(themeResName)
        builder = clazz.createInstance(context, themeResId, isPublic = false)
    }

    constructor(context: Context, themeResId: Int, themeResId2: Int, classloader: ClassLoader?) {
        this.context = context
        this.clazz = clazzString.toClass(classloader)
        builder = clazz.createInstance(context, themeResId, themeResId2, isPublic = false)
    }

    fun setTitle(charSequence: CharSequence) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setTitle"
            parameters(CharSequence::class)
        }.invoke(charSequence)
    }

    fun setTitle(int: Int) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setTitle"
            parameters(Int::class)
        }.invoke(int)
    }

    fun setMessage(charSequence: CharSequence) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setMessage"
            parameters(CharSequence::class)
        }.invoke(charSequence)
    }

    fun setMessage(int: Int) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setMessage"
            parameters(Int::class)
        }.invoke(int)
    }

    fun setPositiveButton(
        charSequence: CharSequence, onClickListener: DialogInterface.OnClickListener?
    ) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setPositiveButton"
            parameters(CharSequence::class, classOf<DialogInterface.OnClickListener>())
        }.invoke(charSequence, onClickListener)
    }

    fun setPositiveButton(int: Int, onClickListener: DialogInterface.OnClickListener?) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setPositiveButton"
            parameters(Int::class, classOf<DialogInterface.OnClickListener>())
        }.invoke(int, onClickListener)
    }

    fun setNeutralButton(
        charSequence: CharSequence, onClickListener: DialogInterface.OnClickListener?
    ) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setNeutralButton"
            parameters(CharSequence::class, classOf<DialogInterface.OnClickListener>())
        }.invoke(charSequence, onClickListener)
    }

    fun setNeutralButton(int: Int, onClickListener: DialogInterface.OnClickListener?) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setNeutralButton"
            parameters(Int::class, classOf<DialogInterface.OnClickListener>())
        }.invoke(int, onClickListener)
    }

    fun setNegativeButton(
        charSequence: CharSequence, onClickListener: DialogInterface.OnClickListener?
    ) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setNegativeButton"
            parameters(CharSequence::class, classOf<DialogInterface.OnClickListener>())
        }.invoke(charSequence, onClickListener)
    }

    fun setNegativeButton(int: Int, onClickListener: DialogInterface.OnClickListener?) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setNegativeButton"
            parameters(Int::class, classOf<DialogInterface.OnClickListener>())
        }.invoke(int, onClickListener)
    }

    fun setView(int: Int) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setView"
            parameters(Int::class)
        }.invoke(int)
    }

    fun setView(view: View) {
        if (builder != null) builder!!.asResolver().firstMethod {
            name = "setView"
            parameters(View::class)
        }.invoke(view)
    }

    fun Any.create(): Any? {
        return asResolver().firstMethod {
            name = "create"
            emptyParameters()
        }.invoke()
    }

    fun Any.dismiss() {
        asResolver().firstMethod {
            name = "dismiss"
            emptyParameters()
            superclass()
        }.invoke()
    }

    fun Any.show(): Any? {
        return asResolver().firstMethod {
            name = "show"
            emptyParameters()
        }.invoke()
    }

    fun Any.findViewById(id: Int): View? {
        return asResolver().firstMethod {
            name = "findViewById"
            parameters(Int::class)
            superclass()
        }.invoke<View>(id)
    }

    @SuppressLint("DiscouragedApi")
    fun Any.findViewById(idName: String): View? {
        val id = context.resources.getIdentifier(idName, "id", context.packageName)
        return asResolver().firstMethod {
            name = "findViewById"
            parameters(Int::class)
            superclass()
        }.invoke<View>(id)
    }

    fun Any.getEditText(idName: String): EditText? {
        return findViewById(idName)?.asResolver()?.firstMethod {
            name = "getEditText"
            emptyParameters()
        }?.invoke<EditText>()
    }

    @SuppressLint("DiscouragedApi")
    fun getStyle(name: String): Int {
        if (name.isBlank()) return 0
        return context.resources.getIdentifier(name.let {
            it.takeIf { e -> e.contains("_") }?.replace("_", ".") ?: it
        }, "style", context.packageName)
    }

    val styleList = arrayOf(
        "COUIAlertDialog_Center", //居中
        "COUIAlertDialog_SingleInput" //单输入框
    )
}