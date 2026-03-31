package cc.neurons.ymenu.config

import cc.neurons.ymenu.model.ActionSpec
import cc.neurons.ymenu.model.BackActionSpec
import cc.neurons.ymenu.model.ConditionalActionSpec
import cc.neurons.ymenu.model.MenuActionSpec
import cc.neurons.ymenu.model.MenuDefinition
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MenuRepository(
    private val plugin: Plugin,
    private val loader: MenuLoader = MenuLoader(),
) {
    private val menus = ConcurrentHashMap<String, MenuDefinition>()
    private val commandBindings = ConcurrentHashMap<String, String>()

    fun reload() {
        val configuredDirectory = File(plugin.dataFolder, plugin.config.getString("menus-directory", "menus") ?: "menus")
        val menuFiles = resolveMenuFiles(configuredDirectory)

        val loaded = mutableMapOf<String, MenuDefinition>()
        val loadedSources = mutableMapOf<String, File>()
        var failedCount = 0
        menuFiles.forEach { file ->
            val menu = try {
                loader.load(file)
            } catch (e: Exception) {
                failedCount++
                plugin.logger.warning("Failed to load menu from ${file.name}: ${e.message}")
                return@forEach
            }
            val existing = loadedSources.putIfAbsent(menu.id, file)
            if (existing != null) {
                plugin.logger.warning("Duplicate menu id '${menu.id}' from file ${file.path}; keeping ${existing.path}")
                return@forEach
            }
            loaded[menu.id] = menu
        }
        if (failedCount > 0) {
            plugin.logger.warning("$failedCount menu file(s) failed to load. Check your YAML syntax.")
        }

        menus.clear()
        menus.putAll(loaded)
        rebuildCommandBindings()
        validateReferences()
    }

    fun get(menuId: String): MenuDefinition? = menus[menuId]

    fun menuIds(): Set<String> = menus.keys.toSortedSet()

    fun getByCommandBinding(commandLabel: String): MenuDefinition? {
        val menuId = commandBindings[normalizeCommandBinding(commandLabel)] ?: return null
        return menus[menuId]
    }

    private fun validateReferences() {
        menus.values.forEach { menu ->
            menu.buttons.values.forEach { button ->
                collectMenuIds(button.actionsByClick.values.flatten()).forEach { target ->
                    if (!menus.containsKey(target)) {
                        plugin.logger.warning("Menu '${menu.id}' references missing menu '$target' from button '${button.key}'")
                    }
                }
                button.variants.forEach { variant ->
                    collectMenuIds(variant.actionsByClick.values.flatten()).forEach { target ->
                        if (!menus.containsKey(target)) {
                            plugin.logger.warning("Menu '${menu.id}' variant on button '${button.key}' references missing menu '$target'")
                        }
                    }
                }
            }

            collectMenuIds(menu.openActions).forEach { target ->
                if (!menus.containsKey(target)) {
                    plugin.logger.warning("Menu '${menu.id}' open-actions reference missing menu '$target'")
                }
            }

            collectMenuIds(menu.closeActions).forEach { target ->
                if (!menus.containsKey(target)) {
                    plugin.logger.warning("Menu '${menu.id}' close-actions reference missing menu '$target'")
                }
            }

            collectMenuIds(menu.closeDenyActions).forEach { target ->
                if (!menus.containsKey(target)) {
                    plugin.logger.warning("Menu '${menu.id}' close-deny-actions reference missing menu '$target'")
                }
            }

            menu.pageSpec?.elementKeys?.forEach { key ->
                if (key !in menu.buttons) {
                    plugin.logger.warning("Menu '${menu.id}' pagination references missing button '$key'")
                }
            }
        }
    }

    private fun rebuildCommandBindings() {
        commandBindings.clear()
        menus.values.forEach { menu ->
            menu.commandBindings.forEach { binding ->
                val normalized = normalizeCommandBinding(binding)
                if (normalized.isBlank()) {
                    return@forEach
                }
                val existing = commandBindings.putIfAbsent(normalized, menu.id)
                if (existing != null && existing != menu.id) {
                    plugin.logger.warning("Command binding '$binding' is declared by both '$existing' and '${menu.id}'")
                }
            }
        }
    }

    private fun normalizeCommandBinding(value: String): String {
        return value.trim().removePrefix("/").substringBefore(' ').lowercase()
    }

    private fun resolveMenuFiles(configuredDirectory: File): List<File> {
        if (!configuredDirectory.exists()) {
            configuredDirectory.mkdirs()
        }

        val menusRoot = File(plugin.dataFolder, "menus")
        if (shouldLoadMenusRoot(configuredDirectory, menusRoot)) {
            plugin.logger.warning(
                "Configured menus-directory '${configuredDirectory.invariantSeparatorsPathRelativeTo(plugin.dataFolder)}' " +
                    "excludes menu files under '${menusRoot.invariantSeparatorsPathRelativeTo(plugin.dataFolder)}'; " +
                    "loading from 'menus' and preferring categorized files over legacy root duplicates."
            )
            return menusRoot.walkTopDown()
                .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
                .sortedWith(menuFileComparator(menusRoot))
                .toList()
        }

        return configuredDirectory.walkTopDown()
            .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
            .sortedWith(menuFileComparator(configuredDirectory))
            .toList()
    }

    private fun menuFileComparator(root: File): Comparator<File> {
        return compareByDescending<File> { it.relativeDepth(root) }
            .thenBy { it.relativeTo(root).invariantSeparatorsPath }
    }

    private fun shouldLoadMenusRoot(configuredDirectory: File, menusRoot: File): Boolean {
        if (!menusRoot.isDirectory) {
            return false
        }
        if (configuredDirectory.absoluteFile == menusRoot.absoluteFile) {
            return false
        }

        val menusRootPath = menusRoot.toPath().normalize()
        val configuredPath = configuredDirectory.toPath().normalize()
        if (!configuredPath.startsWith(menusRootPath)) {
            return false
        }

        return menusRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
            .any { !it.isInside(configuredDirectory) }
    }

    private fun File.isInside(directory: File): Boolean {
        return toPath().normalize().startsWith(directory.toPath().normalize())
    }

    private fun File.invariantSeparatorsPathRelativeTo(root: File): String {
        return runCatching { relativeTo(root).invariantSeparatorsPath }
            .getOrDefault(invariantSeparatorsPath)
    }

    private fun File.relativeDepth(root: File): Int {
        return runCatching { relativeTo(root).invariantSeparatorsPath.count { it == '/' } }
            .getOrDefault(0)
    }

    private fun collectMenuIds(actions: List<ActionSpec>): List<String> = buildList {
        actions.forEach { action ->
            when (action) {
                is MenuActionSpec -> add(action.menuId)
                is BackActionSpec -> action.fallbackMenuId?.let(::add)
                is ConditionalActionSpec -> {
                    addAll(collectMenuIds(action.actions))
                    addAll(collectMenuIds(action.deny))
                }
                else -> Unit
            }
        }
    }
}
