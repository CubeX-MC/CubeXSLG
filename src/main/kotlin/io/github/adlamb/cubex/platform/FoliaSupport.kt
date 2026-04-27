package io.github.adlamb.cubex.platform

object FoliaSupport {
    fun isFolia(): Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
