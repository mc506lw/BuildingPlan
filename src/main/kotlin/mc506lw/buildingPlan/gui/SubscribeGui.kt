package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.window.Window
import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.math.min

class SubscribeGui(private val plugin: BuildingPlan) {

    fun open(player: Player) {
        val lang = plugin.langManager
        val locale = lang.getLocale(player)
        val isEn = locale == "en_us"

        val bgItem: Item = Item.simple(ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).hideTooltip(true))

        val planKeys = plugin.configManager.plans.keys.toList()
        // GUI 最多支持 8 个套餐（2 排 x 4 个）
        val displayKeys = planKeys.take(8)
        val planCount = displayKeys.size

        val structure = buildStructure(planCount)

        val guiBuilder = Gui.builder()
            .setStructure(*structure)
            .addIngredient('#', bgItem)

        for ((index, tierName) in displayKeys.withIndex()) {
            val tier = plugin.configManager.getPlan(tierName) ?: continue
            val tierDisplay = if (isEn) tier.displayNameEn else tier.displayNameZh

            val priceLines = tier.price.entries.map { (mat, amt) ->
                val matName = GuiUtils.materialDisplayName(mat)
                "<#8D99AE>- <#FFD166>$amt x $matName"
            }

            val loreLines = if (isEn) {
                listOf(
                    "<#8D99AE>5h Quota: <#FFD166>${tier.quota5h}",
                    "<#8D99AE>Daily Quota: <#FFD166>${tier.quotaDaily}",
                    "<#8D99AE>Weekly Quota: <#FFD166>${tier.quotaWeekly}",
                    "<#8D99AE>Pool Cap: <#FFD166>${tier.poolCap}",
                    "<#8D99AE>Price:"
                ) + priceLines + listOf(
                    "",
                    "<#06D6A0>Click to subscribe"
                )
            } else {
                listOf(
                    "<#8D99AE>5h额度：<#FFD166>${tier.quota5h}",
                    "<#8D99AE>日额度：<#FFD166>${tier.quotaDaily}",
                    "<#8D99AE>周额度：<#FFD166>${tier.quotaWeekly}",
                    "<#8D99AE>积攒上限：<#FFD166>${tier.poolCap}",
                    "<#8D99AE>价格："
                ) + priceLines + listOf(
                    "",
                    "<#06D6A0>点击订阅"
                )
            }

            val tierItem: Item = Item.builder()
                .setItemProvider {
                    ItemBuilder(tier.icon)
                        .setName("<#48CAE4>$tierDisplay ${if (isEn) "Plan" else "套餐"}")
                        .addMiniMessageLoreLines(loreLines)
                }
                .addClickHandler { click: Click ->
                    val p = click.player()
                    val data = plugin.planManager.getPlayerData(p.uniqueId)
                    if (data != null && data.tier != "NONE") {
                        // 套餐期间不允许续订
                        val currentTier = plugin.configManager.getPlanForTier(data.tier)
                        val tierDisplay = if (isEn) (currentTier?.displayNameEn ?: data.tier) else (currentTier?.displayNameZh ?: data.tier)
                        plugin.langManager.msg(p, "already-subscribed", mapOf(
                            "tier" to tierDisplay,
                            "expire" to GuiUtils.formatExpire(data.expireTime)
                        ))
                    } else {
                        plugin.planManager.purchaseSubscription(p, tierName)
                    }
                }
                .build()

            guiBuilder.addIngredient(GuiUtils.getTabChar(index), tierItem)
        }

        val gui = guiBuilder.build()

        Window.builder()
            .setViewer(player)
            .setTitle(lang.raw(player, "gui.subscribe-title"))
            .setUpperGui(gui)
            .open(player)
    }

    /**
     * 构建订阅 GUI 的结构。
     * 1-4 个套餐：单排布局（3 行）
     * 5-8 个套餐：双排布局（5 行）
     * 每排最多 4 个套餐：`# a # b # c # d #` = 17 字符
     */
    private fun buildStructure(planCount: Int): Array<String> {
        val border = "# # # # # # # # #"
        val tabsPerRow = 4

        return if (planCount <= tabsPerRow) {
            arrayOf(border, buildTabRow(0, planCount), border)
        } else {
            arrayOf(
                border,
                buildTabRow(0, planCount),
                border,
                buildTabRow(tabsPerRow, planCount),
                border
            )
        }
    }

    /** 构建一排套餐标签行，从 startIndex 开始，最多 4 个，不足用背景填充。 */
    private fun buildTabRow(startIndex: Int, planCount: Int): String {
        val line = StringBuilder("#")
        val endIndex = min(startIndex + 4, planCount)
        for (i in startIndex until endIndex) {
            line.append(" ${GuiUtils.getTabChar(i)} #")
        }
        while (line.length < 17) line.append(" #")
        return line.toString()
    }
}
