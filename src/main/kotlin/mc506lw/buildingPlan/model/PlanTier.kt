package mc506lw.buildingPlan.model

import org.bukkit.Material

data class PlanTier(
    val name: String,
    val price: Map<String, Int>,
    val quota5h: Int,
    val quotaDaily: Int,
    val quotaWeekly: Int,
    val poolCap: Int,
    val icon: Material = Material.EMERALD_BLOCK,
    val displayNameZh: String = name,
    val displayNameEn: String = name
)
