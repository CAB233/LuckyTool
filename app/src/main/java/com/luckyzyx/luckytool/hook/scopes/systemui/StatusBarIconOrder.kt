package com.luckyzyx.luckytool.hook.scopes.systemui

import android.os.Handler
import android.os.Looper
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.toClassOrNull
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 状态栏图标位置排序。
 *
 * 宿主图标顺序的唯一来源是 `StatusBarIconList.mSlots`（`ArrayList<Slot>`，下标即顺序）：
 * - `StatusBarIconList(String[])` 构造器按 `config_statusBarIcons` 数组逐项 `mSlots.add(new Slot(name))`；
 * - `mViewOnlySlots` 是 `mSlots` 的 `unmodifiableList` 视图，**原地重排 `mSlots` 立即对 `getSlots()` 生效**；
 * - `StatusBarIconControllerImpl.addIconGroup` / `setIcon` 都按 `getSlots()` 顺序调用
 *   `getViewIndex(tag, slot)` 决定图标 view 的插入下标，因此重排 `mSlots` 即改变图标顺序。
 *
 * 初始顺序在 `StatusBarIconList` 构造器返回后确定（此时 `StatusBarIconControllerImpl` 尚未创建）；
 * 运行期改动由 dataChannel 推送，重排后调用 `refreshStatusBarIconList()` 重建图标 view 立即生效。
 */
@Obfuscate
object StatusBarIconOrder : YukiBaseHooker() {
    private const val KEY = "statusbar_icon_order"

    /** 总开关：关闭时不装载任何 hook（与页面上的开关同名） */
    private const val ENABLE_KEY = "statusbar_icon_order_enabled"

    /** A15+ 把 StatusBarIconList/StatusBarIconControllerImpl 挪到 phone.ui 包，两代名字都兼容 */
    private val iconListClassNames = arrayOf(
        "com.android.systemui.statusbar.phone.ui.StatusBarIconList",
        "com.android.systemui.statusbar.phone.StatusBarIconList",
    )
    private val iconControllerClassNames = arrayOf(
        "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
        "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
    )

    /** 用户配置的顺序（空 = 宿主默认顺序） */
    @Volatile
    private var userOrder: List<String> = emptyList()

    /** 构造时捕获的宿主默认顺序：未收录槽位的回落顺序，也是「恢复默认」的依据 */
    @Volatile
    private var defaultOrder: List<String> = emptyList()

    // StatusBarIconList / StatusBarIconControllerImpl 与 SystemUI 进程同生命周期，强引用不会泄漏
    private var iconList: Any? = null
    private var iconController: Any? = null

    override fun onHook() {
        // 需要重启作用域才会装载/卸载本 hooker
        if (!preferences(ModulePrefs).getBoolean(ENABLE_KEY, false)) return
        userOrder = parseOrder(preferences(ModulePrefs).getString(KEY, ""))
        hookIconList()
        hookIconController()
        dataChannel.wait<String>(KEY) { value ->
            userOrder = parseOrder(value)
            // 重排 + refreshStatusBarIconList 都要在主线程做
            Handler(Looper.getMainLooper()).post { applyOrder(iconList, refresh = true) }
        }
    }

    private fun hookIconList() {
        val clazz = classOrNull(*iconListClassNames) ?: return
        clazz.resolve().apply {
            firstConstructor { parameterCount = 1 }.hook {
                after {
                    val list = instance
                    iconList = list
                    defaultOrder = namesOf(list)
                    applyOrder(list, refresh = false)
                }
            }
        }
    }

    private fun hookIconController() {
        val clazz = classOrNull(*iconControllerClassNames) ?: return
        clazz.resolve().apply {
            // 构造器最早拿到实例；参数个数按版本放宽（A13 ~ A15 之间在 8~11 之间浮动）
            firstConstructor { parameterCount { it in 8..11 } }.hook {
                after { iconController = instance }
            }
            // addIconGroup 在状态栏初始化时必然被调用一次；setIcon 兜底（模块晚加载时）
            firstMethodOrNull { name = "addIconGroup" }?.hook {
                before { iconController = instance }
            }
            firstMethodOrNull { name = "setIcon" }?.hook {
                before { if (iconController == null) iconController = instance }
            }
        }
    }

    /** 按 [userOrder] 原地重排 `mSlots`；未收录的槽位按捕获的宿主默认顺序稳定排在其后 */
    private fun applyOrder(list: Any?, refresh: Boolean) {
        val slots = (list ?: ensureIconList())?.let { slotsOf(it) } ?: return
        val rank = HashMap<String, Int>(userOrder.size)
        userOrder.forEachIndexed { index, name -> rank[name] = index }
        val fallback = HashMap<String, Int>(defaultOrder.size)
        defaultOrder.forEachIndexed { index, name -> fallback[name] = index }

        val comparator = Comparator<Any?> { left, right ->
            val nameLeft = left?.let { slotName(it) }
            val nameRight = right?.let { slotName(it) }
            if (nameLeft == null || nameRight == null) return@Comparator 0
            val rankLeft = rank[nameLeft]
            val rankRight = rank[nameRight]
            when {
                rankLeft != null && rankRight != null -> rankLeft - rankRight
                rankLeft != null -> -1
                rankRight != null -> 1
                else -> (fallback[nameLeft] ?: Int.MAX_VALUE) - (fallback[nameRight] ?: Int.MAX_VALUE)
            }
        }
        // java.util.List.sort 是稳定排序：同序槽位保持原有相对位置
        slots.sortWith(comparator)

        if (refresh) refreshIcons()
    }

    /** 重建全部图标 view（宿主 OplusStatusBarIconControllerEx 会在前后保存/恢复图标状态） */
    private fun refreshIcons() {
        val controller = iconController ?: return
        val method = findMethod(controller.javaClass, "refreshStatusBarIconList")
        if (method == null) {
            YLog.debug("StatusBarIconOrder: refreshStatusBarIconList not found, restart SystemUI to apply the order")
            return
        }
        runCatching { method.invoke(controller) }
            .onFailure { YLog.debug("StatusBarIconOrder: refreshStatusBarIconList failed -> $it") }
    }

    /**
     * 兜底获取 `StatusBarIconList`：模块晚加载时 `StatusBarIconList` 的构造器 hook 不会触发，
     * 但 `StatusBarIconControllerImpl.mStatusBarIconList` 是 public 字段，可直接取。
     */
    private fun ensureIconList(): Any? {
        iconList?.let { return it }
        val controller = iconController ?: return null
        val list = runCatching {
            findField(controller.javaClass, "mStatusBarIconList")?.get(controller)
        }.getOrNull() ?: return null
        iconList = list
        if (defaultOrder.isEmpty()) defaultOrder = namesOf(list)
        return list
    }

    private fun namesOf(list: Any): List<String> =
        slotsOf(list)?.mapNotNull { slot -> slot?.let { slotName(it) } } ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun slotsOf(list: Any): MutableList<Any?>? = runCatching {
        findField(list.javaClass, "mSlots")?.get(list) as? MutableList<Any?>
    }.getOrNull()

    /** Slot 是宿主内部类，直接按字段名反射取值，避免写死 `StatusBarIconList$Slot` */
    private fun slotName(slot: Any): String? = runCatching {
        findField(slot.javaClass, "mName")?.get(slot) as? String
    }.getOrNull()

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (true) {
            val target = current ?: return null
            val field = runCatching {
                target.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()
            if (field != null) return field
            current = target.superclass
        }
    }

    /** 无参方法：`refreshStatusBarIconList()` 在 oplus 实现里是 public final，无重载 */
    private fun findMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (true) {
            val target = current ?: return null
            val method = runCatching {
                target.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
                    ?.apply { isAccessible = true }
            }.getOrNull()
            if (method != null) return method
            current = target.superclass
        }
    }

    private fun classOrNull(vararg names: String): Class<*>? =
        names.firstNotNullOfOrNull { runCatching { it.toClassOrNull() }.getOrNull() }

    private fun parseOrder(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
