package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap

object GuiUtils {

    private val mm = MiniMessage.miniMessage()

    fun mm(text: String) = mm.deserialize(text)

    fun formatExpire(expireTime: Long): String {
        if (expireTime <= 0) return "N/A"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return sdf.format(java.util.Date(expireTime * 1000))
    }

    private val tabChars = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun getTabChar(index: Int): Char {
        return if (index < tabChars.length) tabChars[index] else '?'
    }

    fun maxTabSlots(): Int = tabChars.length

    /**
     * 返回材料显示名称的 MiniMessage 片段，由客户端根据自身语言自动翻译。
     * 通过 ItemStack.translationKey() 获取 Minecraft 官方翻译键，
     * 再包装为 <lang:key> 标签交给客户端渲染（zh_cn/en_us 等所有语言均支持）。
     * 无需联网、无需写死对照表。失败时回退为 material 小写形式。
     * 结果会按 material 缓存以避免重复查找。
     */
    private val nameCache = ConcurrentHashMap<String, String>()

    fun materialDisplayName(material: String): String {
        nameCache[material]?.let { return it }

        val fallback = material.lowercase().replace('_', ' ')
        val mat = try {
            Material.valueOf(material)
        } catch (e: IllegalArgumentException) {
            nameCache[material] = fallback
            return fallback
        }
        if (mat.isAir) {
            nameCache[material] = fallback
            return fallback
        }

        val name = try {
            val key = ItemStack.of(mat).translationKey()
            "<lang:$key>"
        } catch (e: Exception) {
            fallback
        }
        nameCache[material] = name
        return name
    }
}
