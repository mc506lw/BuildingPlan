package mc506lw.buildingPlan.model

import org.bukkit.Material

data class MaterialGroup(
    val name: String,
    val displayNameZh: String,
    val displayNameEn: String,
    val icon: Material,
    val pointsPerStack: Int,
    val materials: List<Material>
)
