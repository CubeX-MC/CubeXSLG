package io.github.adlamb.cubex.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ConfigService(private val plugin: JavaPlugin) {
    fun load(): PluginConfigs {
        plugin.saveDefaultConfig()
        ensureResource("database.yml")
        ensureResource("messages.yml")

        val config = plugin.config
        val database = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "database.yml"))

        return PluginConfigs(
            locale = config.getString("locale", "zh-CN").orEmpty(),
            debug = config.getBoolean("debug", false),
            placeholderMenuRows = config.getInt("menus.placeholder-rows", 3).coerceIn(1, 6),
            validateDatabaseOnStartup = config.getBoolean("startup.validate-database", true),
            database = DatabaseConfig(
                mode = DatabaseMode.valueOf(database.getString("mode", "H2").orEmpty().uppercase()),
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
        )
    }

    private fun ensureResource(name: String) {
        val file = File(plugin.dataFolder, name)
        if (!file.exists()) {
            plugin.saveResource(name, false)
        }
    }
}
