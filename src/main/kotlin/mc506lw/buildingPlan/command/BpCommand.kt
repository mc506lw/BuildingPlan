package mc506lw.buildingPlan.command

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class BpCommand(private val plugin: BuildingPlan) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "shop" -> {
                if (sender !is Player) {
                    plugin.langManager.msg(sender, "player-only")
                    return true
                }
                if (!sender.hasPermission("buildingplan.shop")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                plugin.guiManager.openShop(sender)
            }
            "subscribe" -> {
                if (sender !is Player) {
                    plugin.langManager.msg(sender, "player-only")
                    return true
                }
                if (!sender.hasPermission("buildingplan.subscribe")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                plugin.guiManager.openSubscribe(sender)
            }
            "admin" -> {
                if (!sender.hasPermission("buildingplan.admin")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                handleAdmin(sender, args.drop(1).toTypedArray())
            }
            else -> sendHelp(sender)
        }
        return true
    }

    private fun handleAdmin(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            plugin.langManager.msg(sender, "help-admin-give")
            plugin.langManager.msg(sender, "help-admin-reload")
            return
        }
        when (args[0].lowercase()) {
            "give" -> {
                if (args.size < 3) {
                    plugin.langManager.msg(sender, "help-admin-give")
                    return
                }
                val targetName = args[1]
                val points = args[2].toIntOrNull()
                if (points == null || points <= 0) {
                    plugin.langManager.msg(sender, "invalid-number")
                    return
                }
                val target = Bukkit.getPlayer(targetName)
                if (target == null) {
                    plugin.langManager.msg(sender, "player-not-found")
                    return
                }
                plugin.planManager.giveBonusPoints(target, points)
                plugin.langManager.msg(sender, "admin-give-success",
                    mapOf("player" to targetName, "points" to points.toString()))
            }
            "reload" -> {
                plugin.configManager.reload()
                plugin.langManager.load()
                plugin.langManager.msg(sender, "reloaded")
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        plugin.langManager.msg(sender, "help-header")
        plugin.langManager.msg(sender, "help-shop")
        plugin.langManager.msg(sender, "help-subscribe")
        plugin.langManager.msg(sender, "help-admin-give")
        plugin.langManager.msg(sender, "help-admin-reload")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when {
            args.size == 1 -> listOf("shop", "subscribe", "admin").filter { it.startsWith(args[0].lowercase()) }
            args.size == 2 && args[0].lowercase() == "admin" -> listOf("give", "reload").filter { it.startsWith(args[1].lowercase()) }
            args.size == 3 && args[0].lowercase() == "admin" && args[1].lowercase() == "give" ->
                Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2]) }
            else -> emptyList()
        }
    }
}
