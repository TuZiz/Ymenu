package cc.neurons.ymenu.render

import cc.neurons.ymenu.menu.MenuSession
import cc.neurons.ymenu.menu.ResolvedButton
import cc.neurons.ymenu.util.Texts
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import java.net.URL
import java.util.Base64
import java.util.UUID

class ItemRenderer(
    private val placeholderResolver: PlaceholderResolver,
    private val itemResolver: ItemResolver,
) {
    private val skinUrlPattern = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"")

    fun render(player: Player, button: ResolvedButton, session: MenuSession? = null): ItemStack {
        val display = button.display
        val resolvedItemId = placeholderResolver.resolve(player, display.itemId, session, button.context)
        val material = itemResolver.resolve(resolvedItemId)
        val item = ItemStack(material, display.amount.coerceIn(1, 64))
        val meta = item.itemMeta ?: return item

        val name = placeholderResolver.resolve(player, display.name, session, button.context)
        if (name.isNotBlank()) {
            meta.setDisplayName(Texts.colorize(name))
        }

        val lore = placeholderResolver.resolve(player, display.lore, session, button.context)
        if (lore.isNotEmpty()) {
            meta.lore = Texts.colorize(lore)
        }

        if (display.shiny) {
            val enchantment = Enchantment.getByName("UNBREAKING") ?: Enchantment.getByName("DURABILITY")
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
        }

        applyHeadProfile(meta, resolvedItemId)

        if (material == Material.PLAYER_HEAD && itemResolver.isExternalDescription(resolvedItemId)) {
            meta.setDisplayName(Texts.colorize(name.ifBlank { "&fMenu" }))
        }

        item.itemMeta = meta
        return item
    }

    private fun applyHeadProfile(meta: ItemMeta, itemId: String?) {
        val skullMeta = meta as? SkullMeta ?: return
        val normalized = itemId?.trim().orEmpty()
        when {
            normalized.startsWith("head-", ignoreCase = true) -> {
                val ownerName = normalized.substringAfter("head-", "").trim()
                if (ownerName.isBlank()) {
                    return
                }
                skullMeta.owningPlayer = Bukkit.getOfflinePlayer(ownerName)
            }

            normalized.startsWith("player_head:", ignoreCase = true) -> {
                val ownerName = normalized.substringAfter("player_head:", "").trim()
                    .removePrefix("%").removeSuffix("%")
                    .let { if (it.equals("player_name", ignoreCase = true)) return@let null else it }
                if (ownerName != null && ownerName.isNotBlank()) {
                    skullMeta.owningPlayer = Bukkit.getOfflinePlayer(ownerName)
                }
                // If ownerName was %player_name%, the placeholder was already resolved
                // by PlaceholderResolver before reaching here, so the raw itemId
                // will contain the actual player name after resolution.
            }

            normalized.startsWith("head:", ignoreCase = true) -> {
                applyHeadTexture(skullMeta, normalized.substringAfter("head:", "").trim())
            }
        }
    }

    private fun applyHeadTexture(meta: SkullMeta, encodedTexture: String) {
        val skinUrl = decodeSkinUrl(encodedTexture) ?: return
        val profile = Bukkit.createPlayerProfile(UUID.randomUUID())
        val textures = profile.textures
        textures.setSkin(skinUrl)
        profile.setTextures(textures)
        meta.ownerProfile = profile
    }

    private fun decodeSkinUrl(encodedTexture: String): URL? {
        if (encodedTexture.isBlank()) {
            return null
        }

        val decoded = runCatching {
            String(Base64.getDecoder().decode(encodedTexture.trim()))
        }.getOrNull() ?: return null
        val urlValue = skinUrlPattern.find(decoded)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { URL(urlValue) }.getOrNull()
    }
}
