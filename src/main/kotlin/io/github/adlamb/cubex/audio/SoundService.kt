package io.github.adlamb.cubex.audio

import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player

object SoundService {

    fun playAt(location: Location, sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
        location.world?.playSound(location, sound, volume, pitch)
    }

    fun playTo(player: Player, sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
        player.world.playSound(player.location, sound, volume, pitch)
    }

    fun playError(player: Player) {
        playTo(player, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.7f)
    }
}
