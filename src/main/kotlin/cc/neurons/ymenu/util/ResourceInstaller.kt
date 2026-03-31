package cc.neurons.ymenu.util

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

class ResourceInstaller(private val plugin: JavaPlugin? = null) {
    fun copyMissingDefaults() {
        val plugin = requireNotNull(plugin) { "A JavaPlugin instance is required to install bundled resources." }
        val resources = bundledMenuResources()
        if (resources.isEmpty()) {
            plugin.logger.warning("No bundled menu resources were found to install.")
            return
        }

        resources.forEach { resourcePath ->
            val target = File(plugin.dataFolder, resourcePath)
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                plugin.saveResource(resourcePath, false)
            }
        }
    }

    private fun bundledMenuResources(): List<String> {
        val plugin = requireNotNull(plugin) { "A JavaPlugin instance is required to read bundled resources." }
        val indexed = plugin.getResource("menus.index")
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.useLines(::parseIndexLines)
            .orEmpty()
        if (indexed.isNotEmpty()) {
            return indexed
        }

        val codeSource = runCatching {
            plugin.javaClass.protectionDomain.codeSource?.location?.toURI()?.let(::File)
        }.getOrNull()
        val discovered = discoverBundledResources(codeSource)
        if (discovered.isNotEmpty()) {
            plugin.logger.info("menus.index not found, discovered ${discovered.size} bundled menu resources automatically.")
        }
        return discovered
    }

    internal fun parseIndexLines(lines: Sequence<String>): List<String> {
        return lines.map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }

    internal fun discoverBundledResources(location: File?): List<String> {
        if (location == null || !location.exists()) {
            return emptyList()
        }

        return when {
            location.isDirectory -> discoverFromDirectory(location)
            location.isFile && location.extension.equals("jar", ignoreCase = true) -> discoverFromArchive(location)
            else -> emptyList()
        }
    }

    internal fun discoverFromDirectory(root: File): List<String> {
        val menusDir = File(root, "menus")
        if (!menusDir.isDirectory) {
            return emptyList()
        }

        return menusDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    internal fun discoverFromArchive(archive: File): List<String> {
        return ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("menus/") && it.endsWith(".yml", ignoreCase = true) }
                .sorted()
                .toList()
        }
    }
}
