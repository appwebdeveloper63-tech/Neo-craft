package com.neocraft.game.world

const val CHUNK_WIDTH         = 16
const val CHUNK_HEIGHT        = 128   // Keep 128 for performance; can be raised to 256 later
const val WATER_LEVEL         = 62
const val ATLAS_COLS          = 16
const val ATLAS_ROWS          = 16    // EXPANDED: 8→16 rows to support 200+ block textures
const val TILE_PX             = 16
const val RENDER_DISTANCE     = 6
const val MAX_RENDER_DISTANCE = 10
const val MIN_RENDER_DISTANCE = 2
const val DAY_LENGTH          = 600f   // seconds per full day/night cycle
const val GRAVITY_INTERVAL    = 0.25f  // seconds between gravity physics ticks
const val SAVE_INTERVAL       = 120f   // auto-save every 2 minutes
const val VERSION_CODE        = 3      // incremented for save compatibility checks
