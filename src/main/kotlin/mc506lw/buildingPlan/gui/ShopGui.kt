package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.BoundItem
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.gui.TabGui
import xyz.xenondevs.invui.gui.Markers
import xyz.xenondevs.invui.window.Window
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import kotlin.math.max
import kotlin.math.min

/**
 * 主入口 GUI：建材商店（统一布局）
 *
 * 外层 TabGui（6 行 x 9 列）：
 *   行 1：分类标签（最多 9 个）
 *   行 2-6：内容区（由当前 tab 的 PagedGui 提供）
 *
 * 每个分类的 PagedGui（5 行 x 9 列）：
 *   行 1-4：商品内容（分页，每页 36 格）
 *   行 5：< # 5h 日 周 # # # >  翻页 + 3 个耐久条
 */
class ShopGui(private val plugin: BuildingPlan) {

    fun open(player: Player) {
        val lang = plugin.langManager
        val locale = lang.getLocale(player)
        val isEn = locale == "en_us"
        val groups = plugin.configManager.materialGroups

        if (groups.isEmpty()) {
            plugin.langManager.msg(player, "material-not-found")
            return
        }

        val displayGroups = groups.take(9)
        val bgItem: Item = Item.simple(ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).hideTooltip(true))

        val tabs = mutableListOf<Gui>()
        val tabBuilders = mutableListOf<BoundItem.Builder<TabGui>>()

        for ((groupIndex, group) in displayGroups.withIndex()) {
            val displayName = if (isEn) group.displayNameEn else group.displayNameZh

            val tabBtnBuilder: BoundItem.Builder<TabGui> = BoundItem.tabBuilder()
                .setItemProvider { _: Player, tabGui: TabGui ->
                    val isSelected = tabGui.tab == groupIndex
                    val nameColor = if (isSelected) "<#FFD166>" else "<#48CAE4>"
                    ItemBuilder(group.icon)
                        .setName("$nameColor$displayName")
                        .addMiniMessageLoreLines(listOf(
                            if (isEn) "<#8D99AE>${group.materials.size} materials | <#FFD166>${group.pointsPerStack} pts/stack"
                            else "<#8D99AE>${group.materials.size} 种建材 | <#FFD166>${group.pointsPerStack} 点/组"
                        ))
                }
                .addClickHandler { _: Item, gui: TabGui, _: Click ->
                    gui.setTab(groupIndex)
                }
            tabBuilders.add(tabBtnBuilder)

            val contentItems = group.materials.map { material ->
                Item.builder()
                    .setItemProvider { viewer: Player ->
                        val d = plugin.planManager.getPlayerData(viewer.uniqueId)
                        val available = d?.let { plugin.planManager.getAvailableBalance(it) } ?: 0
                        val cost = group.pointsPerStack
                        val canBuy1 = available >= cost
                        val canBuy8 = available >= cost * 8
                        val canBuy64 = available >= cost * 64

                        val loreLines = mutableListOf<String>()
                        if (isEn) {
                            loreLines.add("<#8D99AE>Cost per stack: <#FFD166>$cost pts")
                            loreLines.add("<#8D99AE>Your available: <#FFD166>$available pts")
                            loreLines.add("")
                            loreLines.add(if (canBuy1) "<#06D6A0>Left click: Buy 1 stack" else "<#EF476F>Left click: Buy 1 stack (insufficient)")
                            loreLines.add(if (canBuy8) "<#06D6A0>Right click: Buy 8 stacks" else "<#EF476F>Right click: Buy 8 stacks (insufficient)")
                            loreLines.add(if (canBuy64) "<#06D6A0>Shift+Left: Buy 64 stacks" else "<#EF476F>Shift+Left: Buy 64 stacks (insufficient)")
                        } else {
                            loreLines.add("<#8D99AE>每组消耗：<#FFD166>$cost 点")
                            loreLines.add("<#8D99AE>当前可用：<#FFD166>$available 点")
                            loreLines.add("")
                            loreLines.add(if (canBuy1) "<#06D6A0>左键购买1组" else "<#EF476F>左键购买1组 (额度不足)")
                            loreLines.add(if (canBuy8) "<#06D6A0>右键购买8组" else "<#EF476F>右键购买8组 (额度不足)")
                            loreLines.add(if (canBuy64) "<#06D6A0>Shift+左键购买64组" else "<#EF476F>Shift+左键购买64组 (额度不足)")
                        }

                        ItemBuilder(material)
                            .setName("<#48CAE4>${GuiUtils.materialDisplayName(material.name)}")
                            .addMiniMessageLoreLines(loreLines)
                    }
                    .updatePeriodically(40)
                    .addClickHandler { click: Click ->
                        val stacks = when {
                            click.clickType().isShiftClick && !click.clickType().isRightClick -> 64
                            click.clickType().isRightClick -> 8
                            else -> 1
                        }
                        plugin.planManager.purchaseMaterial(click.player(), material, stacks)
                    }
                    .build()
            }

            // 每个分类独立的翻页按钮，绑定到该分类的 PagedGui
            val prevBtn: BoundItem = BoundItem.pagedBuilder()
                .setItemProvider { _: Player, gui: PagedGui<*> ->
                    if (gui.page > 0) {
                        ItemBuilder(Material.ARROW)
                            .setName(if (isEn) "<#8D99AE>Previous Page (${gui.page + 1}/${gui.pageCount})"
                                     else "<#8D99AE>上一页 (${gui.page + 1}/${gui.pageCount})")
                    } else {
                        ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).hideTooltip(true)
                    }
                }
                .addClickHandler { _: Item, gui: PagedGui<*>, _: Click ->
                    if (gui.page > 0) gui.setPage(gui.page - 1)
                }
                .build()

            val nextBtn: BoundItem = BoundItem.pagedBuilder()
                .setItemProvider { _: Player, gui: PagedGui<*> ->
                    if (gui.page < gui.pageCount - 1) {
                        ItemBuilder(Material.ARROW)
                            .setName(if (isEn) "<#8D99AE>Next Page (${gui.page + 2}/${gui.pageCount})"
                                     else "<#8D99AE>下一页 (${gui.page + 2}/${gui.pageCount})")
                    } else {
                        ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).hideTooltip(true)
                    }
                }
                .addClickHandler { _: Item, gui: PagedGui<*>, _: Click ->
                    if (gui.page < gui.pageCount - 1) gui.setPage(gui.page + 1)
                }
                .build()

            // 耐久条：每个分类一份实例，但读取的是同一份玩家数据，显示一致
            val durability5h = buildDurabilityItem(plugin, isEn, DurabilityType.QUOTA_5H)
            val durabilityDaily = buildDurabilityItem(plugin, isEn, DurabilityType.QUOTA_DAILY)
            val durabilityWeekly = buildDurabilityItem(plugin, isEn, DurabilityType.QUOTA_WEEKLY)

            val tabGui: PagedGui<Item> = PagedGui.itemsBuilder()
                .setStructure(
                    "x x x x x x x x x",
                    "x x x x x x x x x",
                    "x x x x x x x x x",
                    "x x x x x x x x x",
                    "< # # d D W # # >"
                )
                .addIngredient('#', bgItem)
                .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('<', prevBtn)
                .addIngredient('>', nextBtn)
                .addIngredient('d', durability5h)
                .addIngredient('D', durabilityDaily)
                .addIngredient('W', durabilityWeekly)
                .setContent(contentItems)
                .build()

            tabs.add(tabGui)
        }

        // 外层 TabGui 结构：顶排分类标签 + 5 行内容区
        val tabRow = buildString {
            for (i in displayGroups.indices) {
                append(GuiUtils.getTabChar(i))
            }
            while (length < 9) append('#')
        }

        val structure = arrayOf(
            tabRow,
            "x x x x x x x x x",
            "x x x x x x x x x",
            "x x x x x x x x x",
            "x x x x x x x x x",
            "x x x x x x x x x"
        )

        val tabGuiBuilder: TabGui.Builder = TabGui.builder()
            .setStructure(*structure)
            .addIngredient('#', bgItem)
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .setTabs(tabs)

        for ((index, btnBuilder) in tabBuilders.withIndex()) {
            val char = GuiUtils.getTabChar(index)
            tabGuiBuilder.addIngredient(char, btnBuilder)
        }

        val tabGui = tabGuiBuilder.build()

        Window.builder()
            .setViewer(player)
            .setTitle(lang.raw(player, "gui.shop-title"))
            .setUpperGui(tabGui)
            .open(player)
    }

    private enum class DurabilityType(val material: Material) {
        QUOTA_5H(Material.IRON_PICKAXE),
        QUOTA_DAILY(Material.GOLDEN_PICKAXE),
        QUOTA_WEEKLY(Material.DIAMOND_PICKAXE)
    }

    /**
     * 用工具耐久条展示额度使用比例：满耐久 = 额度全满，耐久越低 = 使用越多。
     */
    private fun buildDurabilityItem(plugin: BuildingPlan, isEn: Boolean, type: DurabilityType): Item {
        return Item.builder()
            .setItemProvider { viewer: Player ->
                val d = plugin.planManager.getPlayerData(viewer.uniqueId)
                val tier = d?.let { plugin.configManager.getPlanForTier(it.tier) }
                val (used, max) = when (type) {
                    DurabilityType.QUOTA_5H -> {
                        val m = tier?.quota5h ?: 0
                        val u = if (d != null && d.tier != "NONE") (m - d.balance5h) else 0
                        u to m
                    }
                    DurabilityType.QUOTA_DAILY -> {
                        val m = tier?.quotaDaily ?: 0
                        val u = if (d != null && d.tier != "NONE") (m - d.balanceDaily) else 0
                        u to m
                    }
                    DurabilityType.QUOTA_WEEKLY -> {
                        val m = tier?.quotaWeekly ?: 0
                        val u = if (d != null && d.tier != "NONE") (m - d.balanceWeekly) else 0
                        u to m
                    }
                }
                val remaining = max - used

                val (label, color) = when (type) {
                    DurabilityType.QUOTA_5H -> (if (isEn) "5h" else "5h额度") to "<#FFD166>"
                    DurabilityType.QUOTA_DAILY -> (if (isEn) "Daily" else "日额度") to "<#06D6A0>"
                    DurabilityType.QUOTA_WEEKLY -> (if (isEn) "Weekly" else "周额度") to "<#48CAE4>"
                }

                val maxDurability = type.material.maxDurability.toInt()
                val damage = if (max <= 0) maxDurability else ((maxDurability.toLong() * used) / max).toInt()
                val safeDamage = min(maxDurability, max(0, damage))

                val stack = ItemStack.of(type.material)
                val meta = stack.itemMeta
                if (meta is Damageable) {
                    meta.damage = safeDamage
                }
                stack.itemMeta = meta

                ItemBuilder(stack)
                    .setName("$color$label<#8D99AE>: <#FFD166>$remaining<#8D99AE>/<#FFD166>$max")
                    .addMiniMessageLoreLines(listOf(
                        if (isEn) "<#8D99AE>Used: <#FFD166>$used <#8D99AE>| Remaining: <#06D6A0>$remaining"
                        else "<#8D99AE>已用：<#FFD166>$used <#8D99AE>| 剩余：<#06D6A0>$remaining"
                    ))
            }
            .updatePeriodically(20)
            .build()
    }
}
