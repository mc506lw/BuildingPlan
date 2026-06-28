package mc506lw.buildingPlan.manager

import mc506lw.buildingPlan.BuildingPlan
import mc506lw.buildingPlan.database.DatabaseManager
import mc506lw.buildingPlan.gui.GuiUtils
import mc506lw.buildingPlan.model.MaterialGroup
import mc506lw.buildingPlan.model.PlanTier
import mc506lw.buildingPlan.model.PlayerData
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class PlanManager(private val plugin: BuildingPlan) {

    private val db: DatabaseManager get() = plugin.databaseManager
    private val dataCache = ConcurrentHashMap<UUID, PlayerData>()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun loadPlayerData(uuid: UUID): CompletableFuture<PlayerData> {
        return db.loadPlayerData(uuid.toString()).thenApply { data ->
            checkExpiration(data)
            dataCache[uuid] = data
            data
        }
    }

    fun getPlayerData(uuid: UUID): PlayerData? = dataCache[uuid]

    fun getOrLoad(uuid: UUID): CompletableFuture<PlayerData> {
        val cached = dataCache[uuid]
        return if (cached != null) {
            CompletableFuture.completedFuture(cached)
        } else {
            loadPlayerData(uuid)
        }
    }

    fun savePlayerData(data: PlayerData): CompletableFuture<Void> {
        dataCache[UUID.fromString(data.uuid)] = data
        return db.savePlayerData(data)
    }

    fun checkAndRefresh5h(data: PlayerData, tier: PlanTier?): PlayerData {
        if (tier == null) return data
        val now = System.currentTimeMillis() / 1000
        if (now - data.last5hRefresh >= 18000) {
            data.balance5h = tier.quota5h
            data.last5hRefresh = now
            if (plugin.configManager.debug) {
                plugin.logger.info("[DEBUG] 5h refresh for ${data.uuid}, new balance5h=${data.balance5h}")
            }
        }
        return data
    }

    fun getAvailableBalance(data: PlayerData): Int {
        return minOf(data.balance5h, data.balanceDaily + data.pool, data.balanceWeekly)
    }

    fun canAfford(data: PlayerData, cost: Int): Boolean {
        return data.balance5h >= cost &&
                (data.balanceDaily + data.pool) >= cost &&
                data.balanceWeekly >= cost
    }

    fun deductBalance(data: PlayerData, cost: Int) {
        data.balance5h -= cost
        val dailyDeduction = minOf(data.balanceDaily, cost)
        data.balanceDaily -= dailyDeduction
        val poolDeduction = cost - dailyDeduction
        if (poolDeduction > 0) {
            data.pool -= poolDeduction
        }
        data.balanceWeekly -= cost
    }

    fun addBalance(data: PlayerData, points: Int) {
        data.balanceDaily += points
        val tier = plugin.configManager.getPlanForTier(data.tier)
        if (tier != null && data.balanceDaily > tier.quotaDaily) {
            data.balanceDaily = tier.quotaDaily
        }
    }

    fun performDailySettlement(data: PlayerData, tier: PlanTier): PlayerData {
        val today = LocalDate.now().format(dateFormatter)
        if (data.lastDailyReset == today) return data

        val transferred = minOf(data.balanceDaily, tier.poolCap - data.pool)
        if (transferred > 0) {
            data.pool += transferred
            if (plugin.configManager.debug) {
                plugin.logger.info("[DEBUG] Pool settlement for ${data.uuid}: transferred $transferred to pool, pool=${data.pool}")
            }
        }

        data.balanceDaily = tier.quotaDaily
        data.lastDailyReset = today
        if (plugin.configManager.debug) {
            plugin.logger.info("[DEBUG] Daily reset for ${data.uuid}: balanceDaily=${data.balanceDaily}")
        }
        return data
    }

    fun performWeeklyReset(data: PlayerData, tier: PlanTier): PlayerData {
        val today = LocalDate.now()
        if (today.dayOfWeek != DayOfWeek.MONDAY) return data
        val todayStr = today.format(dateFormatter)
        if (data.lastWeeklyReset == todayStr) return data

        data.balanceWeekly = tier.quotaWeekly
        data.lastWeeklyReset = todayStr
        if (plugin.configManager.debug) {
            plugin.logger.info("[DEBUG] Weekly reset for ${data.uuid}: balanceWeekly=${data.balanceWeekly}")
        }
        return data
    }

    fun checkExpiration(data: PlayerData): PlayerData {
        if (data.tier == "NONE") return data
        val now = System.currentTimeMillis() / 1000
        if (now >= data.expireTime) {
            data.tier = "NONE"
            data.balance5h = 0
            data.balanceDaily = 0
            data.balanceWeekly = 0
            data.pool = 0
            data.subscribeTime = 0
            data.expireTime = 0
            if (plugin.configManager.debug) {
                plugin.logger.info("[DEBUG] Subscription expired for ${data.uuid}")
            }
            savePlayerData(data)
        }
        return data
    }

    fun refreshPlayerState(data: PlayerData): PlayerData {
        val tier = plugin.configManager.getPlanForTier(data.tier) ?: return data
        checkExpiration(data)
        if (data.tier == "NONE") return data
        performWeeklyReset(data, tier)
        performDailySettlement(data, tier)
        checkAndRefresh5h(data, tier)
        savePlayerData(data)
        return data
    }

    fun purchaseSubscription(player: Player, tierName: String): Boolean {
        val uuid = player.uniqueId
        val tier = plugin.configManager.getPlan(tierName) ?: return false
        val lock = db.getLock(uuid.toString())
        if (!lock.tryLock()) return false
        try {
            val data = getPlayerData(uuid) ?: return false

            if (!checkAndRemovePriceItems(player, tier)) {
                return false
            }

            val now = System.currentTimeMillis() / 1000
            data.tier = tierName.uppercase()
            data.subscribeTime = now
            data.expireTime = now + plugin.configManager.subscriptionDays * 86400L
            data.balance5h = tier.quota5h
            data.balanceDaily = tier.quotaDaily
            data.balanceWeekly = tier.quotaWeekly
            data.pool = 0
            data.last5hRefresh = now
            data.lastDailyReset = LocalDate.now().format(dateFormatter)
            data.lastWeeklyReset = LocalDate.now().format(dateFormatter)
            savePlayerData(data)

            plugin.langManager.msg(player, "subscribe-success", mapOf("tier" to tierName))
            return true
        } finally {
            lock.unlock()
        }
    }

    private fun checkAndRemovePriceItems(player: Player, tier: PlanTier): Boolean {
        // 先检查所有所需物品是否充足，收集缺少的部分
        val missing = mutableListOf<Pair<String, Int>>()
        for ((materialStr, amount) in tier.price) {
            val material = try {
                Material.valueOf(materialStr)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid price material: $materialStr in plan ${tier.name}")
                return false
            }
            if (!player.inventory.containsAtLeast(ItemStack.of(material), amount)) {
                val matName = GuiUtils.materialDisplayName(material.name)
                missing.add(matName to amount)
            }
        }
        if (missing.isNotEmpty()) {
            plugin.langManager.msg(player, "price-item-not-enough",
                mapOf("items" to missing.joinToString(", ") { "${it.second} x ${it.first}" }))
            return false
        }
        for ((materialStr, amount) in tier.price) {
            val material = Material.valueOf(materialStr)
            val toRemove = ItemStack.of(material)
            toRemove.amount = amount
            player.inventory.removeItemAnySlot(toRemove)
        }
        return true
    }

    fun purchaseMaterial(player: Player, material: Material, stacks: Int): Boolean {
        val uuid = player.uniqueId
        val group = plugin.configManager.findMaterialGroup(material) ?: return false
        val costPerStack = group.pointsPerStack
        val totalCost = costPerStack * stacks
        val totalItems = stacks * 64

        val neededSlots = stacks
        val emptySlots = player.inventory.storageContents.count { it == null || it.type.isAir }
        if (emptySlots < neededSlots) {
            plugin.langManager.msg(player, "inventory-full")
            return false
        }

        val lock = db.getLock(uuid.toString())
        if (!lock.tryLock()) return false
        try {
            val data = getPlayerData(uuid) ?: return false
            if (data.tier == "NONE") {
                plugin.langManager.msg(player, "no-subscription")
                return false
            }
            checkExpiration(data)
            if (data.tier == "NONE") {
                plugin.langManager.msg(player, "subscription-expired")
                return false
            }
            val tier = plugin.configManager.getPlanForTier(data.tier) ?: return false
            checkAndRefresh5h(data, tier)

            if (!canAfford(data, totalCost)) {
                val available = getAvailableBalance(data)
                plugin.langManager.msg(player, "not-enough-balance",
                    mapOf("cost" to totalCost.toString(), "available" to available.toString()))
                return false
            }

            deductBalance(data, totalCost)
            savePlayerData(data)

            val itemStack = ItemStack.of(material, 64)
            for (i in 0 until stacks) {
                player.inventory.addItem(itemStack.clone())
            }

            plugin.langManager.msg(player, "purchase-success",
                mapOf("amount" to totalItems.toString(),
                    "material" to GuiUtils.materialDisplayName(material.name),
                    "cost" to totalCost.toString()))

            if (plugin.configManager.debug) {
                plugin.logger.info("[DEBUG] Purchase: ${player.name} bought ${stacks}x${material.name} for $totalCost pts, " +
                        "5h=${data.balance5h} daily=${data.balanceDaily} pool=${data.pool} weekly=${data.balanceWeekly}")
            }
            return true
        } finally {
            lock.unlock()
        }
    }

    fun giveBonusPoints(player: Player, points: Int): Boolean {
        val uuid = player.uniqueId
        val lock = db.getLock(uuid.toString())
        if (!lock.tryLock()) return false
        try {
            val data = getPlayerData(uuid) ?: return false
            data.bonusPoints += points
            savePlayerData(data)
            return true
        } finally {
            lock.unlock()
        }
    }

    fun runSettlementCheck() {
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)
        for ((uuid, data) in dataCache) {
            if (data.tier == "NONE") continue
            val tier = plugin.configManager.getPlanForTier(data.tier) ?: continue

            val lock = db.getLock(uuid.toString())
            if (!lock.tryLock()) continue
            try {
                checkExpiration(data)
                if (data.tier == "NONE") continue

                var changed = false
                if (today.dayOfWeek == DayOfWeek.MONDAY && data.lastWeeklyReset != todayStr) {
                    performWeeklyReset(data, tier)
                    changed = true
                }
                if (data.lastDailyReset != todayStr) {
                    performDailySettlement(data, tier)
                    changed = true
                }
                if (changed) {
                    savePlayerData(data)
                }
            } finally {
                lock.unlock()
            }
        }
    }

    fun startSettlementTask() {
        plugin.runTaskTimer(Runnable {
            runSettlementCheck()
        }, 1200L, 1200L)
    }

    fun onPlayerJoin(uuid: UUID) {
        loadPlayerData(uuid).thenAccept { data ->
            val tier = plugin.configManager.getPlanForTier(data.tier)
            if (tier != null) {
                refreshPlayerState(data)
            }
        }
    }

    fun onPlayerQuit(uuid: UUID) {
        val data = dataCache.remove(uuid) ?: return
        db.savePlayerDataSync(data)
    }
}
