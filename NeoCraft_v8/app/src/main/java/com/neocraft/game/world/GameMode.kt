package com.neocraft.game.world

/**
 * Game mode definitions and per-mode flags.
 */
enum class GameMode(
    val displayName: String,
    val canFly: Boolean,
    val takeDamage: Boolean,
    val instantBreak: Boolean,
    val infiniteBlocks: Boolean,
    val hungerEnabled: Boolean
) {
    SURVIVAL  ("Survival",  false, true,  false, false, true),
    CREATIVE  ("Creative",  true,  false, true,  true,  false),
    ADVENTURE ("Adventure", false, true,  false, false, true),
    SPECTATOR ("Spectator", true,  false, true,  true,  false);

    companion object {
        fun fromOrdinal(ord: Int): GameMode = values().getOrElse(ord) { SURVIVAL }
    }
}
