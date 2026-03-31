package cc.neurons.ymenu

import cc.neurons.ymenu.command.YmenuCommand
import cc.neurons.ymenu.config.MenuRepository
import cc.neurons.ymenu.data.PlayerDataStore
import cc.neurons.ymenu.listener.MenuListener
import cc.neurons.ymenu.menu.MenuService
import cc.neurons.ymenu.platform.PlatformDetector
import cc.neurons.ymenu.render.PlaceholderResolver
import cc.neurons.ymenu.util.ResourceInstaller
import org.bukkit.plugin.java.JavaPlugin

class YmenuPlugin : JavaPlugin() {
    lateinit var menuRepository: MenuRepository
        private set
    lateinit var menuService: MenuService
        private set
    private lateinit var playerDataStore: PlayerDataStore

    override fun onEnable() {
        saveDefaultConfig()
        ResourceInstaller(this).copyMissingDefaults()

        val scheduler = PlatformDetector.detect(this)
        playerDataStore = PlayerDataStore(this)
        val placeholderResolver = PlaceholderResolver(this, playerDataStore)
        menuRepository = MenuRepository(this).also { it.reload() }
        menuService = MenuService(this, scheduler, placeholderResolver, menuRepository, playerDataStore)

        server.pluginManager.registerEvents(MenuListener(menuService, menuRepository), this)
        val command = YmenuCommand(menuService, menuRepository)
        getCommand("ymenu")?.setExecutor(command)
        getCommand("ymenu")?.tabCompleter = command

        logger.info("Loaded ${menuRepository.menuIds().size} menus.")
    }

    override fun onDisable() {
        if (::menuService.isInitialized) {
            menuService.shutdown()
        }
        if (::playerDataStore.isInitialized) {
            playerDataStore.shutdown()
        }
    }
}
