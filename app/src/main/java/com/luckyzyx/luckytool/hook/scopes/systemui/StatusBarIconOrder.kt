package com.luckyzyx.luckytool.hook.scopes.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.makeAccessible
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.hook.scopes.systemui.StatusBarIconOrder.leftBeforeGroup
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
 * 顺序里的 `clock` 伪条目代表它：
 * - 时钟在左区：排在它前面的左区图标放进 `clock` 之前的容器，后面的放进 `clock` 之后的容器，
 *   于是时钟可以停在左区的任意位置；
 * - 时钟搬到右区：左区图标留在原处（时钟原来的位置由左区容器接替），`system_icons` 内部变成
 *   `[右区(时钟前)][时钟][statusIcons][电池]`，于是时钟也能停在右区的任意位置。
 *
 * 运行期改动由 dataChannel 推送，重排后调用 `refreshStatusBarIconList()` 重建图标 view 立即生效。
 */
@SuppressLint("StaticFieldLeak")
@Obfuscate
object StatusBarIconOrder : YukiBaseHooker() {

    /** 右区顺序（逗号分隔的槽位名；空 = 宿主默认顺序） */
    private const val RIGHT_KEY = "statusbar_icon_order_right"

    /** 左区顺序（逗号分隔的槽位名，可含 `clock` 伪条目；空 = 仅时钟在左侧） */
    private const val LEFT_KEY = "statusbar_icon_order_left"

    /** 总开关：关闭时不装载任何 hook（与页面上的开关同名） */
    private const val ENABLE_KEY = "statusbar_icon_order_enabled"

    /** 时钟伪条目：代表 `clock_for_fake`，可以在左区，也可以搬到右区 */
    private const val CLOCK_ITEM = "clock"

    /** ColorOS 的状态栏根布局（StatusBarRoot 里 inflate `R.layout.status_bar` 的那个 View） */
    private const val STATUS_BAR_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.PhoneStatusBarView"

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

    /** 左区顺序（含 `clock` 时表示时钟也在左区） */
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var leftOrder: List<String> = listOf(CLOCK_ITEM)

    /** 右区顺序（含 `clock` 时表示时钟被搬到右区） */
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

    /** 左区图标容器：时钟之前 / 时钟之后（时钟不在左区时只用前者装整个左区） */
    private var leftBeforeGroup: LinearLayout? = null
    private var leftAfterGroup: LinearLayout? = null

    /** 右区「时钟之前」容器：时钟搬到右区时插进 `system_icons` 的 `statusIcons` 之前 */
    private var endBeforeGroup: LinearLayout? = null

    /** 时钟原始布局参数：搬回左区时原样还原 */
    private var clockParams: ViewGroup.LayoutParams? = null

    /** 时钟在左侧容器里的原始下标：-1 = 还没记录过（时钟搬到右区后位置由左区容器接替） */
    private var clockStartIndex = -1

    /** 时钟视图是否可用并参与分区：false 时左区退化成单容器（所有左区图标进 [leftBeforeGroup]） */
    @Volatile
    private var clockAttached = false

    /** 两区容器是否生效（容器已就绪 + 拿到的是状态栏容器） */
    @Volatile
    private var zonesActive = false

    private val slotMethodCache = HashMap<Class<*>, Method?>(8)

    override fun onHook() {
        // 需要重启作用域才会装载/卸载本 hooker
        if (!preferences(ModulePrefs).getBoolean(ENABLE_KEY, false)) return
        // 装载时先把偏好读进来：**重启作用域后不需要任何 dataChannel 推送，按存好的顺序生效**
        //（dataChannel 只负责「运行期改一次就实时生效」，不是唯一入口）
        val initial = normalizeOrders(
            parseOrder(preferences(ModulePrefs).getString(LEFT_KEY, "")),
            parseOrder(preferences(ModulePrefs).getString(RIGHT_KEY, "")),
        )
        leftOrder = initial.first
        rightOrder = initial.second
        YLog.debug("StatusBarIconOrder: loaded left=${leftOrder} right=${rightOrder}")
        hookIconList()
        hookIconController()
        hookIconManager()
        hookStatusBarView()
        // 重排 + refreshStatusBarIconList 都要在主线程做
        dataChannel.wait<String>(RIGHT_KEY) { value ->
            val orders = normalizeOrders(leftOrder, parseOrder(value), PushSide.RIGHT)
            leftOrder = orders.first
            rightOrder = orders.second
            Handler(Looper.getMainLooper()).post { applyZones(refresh = true) }
        }
        dataChannel.wait<String>(LEFT_KEY) { value ->
            val orders = normalizeOrders(parseOrder(value), rightOrder, PushSide.LEFT)
            leftOrder = orders.first
            rightOrder = orders.second
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
                    // 布局装好后再按偏好重建一次图标：**重启作用域后不需要任何推送**，
                    // 直接按存好的左右区顺序生效（与 dataChannel 运行期推送走同一条 applyZones）
                    Handler(Looper.getMainLooper()).post {
                        // 再读一次偏好：装载到 inflate 之间若又改过，以最新偏好为准（prefs 是唯一真相）
                        val orders = normalizeOrders(
                            parseOrder(preferences(ModulePrefs).getString(LEFT_KEY, "")),
                            parseOrder(preferences(ModulePrefs).getString(RIGHT_KEY, "")),
                        )
                        leftOrder = orders.first
                        rightOrder = orders.second
                        YLog.debug("StatusBarIconOrder: startup apply left=${leftOrder} right=${rightOrder}")
                        runCatching { applyZones(refresh = true) }
                            .onFailure { YLog.debug("StatusBarIconOrder: startup apply failed -> $it") }
                    }
                }
            }
        }
    }

    /**
     * 重新对账：先按区域把图标 view 摆到对应容器，再把容器与时钟摆回布局树里的位置。
     *
     * @param refresh true 时额外重建图标 view（顺序变化必须重建：虚拟顺序来自宿主的插入顺序）
     */
    private fun applyZones(refresh: Boolean) {
        // hook 里任何异常都不能带走 SystemUI（宿主结构不符时只记日志、保持原样）
        runCatching {
            applyOrder(iconList, refresh = false)
            ensureGroups()
            reconcile()
            if (refresh) refreshIcons()
        }.onFailure { YLog.debug("StatusBarIconOrder: applyZones failed -> $it") }
    }

    /** 解析状态栏自己的 `statusIcons`，并准备两区容器（按需才会挂到布局上） */
    private fun ensureGroups() {
        val root = statusBarView ?: return
        val systemIcons = root.findViewById<View>(idOf(root, "system_icons"))
        statusIconsView = systemIcons?.findViewById(idOf(root, "statusIcons"))
        if (leftBeforeGroup == null) {
            leftBeforeGroup = createZoneGroup(root.context)
            leftAfterGroup = createZoneGroup(root.context)
        }
        if (endBeforeGroup == null) endBeforeGroup = createZoneGroup(root.context)
    }

    private fun createZoneGroup(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        ).apply { gravity = Gravity.CENTER_VERTICAL }
    }

    /** 时钟是否在左区（否则在右区） */
    private fun clockInStartSide(): Boolean = leftOrder.contains(CLOCK_ITEM)

    /**
     * 把容器与时钟摆到位。
     *
     * - 时钟在左区：`[左区(时钟前)][时钟][左区(时钟后)][通知图标组]`，右区容器就是宿主 `statusIcons`；
     * - 时钟在右区：左区只用一个容器接替时钟原来的位置，宿主 `system_icons` 内变成
     *   `[右区(时钟前)][时钟][statusIcons][电池]`，因此时钟可以插在任意两个右侧图标之间；
     * - **时钟视图拿不到**（宿主换实现 / 结构变化）：退化成「只有左区容器」——左区图标照样能搬过去，
     *   只是无法按时钟拆左右、也搬不动时钟；总比整个分区失效好（之前这种情况会让分区整体失效，
     *   表现为「图标只能在右区排序、搬不进左区」）。
     *
     * `system_icons` 仍是 `status_bar_end_side_content` 的唯一子 view，因此右区整体布局逻辑不变。
     *
     * @return 分区容器是否摆好；false 表示宿主结构完全不符（调用方回落到「不分区」）
     */
    private fun placeGroups(): Boolean {
        val root = statusBarView ?: return false
        val startSide =
            root.findViewById<ViewGroup>(idOf(root, "status_bar_start_side_except_heads_up"))
        val before = leftBeforeGroup
        val after = leftAfterGroup
        val endBefore = endBeforeGroup
        if (startSide == null || before == null || after == null || endBefore == null) return false
        val systemIcons = root.findViewById<ViewGroup>(idOf(root, "system_icons"))
        val clock = root.findViewById<View>(idOf(root, "clock_for_fake"))
        detachViews(before, after, endBefore)
        if (systemIcons == null || clock == null) {
            YLog.debug("StatusBarIconOrder: clock/system_icons missing, left zone degrades to one container")
            clockAttached = false
            startSide.addView(before, 0)
            return true
        }
        clockAttached = true
        if (clockParams == null) clockParams = clock.layoutParams
        // 记录时钟在左侧容器里的原始下标：搬到右区后要由左区容器接替这个位置，搬回左区也放回原处
        if (clockStartIndex < 0 && clock.parent === startSide) clockStartIndex =
            startSide.indexOfChild(clock)
        // 宿主改结构时不动：时钟只认状态栏左侧容器 / system_icons 两种父容器
        val clockParent = clock.parent as? ViewGroup
        if (clockParent != null && clockParent !== startSide && clockParent !== systemIcons) {
            YLog.debug("StatusBarIconOrder: clock parent unexpected -> $clockParent")
            clockAttached = false
            startSide.addView(before, 0)
            return true
        }
        if (clockInStartSide()) {
            if (clock.parent !== startSide) {
                (clock.parent as? ViewGroup)?.removeView(clock)
                startSide.addView(
                    clock,
                    clockStartIndex.coerceIn(0, startSide.childCount),
                    clockParams
                )
            }
            val clockIndex = startSide.indexOfChild(clock).coerceIn(0, startSide.childCount)
            startSide.addView(before, clockIndex)
            startSide.addView(
                after,
                (startSide.indexOfChild(clock) + 1).coerceIn(0, startSide.childCount)
            )
        } else {
            // 时钟原来的位置由左区容器接替（左区图标原地保留，不被时钟的搬迁带走）
            if (clock.parent === startSide) clockStartIndex = startSide.indexOfChild(clock)
            systemIcons.addView(endBefore, 0)
            if (clock.parent !== systemIcons) {
                (clock.parent as? ViewGroup)?.removeView(clock)
                systemIcons.addView(clock, systemIcons.indexOfChild(endBefore) + 1)
            }
            startSide.addView(
                before,
                clockStartIndex.coerceAtLeast(0).coerceIn(0, startSide.childCount)
            )
        }
        return true
    }

    private fun detachViews(vararg groups: ViewGroup?) {
        for (group in groups) (group?.parent as? ViewGroup)?.removeView(group)
    }

    /**
     * 某个图标 view 的目标容器；null = 该 group 自己的容器（状态栏即宿主 `statusIcons`）。
     *
     * **只对状态栏那个 group 做分区路由**（`rightGroup === statusIconsView`）：锁屏/通知栏各自还有
     * 一个 `DarkIconManager`，它们的图标槽位同名，若也按槽位路由就会搬进状态栏的左侧容器，
     * 互相顶掉（左区看起来「搬过去没用」）。
     * 时钟所在区域按时钟拆成「前 / 后」两个容器，另一区域用一个容器装全部图标：
     * 时钟在左区时右区就是宿主 `statusIcons`；时钟在右区时左区只用 [leftBeforeGroup]。
     */
    private fun targetOf(group: ZoneIconGroup, child: View): ViewGroup? {
        if (!zonesActive) return null
        if (group.rightGroup !== statusIconsView) return null
        val slot = slotOf(child) ?: return null
        if (slot == CLOCK_ITEM) return null
        if (!clockAttached) {
            // 时钟视图拿不到：左区图标统一进唯一容器（leftBefore），右区图标留在宿主容器
            return if (slot in leftOrder) leftBeforeGroup else null
        }
        if (clockInStartSide()) {
            if (slot !in leftOrder) return null
            val clockIndex = leftOrder.indexOf(CLOCK_ITEM)
            val itemIndex = leftOrder.indexOf(slot)
            val before = leftBeforeGroup ?: return null
            val after = leftAfterGroup ?: return null
            return if (clockIndex in 0..<itemIndex) after else before
        }
        if (slot in leftOrder) return leftBeforeGroup
        if (slot !in rightOrder) return null
        val clockIndex = rightOrder.indexOf(CLOCK_ITEM)
        val itemIndex = rightOrder.indexOf(slot)
        val before = endBeforeGroup ?: return null
        return if (clockIndex >= 0 && itemIndex < clockIndex) before else null
    }

    /** 把整个虚拟列表按当前区域配置摆好（先把要换容器的摘掉，再按顺序放回去） */
    private fun reconcile() {
        val statusIcons = statusIconsView
        zonesActive = leftBeforeGroup != null && statusIcons != null &&
                zoneGroups.any { it.rightGroup === statusIcons }
        var placed = false
        if (zonesActive) {
            for (group in zoneGroups) reorderGroup(group)
            placed = placeGroups()
        }
        if (!placed) {
            // 容器没能挂进布局树（宿主结构变化 / 时钟不在预期位置）：图标全部回宿主 statusIcons，
            // 否则它们会被搬进不在布局树里的容器 = 直接看不见
            zonesActive = false
            for (group in zoneGroups) reorderGroup(group)
            detachViews(leftBeforeGroup, leftAfterGroup, endBeforeGroup)
        }
    }

    private fun reorderGroup(group: ZoneIconGroup) {
        val containers = ArrayList<ViewGroup>(4)
        containers += group.rightGroup
        leftBeforeGroup?.let { containers += it }
        leftAfterGroup?.let { containers += it }
        endBeforeGroup?.let { containers += it }

        // 1) 计算每个容器期望的子 view 顺序
        val desired = HashMap<ViewGroup, MutableList<View>>(4)
        for (child in group.snapshot()) {
            val target = targetOf(group, child) ?: group.rightGroup
            desired.getOrPut(target) { ArrayList(4) }.add(child)
        }
        // 2) 先把不属于该容器的子 view 摘掉（re-parent 前必须先解除旧父容器）。
        //    只摘本 group 自己的子 view：左区容器被状态栏 group 独占，别的 IconManager 的图标不归它管
        for (container in containers) {
            val want = desired[container] ?: emptyList()
            for (child in childrenOf(container)) {
                if (group.contains(child) && child !in want) container.removeView(child)
            }
        }
        // 3) 再按期望顺序依次摆好
        for (container in containers) {
            val want = desired[container] ?: continue
            for (index in want.indices) {
                val child = want[index]
                // 必须用 getChildAt（越界返回 null）：betterandroid 的 container.child(index)
                // 在容器为空 / 下标越界时会直接抛 IllegalStateException，把 SystemUI 带走
                if (container.getChildAt(index) === child) continue
                (child.parent as? ViewGroup)?.removeView(child)
                runCatching {
                    container.addView(
                        child,
                        index.coerceIn(0, container.childCount),
                        child.layoutParams
                    )
                }.onFailure { YLog.debug("StatusBarIconOrder: reorder add failed -> $it") }
            }
        }
    }

    /** 虚拟容器收到子 view 时：记进虚拟列表，然后按区域转发到真实容器 */
    private fun attachChild(child: View, virtualIndex: Int, params: ViewGroup.LayoutParams?) {
        val group = zoneGroups.firstOrNull { it.contains(child) } ?: return
        val target = targetOf(group, child) ?: group.rightGroup
        val index = indexWithin(group, virtualIndex, target)
        runCatching {
            target.addView(
                child,
                index.coerceIn(0, target.childCount),
                params ?: child.layoutParams
            )
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
            if ((targetOf(group, sibling) ?: group.rightGroup) === target) count++
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
                else -> (fallback[nameLeft] ?: Int.MAX_VALUE) - (fallback[nameRight]
                    ?: Int.MAX_VALUE)
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
                target.getDeclaredField(name).apply { makeAccessible() }
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
                    ?.apply { makeAccessible() }
            }.getOrNull()
            if (method != null) return method
            current = target.superclass
        }
    }

    private fun classOrNull(vararg names: String): Class<*>? =
        names.firstNotNullOfOrNull { runCatching { it.toClassOrNull() }.getOrNull() }

    private fun parseOrder(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

    /**
     * 归一化两区顺序：时钟必须恰好落在一个区域。
     *
     * - 推送方明确的时钟归属优先（[pushed]）：页面写回是两个键分两次推送，只有「刚写的那个键」说了算，
     *   否则把时钟从右区搬回左区时，先到的旧左区值会把时钟又拽回左侧；
     * - 初始读取（[PushSide.NONE]）以及两边都写了时钟时以左区为准；
     * - 都不声明时时钟留在原来的区域，原先也不在任何区域则默认落在左区最前。
     */
    private fun normalizeOrders(
        left: List<String>,
        right: List<String>,
        pushed: PushSide = PushSide.NONE,
    ): Pair<List<String>, List<String>> {
        val clockInLeft = left.contains(CLOCK_ITEM)
        val clockInRight = right.contains(CLOCK_ITEM)
        return when {
            pushed == PushSide.RIGHT && clockInRight -> left.filterNot { it == CLOCK_ITEM } to right
            pushed == PushSide.LEFT && clockInLeft -> left to right.filterNot { it == CLOCK_ITEM }
            clockInLeft -> left to right.filterNot { it == CLOCK_ITEM }
            clockInRight -> left to right
            else -> (listOf(CLOCK_ITEM) + left) to right
        }
    }

    /** 归一化时以哪一区的推送为准（页面写回是两个键分两次推送） */
    private enum class PushSide { NONE, LEFT, RIGHT }
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
