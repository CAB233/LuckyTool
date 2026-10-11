package com.luckyzyx.luckytool.hook.scopes.systemui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.toClassOrNull
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 状态栏图标位置排序（左 / 右两区域，区域内可排序、可跨区搬运）。
 *
 * ## 顺序（宿主唯一来源）
 * `StatusBarIconList.mSlots`（`ArrayList<Slot>`，下标即顺序）：
 * - `StatusBarIconList(String[])` 构造器按 `config_statusBarIcons` 逐项 `mSlots.add(new Slot(name))`；
 * - `mViewOnlySlots` 是 `mSlots` 的 `unmodifiableList` 视图，**原地重排 `mSlots` 立即对 `getSlots()` 生效**；
 * - 图标 view 的插入下标由 `getViewIndex(tag, slot)` 从 `mSlots` 顺序推出来，所以重排 `mSlots` 即改变顺序。
 *
 * 本模块把 `mSlots` 排成 `[左区槽位…] + [右区槽位…] + [未收录槽位（宿主默认顺序）]`，
 * 于是「虚拟顺序」既是宿主索引空间，也是每个容器内部的显示顺序：
 * 左区容器里只会有左区图标（按左区顺序），右区容器里只会有右区图标（按右区顺序）。
 *
 * ## 左 / 右分区（宿主没有原生机制，只能自己搬 view）
 * 宿主 `IconManager.mGroup` 的子 view 必须与「当前存在的图标的全局索引空间」严格一一对应：
 * `onSetIcon(i)` / `onRemoveIcon(i)` 都直接 `mGroup.getChildAt(i)` / `removeViewAt(i)`，
 * 因此**把 view 搬出 mGroup 会让索引错位**（取到错误的 view 甚至 IndexOutOfBoundsException）。
 *
 * 解法：hook `DarkIconManager` 构造器，把第一个参数（真实的 `statusIcons` 容器）换成
 * [ZoneIconGroup]——一个只记录虚拟子 view 列表、并把每个图标按槽位转发到左/右真实容器的
 * `LinearLayout`。它对宿主而言仍是 `LinearLayout`（`createDemoStatusIcons` 会强转），
 * `getChildAt(i)` 永远返回正确的图标 view（不论其真实父容器在哪），`addView` 仍按宿主给的全局下标入列。
 * 虚拟容器本身不在布局树里，不参与测量。
 *
 * ## 时钟
 * 时钟（`clock_for_fake`）不是槽位，它是左侧容器 `status_bar_start_side_except_heads_up` 里的独立 view。
 * 左区顺序里的 `clock` 伪条目代表它：排在它前面的左区图标放进 `clock` 之前的容器，
 * 排在它后面的放进 `clock` 之后的容器，于是时钟可以停在左区的任意位置。
 *
 * 运行期改动由 dataChannel 推送，重排后调用 `refreshStatusBarIconList()` 重建图标 view 立即生效。
 */
@Obfuscate
object StatusBarIconOrder : YukiBaseHooker() {

    /** 右区顺序（逗号分隔的槽位名；空 = 宿主默认顺序） */
    private const val RIGHT_KEY = "statusbar_icon_order"

    /** 左区顺序（逗号分隔的槽位名，可含 `clock` 伪条目；空 = 仅时钟在左侧） */
    private const val LEFT_KEY = "statusbar_icon_order_left"

    /** 总开关：关闭时不装载任何 hook（与页面上的开关同名） */
    private const val ENABLE_KEY = "statusbar_icon_order_enabled"

    /** 时钟伪条目：代表 `clock_for_fake`，只能待在左区 */
    private const val CLOCK_ITEM = "clock"

    /** ColorOS 的状态栏根布局（StatusBarRoot 里 inflate `R.layout.status_bar` 的那个 View） */
    private const val STATUS_BAR_VIEW_CLASS = "com.android.systemui.statusbar.phone.PhoneStatusBarView"

    /** A15+ 把 StatusBarIconList/StatusBarIconControllerImpl 挪到 phone.ui 包，两代名字都兼容 */
    private val iconListClassNames = arrayOf(
        "com.android.systemui.statusbar.phone.ui.StatusBarIconList",
        "com.android.systemui.statusbar.phone.StatusBarIconList",
    )
    private val iconControllerClassNames = arrayOf(
        "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
        "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
    )

    /** 持有 `mGroup` 的图标管理器（状态栏用的是 DarkIconManager；shade / keyguard 另有实例） */
    private val iconManagerClassNames = arrayOf(
        "com.android.systemui.statusbar.phone.ui.DarkIconManager",
        "com.android.systemui.statusbar.phone.DarkIconManager",
    )

    /** 左区顺序（含 `clock`） */
    @Volatile
    private var leftOrder: List<String> = listOf(CLOCK_ITEM)

    /** 右区顺序（不含左区槽位） */
    @Volatile
    private var rightOrder: List<String> = emptyList()

    /** 构造时捕获的宿主默认顺序：未收录槽位的回落顺序 */
    @Volatile
    private var defaultOrder: List<String> = emptyList()

    // StatusBarIconList / StatusBarIconControllerImpl / PhoneStatusBarView 与 SystemUI 进程同生命周期，强引用不会泄漏
    private var iconList: Any? = null
    private var iconController: Any? = null
    private var statusBarView: View? = null

    /** 状态栏自己的 `statusIcons` 容器（用于分辨哪个 IconManager 属于状态栏） */
    private var statusIconsView: ViewGroup? = null

    /** 被替换过 `mGroup` 的图标管理器（运行期重新对账用） */
    private val zoneGroups = ArrayList<ZoneIconGroup>(2)

    /** 左区图标容器：时钟之前 / 时钟之后，只在左区有系统图标时才挂到布局上 */
    private var leftBeforeGroup: LinearLayout? = null
    private var leftAfterGroup: LinearLayout? = null

    /** 左区是否生效（有系统图标 + 容器已就绪 + 拿到的是状态栏容器） */
    @Volatile
    private var leftActive = false

    private val slotMethodCache = HashMap<Class<*>, Method?>(8)

    override fun onHook() {
        // 需要重启作用域才会装载/卸载本 hooker
        if (!preferences(ModulePrefs).getBoolean(ENABLE_KEY, false)) return
        leftOrder = normalizeLeft(parseOrder(preferences(ModulePrefs).getString(LEFT_KEY, "")))
        rightOrder = parseOrder(preferences(ModulePrefs).getString(RIGHT_KEY, ""))
        hookIconList()
        hookIconController()
        hookIconManager()
        hookStatusBarView()
        // 重排 + refreshStatusBarIconList 都要在主线程做
        dataChannel.wait<String>(RIGHT_KEY) { value ->
            rightOrder = parseOrder(value)
            Handler(Looper.getMainLooper()).post { applyZones(refresh = true) }
        }
        dataChannel.wait<String>(LEFT_KEY) { value ->
            leftOrder = normalizeLeft(parseOrder(value))
            Handler(Looper.getMainLooper()).post { applyZones(refresh = true) }
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

    /**
     * 把 `DarkIconManager.mGroup` 换成虚拟容器。
     *
     * 直接替换构造器第一个参数（而不是反射写 final 字段）：`mGroup = viewGroup` 仍是宿主自己的赋值，
     * 不依赖 final 字段的可写性，也不受 AOT 内联影响。原始容器留作右区容器。
     */
    private fun hookIconManager() {
        val clazz = classOrNull(*iconManagerClassNames) ?: return
        clazz.resolve().apply {
            firstConstructor { parameterCount { it in 7..10 } }.hook {
                before {
                    val original = args.getOrNull(0) as? ViewGroup ?: return@before
                    val group = ZoneIconGroup(original.context, original) { child, index, params ->
                        attachChild(child, index, params)
                    }
                    zoneGroups += group
                    args[0] = group
                }
            }
        }
    }

    /** 状态栏布局 inflate 完成后才能拿到左侧容器 / 时钟 / 状态栏自己的 statusIcons */
    private fun hookStatusBarView() {
        val clazz = classOrNull(STATUS_BAR_VIEW_CLASS) ?: return
        clazz.resolve().apply {
            firstMethod { name = "onFinishInflate" }.hook {
                after {
                    val view = instance<View>()
                    statusBarView = view
                    runCatching { applyZones(refresh = false) }
                        .onFailure { YLog.debug("StatusBarIconOrder: layout apply failed -> $it") }
                }
            }
        }
    }

    /**
     * 重新对账：先按区域把图标 view 摆到对应容器，再把左区容器挂到时钟两侧。
     *
     * @param refresh true 时额外重建图标 view（顺序变化必须重建：虚拟顺序来自宿主的插入顺序）
     */
    private fun applyZones(refresh: Boolean) {
        applyOrder(iconList, refresh = false)
        ensureLeftGroups()
        reconcile()
        if (refresh) refreshIcons()
    }

    /** 解析状态栏自己的 `statusIcons`，并准备左区容器（只在左区有系统图标时才挂上去） */
    private fun ensureLeftGroups() {
        val root = statusBarView ?: return
        val systemIcons = root.findViewById<View>(idOf(root, "system_icons"))
        statusIconsView = systemIcons?.findViewById(idOf(root, "statusIcons"))
        if (!leftOrder.any { it != CLOCK_ITEM }) return
        if (leftBeforeGroup == null) {
            leftBeforeGroup = createLeftGroup(root.context)
            leftAfterGroup = createLeftGroup(root.context)
        }
    }

    private fun createLeftGroup(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        ).apply { gravity = Gravity.CENTER_VERTICAL }
    }

    /** 把左区容器摆到时钟两侧：`[左区(时钟前)][时钟][左区(时钟后)][通知图标组]` */
    private fun attachLeftGroups() {
        val root = statusBarView ?: return
        val startSide = root.findViewById<ViewGroup>(idOf(root, "status_bar_start_side_except_heads_up"))
            ?: return
        val before = leftBeforeGroup
        val after = leftAfterGroup
        if (before == null || after == null) return
        val clock = startSide.findViewById<View>(idOf(root, "clock_for_fake")) ?: return
        // 只处理直接的子 view（宿主改结构时不动，避免破坏其它联动）
        if (clock.parent !== startSide) return

        if (before.parent === startSide) startSide.removeView(before)
        if (after.parent === startSide) startSide.removeView(after)
        startSide.addView(before, startSide.indexOfChild(clock).coerceIn(0, startSide.childCount))
        startSide.addView(after, (startSide.indexOfChild(clock) + 1).coerceIn(0, startSide.childCount))
    }

    private fun detachLeftGroups() {
        val root = statusBarView ?: return
        (leftBeforeGroup?.parent as? ViewGroup)?.removeView(leftBeforeGroup)
        (leftAfterGroup?.parent as? ViewGroup)?.removeView(leftAfterGroup)
    }

    /** 某个图标 view 应该放进哪个容器；null = 右区容器 */
    private fun leftTargetOf(child: View): ViewGroup? {
        if (!leftActive) return null
        if (slotOf(child) !in leftOrder) return null
        val clockIndex = leftOrder.indexOf(CLOCK_ITEM)
        val itemIndex = leftOrder.indexOf(slotOf(child))
        val before = leftBeforeGroup ?: return null
        val after = leftAfterGroup ?: return null
        return if (clockIndex >= 0 && itemIndex > clockIndex) after else before
    }

    /** 把整个虚拟列表按当前区域配置摆好（先把要换容器的摘掉，再按顺序放回去） */
    private fun reconcile() {
        val statusIcons = statusIconsView
        leftActive = leftBeforeGroup != null && statusIcons != null &&
            zoneGroups.any { it.rightGroup === statusIcons }
        for (group in zoneGroups) reorderGroup(group)
        val needAttach = leftActive
        if (needAttach) attachLeftGroups() else detachLeftGroups()
    }

    private fun reorderGroup(group: ZoneIconGroup) {
        val containers = ArrayList<ViewGroup>(3)
        containers += group.rightGroup
        leftBeforeGroup?.let { containers += it }
        leftAfterGroup?.let { containers += it }

        // 1) 计算每个容器期望的子 view 顺序
        val desired = HashMap<ViewGroup, MutableList<View>>(4)
        for (child in group.snapshot()) {
            val target = leftTargetOf(child) ?: group.rightGroup
            desired.getOrPut(target) { ArrayList(4) }.add(child)
        }
        // 2) 先把不属于该容器的子 view 摘掉（re-parent 前必须先解除旧父容器）
        for (container in containers) {
            val want = desired[container] ?: emptyList()
            for (child in childrenOf(container)) if (child !in want) container.removeView(child)
        }
        // 3) 再按期望顺序依次摆好
        for (container in containers) {
            val want = desired[container] ?: continue
            for (index in want.indices) {
                val child = want[index]
                if (container.getChildAt(index) === child) continue
                (child.parent as? ViewGroup)?.removeView(child)
                runCatching {
                    container.addView(child, index.coerceIn(0, container.childCount), child.layoutParams)
                }
            }
        }
    }

    /** 虚拟容器收到子 view 时：记进虚拟列表，然后按区域转发到真实容器 */
    private fun attachChild(child: View, virtualIndex: Int, params: ViewGroup.LayoutParams?) {
        val group = zoneGroups.firstOrNull { it.contains(child) } ?: return
        val target = leftTargetOf(child) ?: group.rightGroup
        val index = indexWithin(group, virtualIndex, target)
        runCatching {
            target.addView(child, index.coerceIn(0, target.childCount), params ?: child.layoutParams)
        }.onFailure {
            YLog.debug("StatusBarIconOrder: attach icon failed -> $it")
            runCatching { group.rightGroup.addView(child, params ?: child.layoutParams) }
        }
    }

    /** 虚拟列表中排在该图标之前、且同属 [target] 的图标个数 = 目标容器内的插入下标 */
    private fun indexWithin(group: ZoneIconGroup, virtualIndex: Int, target: ViewGroup): Int {
        var count = 0
        val children = group.virtualChildren
        for (index in 0 until virtualIndex.coerceAtMost(children.size)) {
            val sibling = children[index]
            if ((leftTargetOf(sibling) ?: group.rightGroup) === target) count++
        }
        return count
    }

    private fun childrenOf(group: ViewGroup): List<View> =
        (0 until group.childCount).mapNotNull { group.getChildAt(it) }

    /** 宿主资源 id 只能按名字取（模块里没有 com.android.systemui 的 R） */
    private fun idOf(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", view.context.packageName)

    /** 图标 view 自报槽位名（`StatusIconDisplayable.getSlot()`） */
    private fun slotOf(view: View): String? = runCatching {
        val clazz = view.javaClass
        if (!slotMethodCache.containsKey(clazz)) {
            slotMethodCache[clazz] = clazz.getMethod("getSlot")
        }
        slotMethodCache[clazz]?.invoke(view) as? String
    }.getOrNull()

    /**
     * 按区域顺序原地重排 `mSlots`：左区在前、右区在后，未收录槽位按宿主默认顺序稳定排在最后。
     *
     * 左区在前是为了让左区图标按左区顺序进入虚拟列表；两区各自的相对顺序都会原样保留。
     */
    private fun applyOrder(list: Any?, refresh: Boolean) {
        val slots = (list ?: ensureIconList())?.let { slotsOf(it) } ?: return
        val rank = HashMap<String, Int>(leftOrder.size + rightOrder.size)
        var next = 0
        leftOrder.forEach { if (it != CLOCK_ITEM && rank.put(it, next) == null) next++ }
        rightOrder.forEach { if (it != CLOCK_ITEM && rank.put(it, next) == null) next++ }
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
            YLog.debug("StatusBarIconOrder: refreshStatusBarIconList not found, restart SystemUI to apply")
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
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

    /** 左区顺序永远包含时钟（时钟不支持搬到右侧区域） */
    private fun normalizeLeft(order: List<String>): List<String> =
        if (order.contains(CLOCK_ITEM)) order else listOf(CLOCK_ITEM) + order
}

/**
 * 虚拟图标容器：只维护「虚拟子 view 列表」，每个图标按槽位被转发到左区或右区真实容器，
 * 自己从不持有真实子 view，也不在布局树里。
 *
 * 宿主 `IconManager` 对 `mGroup` 的所有用法都能被满足：
 * - `addView(view, index, params)`：按宿主给的全局下标入列，再转发到目标容器；
 * - `getChildAt(i)` / `getChildCount()` / `indexOfChild(view)`：返回虚拟列表，索引空间与宿主一致；
 * - `removeViewAt(i)` / `removeView(view)` / `removeAllViews()`：同步从真实容器摘除。
 */
private class ZoneIconGroup(
    context: Context,
    /** 宿主原本的图标容器（右区容器，仍在布局树里） */
    val rightGroup: ViewGroup,
    private val onAttach: (View, Int, ViewGroup.LayoutParams?) -> Unit,
) : LinearLayout(context) {

    val virtualChildren = ArrayList<View>(16)

    fun snapshot(): List<View> = ArrayList(virtualChildren)

    fun contains(child: View): Boolean = virtualChildren.contains(child)

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        val at = (if (index < 0) virtualChildren.size else index).coerceIn(0, virtualChildren.size)
        virtualChildren.add(at, child)
        onAttach(child, at, params)
    }

    override fun getChildAt(index: Int): View? = virtualChildren.getOrNull(index)

    override fun getChildCount(): Int = virtualChildren.size

    override fun indexOfChild(child: View): Int = virtualChildren.indexOf(child)

    override fun removeView(child: View) {
        virtualChildren.remove(child)
        (child.parent as? ViewGroup)?.removeView(child)
    }

    override fun removeViewAt(index: Int) {
        val child = virtualChildren.getOrNull(index) ?: return
        virtualChildren.removeAt(index)
        (child.parent as? ViewGroup)?.removeView(child)
    }

    override fun removeViews(start: Int, count: Int) {
        for (index in (start + count - 1) downTo start) removeViewAt(index)
    }

    override fun removeAllViews() {
        for (child in snapshot()) (child.parent as? ViewGroup)?.removeView(child)
        virtualChildren.clear()
    }
}
