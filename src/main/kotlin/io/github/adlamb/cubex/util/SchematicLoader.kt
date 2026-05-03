package io.github.adlamb.cubex.util

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.Vector3
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.block.TileState
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.FileInputStream

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
     * 建筑特殊位置信息
     */
    data class BuildingMarkers(
        var coreLocation: Location? = null,
        var logisticsInput: Location? = null,
        var logisticsOutput: Location? = null,
        var powerConnection: Location? = null
    ) {
        override fun toString(): String {
            return "BuildingMarkers(core=$coreLocation, input=$logisticsInput, output=$logisticsOutput, power=$powerConnection)"
        }
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
     * 从 schematic 文件生成建筑（不处理特殊标记）
     */
    fun pasteSchematic(origin: Location, schemFileName: String): Boolean {
        return pasteSchematicWithMarkers(origin, schemFileName) != null
    }
    
    /**
     * 从 schematic 文件生成建筑，并识别特殊告示牌标记
     */
    fun pasteSchematicWithMarkers(origin: Location, schemFileName: String): BuildingMarkers? {
        if (!isWorldEditAvailable()) {
            plugin.logger.warning("WorldEdit 未安装，无法生成建筑")
            return null
        }
        
        val schemFile = File(plugin.dataFolder, "schematics/$schemFileName")
        if (!schemFile.exists()) {
            plugin.logger.warning("Schematic 文件不存在: ${schemFile.absolutePath}")
            return null
        }
        
        return try {
            plugin.logger.info("开始加载 schematic: ${schemFile.absolutePath}")
            
            // 确定文件格式
            val format = ClipboardFormats.findByFile(schemFile)
            if (format == null) {
                plugin.logger.severe("不支持的 schematic 格式: $schemFileName")
                return null
            }
            
            plugin.logger.info("检测到 schematic 格式: $format")
            
            // 读取 clipboard
            val reader = format.getReader(FileInputStream(schemFile))
            val clipboard = reader.read()
            reader.close()
            
            plugin.logger.info("Schematic 尺寸: ${clipboard.dimensions}")
            
            // 粘贴到世界
            val adaptedWorld = BukkitAdapter.adapt(origin.world)
            val pasteLocation = BukkitAdapter.asBlockVector(origin)
            
            val markers = BuildingMarkers()
            
            WorldEdit.getInstance().newEditSession(adaptedWorld).use { editSession ->
                val operation = com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteLocation)
                    .ignoreAirBlocks(false)
                    .build()
                
                Operations.complete(operation)
                editSession.flushSession()
            }
            
            plugin.logger.info("Schematic 粘贴完成，开始扫描告示牌...")
            
            plugin.logger.info("Schematic '$schemFileName' 已成功粘贴到: ${origin.blockX}, ${origin.blockY}, ${origin.blockZ}")
            
            // 立即扫描告示牌（Folia 不支持异步调度）
            scanAndProcessSigns(origin, clipboard, markers)
            
            if (markers.coreLocation != null) {
                plugin.logger.info("找到核心方块位置: ${markers.coreLocation}")
            } else {
                plugin.logger.warning("未找到核心方块标记！请检查 schematic 中是否有 [SLG] + CORE 告示牌")
            }
            
            return markers
        } catch (e: Exception) {
            plugin.logger.severe("粘贴 schematic 失败: $schemFileName - ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 扫描建筑区域内的告示牌，识别特殊标记并替换为对应方块
     */
    private fun scanAndProcessSigns(origin: Location, clipboard: Clipboard, markers: BuildingMarkers) {
        val size = clipboard.dimensions
        val width = size.blockX
        val height = size.blockY
        val length = size.blockZ
        
        plugin.logger.info("开始扫描 schematic 区域: ${width}x${height}x${length}")
        var signCount = 0
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                for (z in 0 until length) {
                    try {
                        val pos = com.sk89q.worldedit.math.BlockVector3.at(x, y, z)
                        val blockState = clipboard.getBlock(pos)
                        val blockType = blockState.blockType
                        
                        // 检查是否是告示牌（从 clipboard 中检测）
                        if (blockType.id.contains("sign", ignoreCase = true)) {
                            signCount++
                            plugin.logger.info("在 schematic 中找到告示牌 #$signCount at ($x,$y,$z), 类型: ${blockType.id}")
                            
                            // 计算实际世界坐标
                            val worldX = origin.blockX + x
                            val worldY = origin.blockY + y
                            val worldZ = origin.blockZ + z
                            
                            val signLocation = Location(origin.world, worldX.toDouble(), worldY.toDouble(), worldZ.toDouble())
                            val block = signLocation.block
                            
                            plugin.logger.info("世界坐标: $worldX,$worldY,$worldZ, 方块类型: ${block.type}")
                            
                            // 从世界中读取告示牌（WorldEdit 已粘贴）
                            val state = block.state
                            if (state is Sign) {
                                val line0 = state.getLine(0).trim()
                                val line1 = state.getLine(1).trim()
                                
                                plugin.logger.info("发现告示牌 at $worldX,$worldY,$worldZ: Line0='$line0', Line1='$line1'")
                                
                                // 检查是否是 SLG 特殊标记
                                if (line0.equals(SLG_MARKER, ignoreCase = true) && line1.isNotEmpty()) {
                                    plugin.logger.info("识别到 SLG 标记: $line1 at $worldX,$worldY,$worldZ")
                                    processSpecialMarker(block, line1, signLocation, markers)
                                }
                            } else {
                                plugin.logger.warning("方块不是 Sign 状态: ${state.javaClass.simpleName}")
                            }
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("处理方块时出错: $x,$y,$z - ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
        
        if (signCount == 0) {
            plugin.logger.warning("Schematic 中没有找到任何告示牌方块！")
        } else {
            plugin.logger.info("总共找到 $signCount 个告示牌")
        }
    }
    
    /**
     * 处理特殊标记告示牌
     */
    private fun processSpecialMarker(block: Block, markerType: String, location: Location, markers: BuildingMarkers) {
        when (markerType.uppercase()) {
            CORE_MARKER -> {
                // 核心方块 - 替换为木桶
                block.type = Material.BARREL
                markers.coreLocation = location.clone()
                plugin.logger.fine("识别到核心方块标记: $location")
            }
            LOGISTICS_INPUT_MARKER -> {
                // 物流入口 - 替换为漏斗
                block.type = Material.HOPPER
                markers.logisticsInput = location.clone()
                plugin.logger.fine("识别到物流入口标记: $location")
            }
            LOGISTICS_OUTPUT_MARKER -> {
                // 物流出口 - 替换为漏斗
                block.type = Material.HOPPER
                markers.logisticsOutput = location.clone()
                plugin.logger.fine("识别到物流出口标记: $location")
            }
            POWER_MARKER -> {
                // 电力接口 - 替换为红石块
                block.type = Material.REDSTONE_BLOCK
                markers.powerConnection = location.clone()
                plugin.logger.fine("识别到电力接口标记: $location")
            }
            else -> {
                plugin.logger.warning("未知的 SLG 标记类型: $markerType 在 $location")
            }
        }
    }
    
    /**
     * 获取建筑 schematic 文件名
     */
    fun getSchematicFileName(buildingType: String, level: Int): String {
        return "${buildingType.lowercase()}_level$level.schem"
    }
}
