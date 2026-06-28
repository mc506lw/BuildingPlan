package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.entity.Player

class GuiManager(private val plugin: BuildingPlan) {

    private val subscribeGui = SubscribeGui(plugin)
    private val shopGui = ShopGui(plugin)

    /** 主入口：建材商店（原 menu，现已统一为 shop） */
    fun openShop(player: Player) {
        plugin.runOnEntity(player, Runnable { shopGui.open(player) })
    }

    fun openSubscribe(player: Player) {
        plugin.runOnEntity(player, Runnable { subscribeGui.open(player) })
    }
}
