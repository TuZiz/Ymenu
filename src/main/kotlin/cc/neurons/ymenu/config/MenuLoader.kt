package cc.neurons.ymenu.config

import cc.neurons.ymenu.action.ActionParser
import cc.neurons.ymenu.model.ButtonDefinition
import cc.neurons.ymenu.model.ButtonType
import cc.neurons.ymenu.model.ButtonVariantDefinition
import cc.neurons.ymenu.model.ClickType
import cc.neurons.ymenu.model.DisplaySpec
import cc.neurons.ymenu.model.MenuDefinition
import cc.neurons.ymenu.model.PageSpec
import cc.neurons.ymenu.parser.LayoutValidator
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemorySection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class MenuLoader(
    private val layoutValidator: LayoutValidator = LayoutValidator(),
    private val actionParser: ActionParser = ActionParser(),
) {
    fun load(file: File): MenuDefinition {
        val config = YamlConfiguration.loadConfiguration(file)
        val id = file.nameWithoutExtension
        val title = readStringValue(config, "Title", "title") ?: id
        val titleUpdateTicks = readLongValue(config, "Title-Update", "TITLE-UPDATE", "title-update")?.takeIf { it > 0 }
        val layoutBundle = loadLayouts(config, id)
        val buttons = loadButtons(findConfigurationSection(config, "BUTTONS", "Icons"))
        val bindingsSection = findConfigurationSection(config, "Bindings")
        val eventsSection = findConfigurationSection(config, "Events")
        val openActions = actionParser.parseAll(findList(config, "Open-Actions", "open-actions")) +
            actionParser.parseAll(findList(eventsSection, "Open", "open"))
        val closeDenyActions = actionParser.parseAll(findList(config, "Close-Deny-Actions", "close-deny-actions"))
        val closeActions = actionParser.parseAll(findList(config, "Close-Actions", "close-actions")) +
            actionParser.parseAll(findList(eventsSection, "Close", "close"))
        val closeDenyConditions = findStringList(config, "Close-Deny-Conditions", "close-deny-conditions")
        val pageSpec = loadPageSpec(findConfigurationSection(config, "Pagination"))

        return MenuDefinition(
            id = id,
            title = title,
            titleUpdateTicks = titleUpdateTicks,
            layoutRows = layoutBundle.rows,
            layoutTokens = layoutBundle.pages.first(),
            layoutPages = layoutBundle.pages,
            buttons = buttons,
            commandBindings = loadStringList(bindingsSection, "command"),
            pageSpec = pageSpec,
            openActions = openActions,
            closeActions = closeActions,
            closeDenyConditions = closeDenyConditions,
            closeDenyActions = closeDenyActions,
            functions = loadFunctions(findConfigurationSection(config, "Functions")),
        )
    }

    private fun loadButtons(section: ConfigurationSection?): Map<String, ButtonDefinition> {
        if (section == null) {
            return emptyMap()
        }

        return section.getKeys(false).associateWith { key ->
            val buttonSection = requireNotNull(section.getConfigurationSection(key)) {
                "Button '$key' must be a configuration section"
            }
            val displaySection = findConfigurationSection(buttonSection, "display")
            ButtonDefinition(
                key = key,
                updateTicks = buttonSection.getLong("update").takeIf { it > 0 },
                display = loadDisplaySpec(displaySection),
                actionsByClick = loadActionsByClick(findValue(buttonSection, "actions")),
                viewConditions = findStringList(buttonSection, "view-condition"),
                clickCooldownTicks = buttonSection.getLong("click-cooldown").takeIf { it > 0 },
                buttonType = buttonSection.getString("button-type")
                    ?.uppercase()
                    ?.let { runCatching { ButtonType.valueOf(it) }.getOrNull() }
                    ?: ButtonType.NORMAL,
                pageTarget = buttonSection.getInt("page").takeIf { it > 0 },
                variants = loadVariants(buttonSection),
            )
        }
    }

    private fun loadPageSpec(section: ConfigurationSection?): PageSpec? {
        if (section == null) {
            return null
        }

        return PageSpec(
            slots = section.getIntegerList("slots"),
            elementKeys = section.getStringList("elements"),
        )
    }

    private fun loadFunctions(section: ConfigurationSection?): Map<String, String> {
        if (section == null) {
            return emptyMap()
        }
        return section.getKeys(false).associateWith { key -> section.getString(key).orEmpty() }
    }

    private fun loadVariants(section: ConfigurationSection): List<ButtonVariantDefinition> {
        val entries = findVariantEntries(section)
        if (entries.isEmpty()) {
            return emptyList()
        }
        return entries.mapNotNull { entry ->
            val condition = entry["condition"]?.toString()?.trim().orEmpty()
            if (condition.isBlank()) {
                return@mapNotNull null
            }
            ButtonVariantDefinition(
                condition = condition,
                priority = entry["priority"]?.toString()?.toIntOrNull() ?: 0,
                updateTicks = entry["update"]?.toString()?.toLongOrNull()?.takeIf { it > 0 },
                display = loadDisplaySpec(entry["display"]),
                actionsByClick = loadActionsByClick(entry["actions"]),
            )
        }.sortedBy { it.priority }
    }

    private fun loadDisplaySpec(raw: Any?): DisplaySpec {
        val section = raw as? ConfigurationSection
        val map = raw as? Map<*, *>
        val shinyValue = section?.get("shiny") ?: map?.get("shiny")
        val amountRaw = section?.get("amount") ?: map?.get("amount")
        val amount = when (amountRaw) {
            is Number -> amountRaw.toInt()
            is String -> amountRaw.toIntOrNull() ?: 1
            else -> 1
        }.coerceIn(1, 64)
        return DisplaySpec(
            material = section?.getString("material") ?: map?.get("material")?.toString(),
            mat = section?.getString("mat") ?: map?.get("mat")?.toString(),
            mats = section?.getString("mats") ?: map?.get("mats")?.toString(),
            name = readStringValue(section, map, "name"),
            lore = section?.getStringList("lore")
                ?: (map?.get("lore") as? List<*>)?.mapNotNull { it?.toString() }
                ?: emptyList(),
            amount = amount,
            shiny = shinyValue as? Boolean ?: false,
            shinyCondition = shinyValue as? String,
        )
    }

    private fun loadActionsByClick(raw: Any?): Map<ClickType, List<cc.neurons.ymenu.model.ActionSpec>> {
        return when (raw) {
            null -> emptyMap()
            is ConfigurationSection -> raw.getKeys(false)
                .associate { key -> ClickType.fromConfig(key) to parseActionEntries(raw.get(key)) }
                .filterKeys { it != ClickType.UNKNOWN }
            is Map<*, *> -> raw.entries
                .associate { (key, value) ->
                    ClickType.fromConfig(key.toString()) to parseActionEntries(value)
                }
                .filterKeys { it != ClickType.UNKNOWN }
            else -> mapOf(ClickType.ALL to parseActionEntries(raw))
        }
    }

    private fun findVariantEntries(section: ConfigurationSection): List<Map<*, *>> {
        val names = arrayOf("icon", "Icon", "icons", "Icons")
        return names
            .asSequence()
            .map { name -> section.getMapList(name) }
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun parseActionEntries(raw: Any?): List<cc.neurons.ymenu.model.ActionSpec> {
        val normalized = unwrapActionContainer(raw)
        return when (normalized) {
            null -> emptyList()
            is List<*> -> actionParser.parseAll(normalized)
            else -> actionParser.parseAll(listOf(normalized))
        }
    }

    private fun unwrapActionContainer(raw: Any?): Any? {
        return when (raw) {
            is ConfigurationSection -> if (isActionContainer(raw.getKeys(false))) {
                unwrapActionContainer(findValue(raw, "actions", "action"))
            } else {
                raw
            }
            is MemorySection -> if (isActionContainer(raw.getKeys(false))) {
                unwrapActionContainer(findValue(raw, "actions", "action"))
            } else {
                raw
            }
            is Map<*, *> -> {
                val keys = raw.keys.mapNotNull { it?.toString() }
                if (!isActionContainer(keys)) {
                    raw
                } else {
                    val entry = raw.entries.firstOrNull { (key, _) ->
                        key?.toString()?.equals("actions", ignoreCase = true) == true ||
                            key?.toString()?.equals("action", ignoreCase = true) == true
                    }
                    unwrapActionContainer(entry?.value)
                }
            }
            else -> raw
        }
    }

    private fun isActionContainer(keys: Collection<String>): Boolean {
        return keys.isNotEmpty() && keys.all { key ->
            key.equals("actions", ignoreCase = true) || key.equals("action", ignoreCase = true)
        }
    }

    private fun loadLayouts(config: YamlConfiguration, menuId: String): LayoutBundle {
        val raw = findValue(config, "Layout", "layout", "Shape")
        val pages = when (raw) {
            is List<*> -> parseLayoutPages(raw)
            else -> emptyList()
        }
        require(pages.isNotEmpty()) { "Menu $menuId must declare at least one layout row." }

        val validatedPages = pages.mapIndexed { index, rows ->
            layoutValidator.validate(rows, if (pages.size == 1) menuId else "$menuId page ${index + 1}")
        }
        return LayoutBundle(rows = pages.first(), pages = validatedPages)
    }

    private fun parseLayoutPages(raw: List<*>): List<List<String>> {
        val firstNonNull = raw.firstOrNull { it != null } ?: return emptyList()
        return when (firstNonNull) {
            is String -> listOf(raw.mapNotNull { it?.toString() })
            is List<*> -> raw.mapNotNull { page ->
                (page as? List<*>)?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }
            }
            else -> emptyList()
        }
    }

    private fun findConfigurationSection(section: ConfigurationSection?, vararg names: String): ConfigurationSection? {
        if (section == null) {
            return null
        }
        val key = findKey(section, *names) ?: return null
        return section.getConfigurationSection(key)
    }

    private fun findList(section: ConfigurationSection?, vararg names: String): List<Any?> {
        return when (val value = findValue(section, *names)) {
            is List<*> -> value
            else -> emptyList()
        }
    }

    private fun findStringList(section: ConfigurationSection?, vararg names: String): List<String> {
        return findList(section, *names).mapNotNull { it?.toString() }
    }

    private fun loadStringList(section: ConfigurationSection?, vararg names: String): List<String> {
        return when (val value = findValue(section, *names)) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter(String::isNotEmpty)
            is String -> listOf(value.trim()).filter(String::isNotEmpty)
            else -> emptyList()
        }
    }

    private fun findValue(section: ConfigurationSection?, vararg names: String): Any? {
        if (section == null) {
            return null
        }
        val key = findKey(section, *names) ?: return null
        return section.get(key)
    }

    private fun readLongValue(section: ConfigurationSection?, vararg names: String): Long? {
        val value = findValue(section, *names) ?: return null
        return when (value) {
            is Number -> value.toLong()
            else -> value.toString().trim().toLongOrNull()
        }
    }

    private fun readStringValue(section: ConfigurationSection?, vararg names: String): String? {
        val value = findValue(section, *names) ?: return null
        return when (value) {
            is List<*> -> value.firstOrNull()?.toString()
            else -> value.toString()
        }
    }

    private fun findKey(section: ConfigurationSection, vararg names: String): String? {
        return section.getKeys(false).firstOrNull { key -> names.any { key.equals(it, ignoreCase = true) } }
    }

    private fun readStringValue(section: ConfigurationSection?, map: Map<*, *>?, key: String): String? {
        val sectionList = section?.getStringList(key).orEmpty()
        if (sectionList.isNotEmpty()) {
            return sectionList.first()
        }
        section?.getString(key)?.let { return it }
        val raw = map?.get(key) ?: return null
        return when (raw) {
            is List<*> -> raw.firstOrNull()?.toString()
            else -> raw.toString()
        }
    }

    private data class LayoutBundle(
        val rows: List<String>,
        val pages: List<List<List<String>>>,
    )
}
