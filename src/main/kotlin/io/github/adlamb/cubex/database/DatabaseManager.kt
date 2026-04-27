package io.github.adlamb.cubex.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.adlamb.cubex.config.DatabaseConfig
import io.github.adlamb.cubex.config.DatabaseMode
import io.github.adlamb.cubex.coroutine.PluginCoroutines
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

class DatabaseManager(
    private val plugin: JavaPlugin,
    private val config: DatabaseConfig,
    private val coroutines: PluginCoroutines,
) : TransactionRunner {
    private var dataSource: HikariDataSource? = null
    private var database: Database? = null

    fun initialize() {
        if (database != null) {
            return
        }

        val hikari = HikariConfig().apply {
            maximumPoolSize = config.pool.maximumSize
            minimumIdle = config.pool.minimumIdle
            connectionTimeout = config.pool.connectionTimeoutMs
            poolName = "${plugin.name}-pool"

            when (config.mode) {
                DatabaseMode.H2 -> {
                    driverClassName = "org.h2.Driver"
                    val filePath = File(plugin.dataFolder, config.h2.file).absolutePath
                    jdbcUrl = "jdbc:h2:${filePath};MODE=MySQL;AUTO_SERVER=TRUE"
                    username = "sa"
                    password = ""
                }

                DatabaseMode.MARIADB -> {
                    driverClassName = "org.mariadb.jdbc.Driver"
                    jdbcUrl = buildString {
                        append("jdbc:mariadb://")
                        append(config.mariadb.host)
                        append(':')
                        append(config.mariadb.port)
                        append('/')
                        append(config.mariadb.database)
                        if (config.mariadb.parameters.isNotBlank()) {
                            append('?')
                            append(config.mariadb.parameters)
                        }
                    }
                    username = config.mariadb.username
                    password = config.mariadb.password
                }
            }
        }

        val hikariDataSource = HikariDataSource(hikari)
        val exposed = Database.connect(datasource = hikariDataSource)

        transaction(exposed) {
            SchemaUtils.create(PluginMetadataTable)
            val hasRow = PluginMetadataTable.selectAll().limit(1).firstOrNull() != null
            if (!hasRow) {
                PluginMetadataTable.insert {
                    it[key] = "schema_version"
                    it[value] = "base-1"
                }
            }
        }

        dataSource = hikariDataSource
        database = exposed
    }

    override suspend fun <T> inTransaction(block: () -> T): T {
        val db = requireNotNull(database) { "Database has not been initialized." }
        return coroutines.io {
            transaction(db) {
                block()
            }
        }
    }

    fun close() {
        dataSource?.close()
        dataSource = null
        database = null
    }
}

object PluginMetadataTable : Table("cubexslg_metadata") {
    val key = varchar("key", 64)
    val value = varchar("value", 255)

    override val primaryKey = PrimaryKey(key)
}
