package io.github.adlamb.cubex.config

data class PluginConfigs(
    val locale: String,
    val debug: Boolean,
    val placeholderMenuRows: Int,
    val validateDatabaseOnStartup: Boolean,
    val database: DatabaseConfig,
    val menuItems: MenuItemConfigs,
)

enum class DatabaseMode {
    H2,
    MARIADB,
}

data class PoolSettings(
    val maximumSize: Int,
    val minimumIdle: Int,
    val connectionTimeoutMs: Long,
)

data class H2Settings(
    val file: String,
)

data class MariaDbSettings(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val parameters: String,
)

data class DatabaseConfig(
    val mode: DatabaseMode,
    val pool: PoolSettings,
    val h2: H2Settings,
    val mariadb: MariaDbSettings,
)
