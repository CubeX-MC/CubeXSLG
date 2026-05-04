package io.github.adlamb.cubex.util

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Sign
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CompletableFuture

/**
 * 粘贴 schematic 并扫描告示牌的结果
 */
data class PasteScanResult(
    val markers: Map<String, Location>,
    val coreFound: Boolean,
    val signCount: Int,
    val originX: Int,
    val originY: Int,
    val originZ: Int,
    val width: Int,
    val height: Int,
    val length: Int,
    val nonAirBlockCount: Int,
)

/**
 * Schematic 加载器
 * 用于从 .schem 文件加载建筑并识别特殊告示牌标记
 */
class SchematicLoader(private val plugin: JavaPlugin) {
    
    companion object {
        private const val SLG_MARKER = "[SLG]"
        private const val CORE_MARKER = "CORE"
        private const val LOGISTICS_INPUT_MARKER = "I"
        private const val LOGISTICS_OUTPUT_MARKER = "O"
        private const val POWER_MARKER = "POWER"
    }
    
    /**
     * 检查 WorldEdit 是否可用
     */
    fun isWorldEditAvailable(): Boolean {
        return try {
            Class.forName("com.sk89q.worldedit.WorldEdit")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    /**
     * 核心功能：粘贴 schematic，扫描告示牌，替换特殊方块
     * @param origin 建筑原点（world 坐标）
     * @param schematicName schematic 文件名（如 "farm_level1.schem"）
     * @return CompletableFuture<PasteScanResult>
     */
    fun pasteSchematicAndScan(
        origin: Location,
        schematicName: String,
        ignoreAirBlocks: Boolean = false,
    ): CompletableFuture<PasteScanResult>? {
        if (!isWorldEditAvailable()) {
            plugin.logger.warning("WorldEdit 插件未安装")
            return null
        }

        val schemFolder = File(plugin.dataFolder, "schematics")
        val schemFile = File(schemFolder, schematicName)
        if (!schemFile.exists()) {
            plugin.logger.warning("Schematic 文件不存在: $schematicName")
            return null
        }

        val future = CompletableFuture<PasteScanResult>()

        // 异步读取 schematic 文件
        Bukkit.getAsyncScheduler().runNow(plugin) { scheduledTask ->
            try {
                // === 第一阶段：读取 schematic ===
                val format = ClipboardFormats.findByFile(schemFile)
                    ?: throw IllegalArgumentException("不支持的 schematic 格式: $schematicName")
                val clipboard = format.getReader(FileInputStream(schemFile)).read()
                val dimensions = clipboard.dimensions
                val width = dimensions.x
                val height = dimensions.y
                val length = dimensions.z

                val region = clipboard.region
                val regionMin = region.minimumPoint
                val regionMax = region.maximumPoint
                val clipOrigin = clipboard.origin

                val offsetX = clipOrigin.x - regionMin.x
                val offsetY = clipOrigin.y - regionMin.y
                val offsetZ = clipOrigin.z - regionMin.z

                val actualOriginX = origin.blockX - offsetX
                val actualOriginY = origin.blockY - offsetY
                val actualOriginZ = origin.blockZ - offsetZ
                val actualWidth = regionMax.x - regionMin.x + 1
                val actualHeight = regionMax.y - regionMin.y + 1
                val actualLength = regionMax.z - regionMin.z + 1

                plugin.logger.info("开始加载蓝图: ${schemFile.absolutePath}")
                plugin.logger.info("蓝图尺寸: $width x $height x $length")
                if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
                    plugin.logger.info("剪贴板偏移: ($offsetX, $offsetY, $offsetZ), 实际原点: ($actualOriginX, $actualOriginY, $actualOriginZ)")
                }
                plugin.logger.info("粘贴原点: ${origin.blockX}, ${origin.blockY}, ${origin.blockZ}")

                // === 第二阶段：粘贴到世界（使用区域调度器） ===
                val world = origin.world ?: throw IllegalStateException("无效的世界")
                Bukkit.getRegionScheduler().run(plugin, origin) { regionTask ->
                    try {
                        val weWorld = BukkitAdapter.adapt(world)
                        val weOrigin = BlockVector3.at(origin.blockX.toDouble(), origin.blockY.toDouble(), origin.blockZ.toDouble())

                        // 创建编辑会话
                        val editSession = WorldEdit.getInstance().newEditSession(weWorld)
                        try {
                            // 粘贴操作
                            val operation = com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                                .createPaste(editSession)
                                .to(weOrigin)
                                .ignoreAirBlocks(ignoreAirBlocks)
                                .build()

                            Operations.complete(operation)
                        } finally {
                            editSession.close()
                        }

                        // 强制加载 origin 所在区块
                        val originChunk = world.getChunkAt(origin)
                        if (!originChunk.isLoaded) {
                            originChunk.load()
                        }

                        // 等待 2 ticks 确保方块更新
                        Bukkit.getRegionScheduler().runDelayed(plugin, origin, { delayedTask ->
                            try {
                                // === 第三阶段：扫描告示牌 ===
                                val markers = mutableMapOf<String, Location>()
                                var coreFound = false
                                var signCount = 0
                                var nonAirBlockCount = 0

                                for (x in 0 until width) {
                                    for (y in 0 until height) {
                                        for (z in 0 until length) {
                                            val worldX = origin.blockX + x - offsetX
                                            val worldY = origin.blockY + y - offsetY
                                            val worldZ = origin.blockZ + z - offsetZ
                                            val loc = Location(world, worldX.toDouble(), worldY.toDouble(), worldZ.toDouble())

                                            val block = world.getBlockAt(loc)
                                            val type = block.type
                                            if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.STRUCTURE_VOID) {
                                                nonAirBlockCount++
                                            }
                                            if (type.name.uppercase().contains("SIGN")) {
                                                val state = block.state
                                                if (state is Sign) {
                                                    signCount++
                                                    
                                                    val lines = state.lines()
                                                    val line0 = if (lines.size > 0) {
                                                        PlainTextComponentSerializer.plainText().serialize(lines[0]).trim()
                                                    } else ""
                                                    val line1 = if (lines.size > 1) {
                                                        PlainTextComponentSerializer.plainText().serialize(lines[1]).trim()
                                                    } else ""

                                                    plugin.logger.info("发现告示牌 #$signCount at ($worldX,$worldY,$worldZ): ['$line0', '$line1']")

                                                    if (line0.equals(SLG_MARKER, ignoreCase = true) && line1.isNotEmpty()) {
                                                        val markerType = line1.uppercase()
                                                        val replacement: Material? = when (markerType) {
                                                            CORE_MARKER -> Material.BARREL
                                                            LOGISTICS_INPUT_MARKER -> Material.HOPPER
                                                            LOGISTICS_OUTPUT_MARKER -> Material.HOPPER
                                                            POWER_MARKER -> Material.REDSTONE_BLOCK
                                                            else -> null
                                                        }

                                                        if (replacement != null) {
                                                            block.setType(replacement)
                                                            
                                                            val key = when (markerType) {
                                                                CORE_MARKER -> "CORE"
                                                                LOGISTICS_INPUT_MARKER -> "INPUT"
                                                                LOGISTICS_OUTPUT_MARKER -> "OUTPUT"
                                                                POWER_MARKER -> "POWER"
                                                                else -> markerType
                                                            }
                                                            markers[key] = loc.clone()
                                                            if (markerType == CORE_MARKER) {
                                                                coreFound = true
                                                            }
                                                            plugin.logger.info("识别到 SLG 标记: $markerType，已替换为 $replacement")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                plugin.logger.info("找到 $signCount 个告示牌")
                                plugin.logger.info("是否找到核心方块: $coreFound")
                                
                                if (coreFound) {
                                    plugin.logger.info("✓ 核心方块位置: ${markers["CORE"]}")
                                } else {
                                    plugin.logger.warning("✗ 未找到核心方块标记！请检查 schematic 中是否有 [SLG] + CORE 告示牌")
                                }
                                
                                future.complete(PasteScanResult(markers, coreFound, signCount, actualOriginX, actualOriginY, actualOriginZ, actualWidth, actualHeight, actualLength, nonAirBlockCount))
                            } catch (e: Exception) {
                                future.completeExceptionally(e)
                            }
                        }, 2L)
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }
    
    /**
     * 获取建筑 schematic 文件名
     */
    fun getSchematicFileName(buildingType: String, level: Int): String {
        return "${buildingType.lowercase()}_level$level.schem"
    }

    fun removeSchematicFromWorld(
        originX: Int,
        originY: Int,
        originZ: Int,
        width: Int,
        height: Int,
        length: Int,
        world: org.bukkit.World,
    ) {
        val loc = Location(world, originX.toDouble(), originY.toDouble(), originZ.toDouble())
        Bukkit.getRegionScheduler().run(plugin, loc) { _ ->
            try {
                val weWorld = BukkitAdapter.adapt(world)
                val editSession = WorldEdit.getInstance().newEditSession(weWorld)
                try {
                    for (x in 0 until width) {
                        for (y in 0 until height) {
                            for (z in 0 until length) {
                                val airType = com.sk89q.worldedit.world.block.BlockTypes.AIR
                                if (airType != null) {
                                    editSession.setBlock(
                                        BlockVector3.at(originX + x, originY + y, originZ + z),
                                        airType.defaultState,
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    editSession.close()
                }
                plugin.logger.info("已清除建筑 schematic 区域 ($width x $height x $length) at ($originX, $originY, $originZ)")
            } catch (e: Exception) {
                plugin.logger.severe("清除 schematic 区域失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
