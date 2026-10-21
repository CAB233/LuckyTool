package com.luckyzyx.luckytool.ui.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Real pointer dispatch with bounded frames; no device or emulator is required. */
internal class ComposeGestureHost(savedState: Bundle? = null) : AutoCloseable {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        .create(savedState).start().resume().visible()
    val activity: ComponentActivity = controller.get()
    val view = ComposeView(activity)
    private val main = shadowOf(Looper.getMainLooper())
    private var closed = false

    init {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        activity.setContentView(view)
    }

    fun setContent(content: @Composable () -> Unit) {
        view.setContent(content)
        advance(128)
        if (activity.window.decorView.isAttachedToWindow) controller.windowFocusChanged(true)
        advance(128)
    }

    fun advance(millis: Int = 1600) {
        repeat((millis + 15) / 16) {
            Snapshot.sendApplyNotifications()
            main.idleFor(16, TimeUnit.MILLISECONDS)
            view.getChildAt(0)?.forceLayout()
            view.forceLayout()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(822, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1760, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 822, 1760)
        }
    }

    fun drag(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        holdBeforeUp: Boolean = true,
        settle: Boolean = true,
        cancel: Boolean = false,
    ) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
            advance(16)
        }
        send(MotionEvent.ACTION_DOWN, fromX, fromY)
        repeat(12) { index ->
            val fraction = (index + 1) / 12f
            send(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * fraction, fromY + (toY - fromY) * fraction)
        }
        if (holdBeforeUp) advance(160)
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, toX, toY)
        if (settle) advance()
    }

    fun nodes(): List<SemanticsNode> {
        val result = mutableListOf<SemanticsNode>()
        fun collect(node: SemanticsNode) {
            result += node
            node.children.forEach(::collect)
        }
        fun visit(target: View) {
            val getter = target.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
            (getter?.invoke(target) as? SemanticsOwner)?.let { collect(it.unmergedRootSemanticsNode) }
            if (target is ViewGroup) repeat(target.childCount) { visit(target.getChildAt(it)) }
        }
        WindowInspector.getGlobalWindowViews().forEach(::visit)
        return result
    }

    fun textNode(text: String): SemanticsNode? = nodes().firstOrNull { node ->
        node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0 &&
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }

    fun clickText(text: String) {
        var node = requireNotNull(textNode(text)) { "Missing text: $text" }
        while (node.config.getOrNull(SemanticsActions.OnClick)?.action == null) {
            node = requireNotNull(node.parent) { "No click action for $text" }
        }
        check(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
    }

    fun snapshot(name: String) {
        val folder = System.getProperty("pager.preview.output") ?: return
        val bitmap = Bitmap.createBitmap(822, 1760, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        val origin = IntArray(2).also(view::getLocationOnScreen)
        WindowInspector.getGlobalWindowViews().filter { it !== view.rootView && it.isShown }.forEach { popup ->
            val location = IntArray(2).also(popup::getLocationOnScreen)
            val checkpoint = canvas.save()
            canvas.translate((location[0] - origin[0]).toFloat(), (location[1] - origin[1]).toFloat())
            popup.draw(canvas)
            canvas.restoreToCount(checkpoint)
        }
        File(folder, "$name.png").apply { parentFile.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    fun saveAndClose(): Bundle {
        val bundle = Bundle()
        controller.pause().saveInstanceState(bundle).stop().destroy()
        closed = true
        main.idle()
        return bundle
    }

    override fun close() {
        if (!closed) {
            controller.pause().stop().destroy()
            closed = true
            main.idle()
        }
    }
}
