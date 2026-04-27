package io.github.adlamb.cubex.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ConfigService(private val plugin: JavaPlugin) {
    fun load(): PluginConfigs {
        plugin.saveDefaultConfig()
        ensureResource("database.yml")
        ensureResource("messages.yml")
        ensureResource("menu-items.yml")

        val config = plugin.config
        val database = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "database.yml"))

        return PluginConfigs(
            locale = config.getString("locale", "zh-CN").orEmpty(),
            debug = config.getBoolean("debug", false),
            placeholderMenuRows = config.getInt("menus.placeholder-rows", 4).coerceIn(1, 6),
            validateDatabaseOnStartup = config.getBoolean("startup.validate-database", true),
            database = DatabaseConfig(
                mode = parseDatabaseMode(database.getString("mode", "H2")),
                pool = PoolSettings(
                    maximumSize = database.getInt("pool.maximum-size", 8),
                    minimumIdle = database.getInt("pool.minimum-idle", 1),
                    connectionTimeoutMs = database.getLong("pool.connection-timeout-ms", 10_000L),
                ),
                h2 = H2Settings(
                    file = database.getString("h2.file", "data/cubexslg").orEmpty(),
                ),
                mariadb = MariaDbSettings(
                    host = database.getString("mariadb.host", "127.0.0.1").orEmpty(),
                    port = database.getInt("mariadb.port", 3306),
                    database = database.getString("mariadb.database", "cubexslg").orEmpty(),
                    username = database.getString("mariadb.username", "root").orEmpty(),
                    password = database.getString("mariadb.password", "").orEmpty(),
                    parameters = database.getString("mariadb.parameters", "").orEmpty(),
                ),
            ),
            menuItems = loadMenuItems(),
        )
    }

    private fun loadMenuItems(): MenuItemConfigs {
        val file = File(plugin.dataFolder, "menu-items.yml")
        val yaml = runCatching { YamlConfiguration.loadConfiguration(file) }
            .onFailure { plugin.logger.warning("Failed to load menu-items.yml: ${it.message}. Using built-in fallback menu config.") }
            .getOrNull() ?: return MenuItemDefaults.create()

        val menusSection = yaml.getConfigurationSection("menus")
            ?: return MenuItemDefaults.create().also {
                plugin.logger.warning("menu-items.yml missing 'menus' section. Using built-in fallback menu config.")
            }

        val parsedMenus = menusSection.getKeys(false)
            .associateWith { key ->
                parseMenu(menusSection.getConfigurationSection(key), "menus.$key")
            }
            .filterValues { it != null }
            .mapValues { it.value!! }

        if (parsedMenus.isEmpty()) {
            plugin.logger.warning("menu-items.yml does not define valid menus. Using built-in fallback menu config.")
            return MenuItemDefaults.create()
        }
        return MenuItemConfigs(parsedMenus)
    }

    private fun parseMenu(section: ConfigurationSection?, path: String): MenuViewConfig? {
        if (section == null) {
            plugin.logger.warning("Missing section: $path")
            return null
        }

        val title = section.getString("title")?.takeIf { it.isNotBlank() } ?: "<white>${path.substringAfterLast('.')}"
        val rows = if (section.contains("rows")) section.getInt("rows").coerceIn(1, 6) else null

        val glass = parseItemTemplate(section.getConfigurationSection("glass"))
        val body = parseBodyConfig(section.getConfigurationSection("body"), "$path.body")
        val buttons = parseButtons(section.getMapList("buttons"), "$path.buttons")
        val dynamicLists = parseDynamicLists(section.getConfigurationSection("dynamic-lists"), "$path.dynamic-lists")

        return MenuViewConfig(
            title = title,
            rows = rows,
            glass = glass,
            body = body,
            buttons = buttons,
            dynamicLists = dynamicLists,
        )
    }

    private fun parseItemTemplate(section: ConfigurationSection?): MenuItemTemplate {
        if (section == null) {
            return MenuItemTemplate()
        }
        return MenuItemTemplate(
            material = section.getString("material", "GRAY_STAINED_GLASS_PANE").orEmpty(),
            name = section.getString("name", "<white> ").orEmpty(),
            lore = section.getStringList("lore").ifEmpty { emptyList() },
        )
    }

    private fun parseBodyConfig(section: ConfigurationSection?, path: String): MenuBodyConfig {
        if (section == null) {
            return MenuBodyConfig()
        }

        val slots = sanitizeSlots(section.getIntegerList("slots"), "$path.slots")
            .ifEmpty { MenuBodyConfig().slots }

        val templates = section.getMapList("templates")
            .mapNotNull { node ->
                val material = node["material"]?.toString().orEmpty().ifBlank { "PAPER" }
                val name = node["name"]?.toString().orEmpty().ifBlank { "<white> " }
                val lore = when (val rawLore = node["lore"]) {
                    is List<*> -> rawLore.mapNotNull { it?.toString() }
                    is String -> listOf(rawLore)
                    else -> listOf("<white>{line}")
                }
                MenuBodyTemplate(
                    material = material,
                    name = name,
                    lore = lore.ifEmpty { listOf("<white>{line}") },
                )
            }
            .ifEmpty { MenuBodyConfig().templates }

        return MenuBodyConfig(templates = templates, slots = slots)
    }

    private fun parseButtons(raw: List<Map<*, *>>, path: String): List<MenuButtonTemplate> = raw.mapIndexedNotNull { index, node ->
        val id = node["id"]?.toString().orEmpty().ifBlank {
            plugin.logger.warning("$path[$index] missing id, entry skipped.")
            return@mapIndexedNotNull null
        }
        val slot = (node["slot"] as? Number)?.toInt()
        if (slot == null || slot !in 0..53) {
            plugin.logger.warning("$path[$index] has invalid slot '$slot', entry skipped.")
            return@mapIndexedNotNull null
        }

        val material = node["material"]?.toString().orEmpty().ifBlank { "PAPER" }
        val title = node["title"]?.toString().orEmpty().ifBlank { "<white>$id" }
        val lore = when (val rawLore = node["lore"]) {
            is List<*> -> rawLore.mapNotNull { it?.toString() }
            is String -> listOf(rawLore)
            else -> emptyList()
        }
        val visible = (node["visible"] as? Boolean) ?: true

        MenuButtonTemplate(
            id = id,
            slot = slot,
            material = material,
            title = title,
            lore = lore,
            visible = visible,
        )
    }

    private fun parseDynamicLists(section: ConfigurationSection?, path: String): Map<String, MenuDynamicListTemplate> {
        if (section == null) {
            return emptyMap()
        }

        return section.getKeys(false).mapNotNull { key ->
            val listSection = section.getConfigurationSection(key)
            if (listSection == null) {
                plugin.logger.warning("$path.$key missing section, entry skipped.")
                return@mapNotNull null
            }

            val slots = sanitizeSlots(listSection.getIntegerList("slots"), "$path.$key.slots")
            if (slots.isEmpty()) {
                plugin.logger.warning("$path.$key has no valid slots, entry skipped.")
                return@mapNotNull null
            }

            val material = listSection.getString("material", "PAPER").orEmpty()
            val title = listSection.getString("title", "<white>$key").orEmpty()
            val lore = listSection.getStringList("lore")
            val visible = listSection.getBoolean("visible", true)

            key to MenuDynamicListTemplate(
                slots = slots,
                material = material,
                title = title,
                lore = lore,
                visible = visible,
            )
        }.toMap()
    }

    private fun sanitizeSlots(slots: List<Int>, path: String): List<Int> = slots.filter { slot ->
        val valid = slot in 0..53
        if (!valid) {
            plugin.logger.warning("Invalid slot $slot in $path, it will be ignored.")
        }
        valid
    }

    private fun parseDatabaseMode(raw: String?): DatabaseMode = runCatching {
        DatabaseMode.valueOf(raw.orEmpty().uppercase())
    }.getOrElse {
        plugin.logger.warning("Unsupported database mode '$raw', fallback to H2.")
        DatabaseMode.H2
    }

    private fun ensureResource(name: String) {
        val file = File(plugin.dataFolder, name)
        if (!file.exists()) {
            plugin.saveResource(name, false)
        }
    }
}
