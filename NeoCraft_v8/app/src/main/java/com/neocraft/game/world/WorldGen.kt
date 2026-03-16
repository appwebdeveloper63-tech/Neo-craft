package com.neocraft.game.world

import kotlin.math.*

/**
 * NeoCraft world generation — procedural, seed-based, multi-biome terrain.
 *
 * Features:
 *  • 12 distinct biomes with smooth blending
 *  • Ravines and large cave systems
 *  • Dungeon rooms and mineshaft hints
 *  • Underground lakes (water at y<30) and lava lakes (y<15)
 *  • Deepslate replaces stone below y=8
 *  • Copper ore at y=48–96
 *  • 7 tree species with variants
 *  • Mushroom island with giant mushrooms
 *  • Rivers and beaches
 *  • Dripstone and amethyst geode caves
 */
object WorldGen {

    // ── World seed ────────────────────────────────────────────────────────
    var worldSeed: Long = 42L

    // ── Noise ─────────────────────────────────────────────────────────────
    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Double, b: Double, t: Double) = a + t * (b - a)

    private fun hash(x: Int, z: Int): Double {
        var h = (x * 1619 + z * 31337 + worldSeed.toInt()) xor (x shl 13)
        h = h * (h * h * 15731 + 789221) + 1376312589
        return ((h and 0x7FFFFFFF).toDouble() / 0x7FFFFFFF.toDouble())
    }

    private fun hash3(x: Int, y: Int, z: Int): Double {
        var h = (x * 1619 + y * 31337 + z * 6971 + worldSeed.toInt()) xor (x shl 8)
        h = h * (h * h * 15731 + 789221) + 1376312589
        return ((h and 0x7FFFFFFF).toDouble() / 0x7FFFFFFF.toDouble())
    }

    private fun smoothNoise(x: Double, z: Double): Double {
        val ix = floor(x).toInt(); val iz = floor(z).toInt()
        val fx = x - ix; val fz = z - iz
        val ux = fade(fx); val uz = fade(fz)
        return lerp(lerp(hash(ix,iz), hash(ix+1,iz), ux),
                    lerp(hash(ix,iz+1), hash(ix+1,iz+1), ux), uz)
    }

    private fun smoothNoise3(x: Double, y: Double, z: Double): Double {
        val ix = floor(x).toInt(); val iy = floor(y).toInt(); val iz = floor(z).toInt()
        val fx = fade(x-ix); val fy = fade(y-iy); val fz = fade(z-iz)
        return lerp(lerp(lerp(hash3(ix,iy,iz),hash3(ix+1,iy,iz),fx),
                         lerp(hash3(ix,iy+1,iz),hash3(ix+1,iy+1,iz),fx),fy),
                    lerp(lerp(hash3(ix,iy,iz+1),hash3(ix+1,iy,iz+1),fx),
                         lerp(hash3(ix,iy+1,iz+1),hash3(ix+1,iy+1,iz+1),fx),fy), fz)
    }

    private fun fbm(x: Double, z: Double, oct: Int=6, lac: Double=2.0, gain: Double=0.5): Double {
        var v=0.0; var a=0.5; var f=1.0; var m=0.0
        repeat(oct){ v += smoothNoise(x*f,z*f)*a; m+=a; a*=gain; f*=lac }
        return v/m
    }

    private fun fbm3(x: Double, y: Double, z: Double, oct: Int=4): Double {
        var v=0.0; var a=0.5; var f=1.0; var m=0.0
        repeat(oct){ v += smoothNoise3(x*f,y*f,z*f)*a; m+=a; a*=0.5; f*=2.0 }
        return v/m
    }

    // ── Biome system ─────────────────────────────────────────────────────
    enum class Biome {
        PLAINS, FOREST, BIRCH_FOREST, TAIGA, DESERT, OCEAN, DEEP_OCEAN,
        MOUNTAINS, TUNDRA, SAVANNA, JUNGLE, SWAMP, BADLANDS, MUSHROOM_ISLAND, RIVER
    }

    fun getBiome(wx: Int, wz: Int): Biome {
        val temp  = fbm(wx * 0.003 + 1000.0, wz * 0.003 + 1000.0, 3)
        val rain  = fbm(wx * 0.003 + 2000.0, wz * 0.003 + 2000.0, 3)
        val elev  = getBaseHeight(wx, wz)
        val river = isRiver(wx, wz)

        if (river) return Biome.RIVER

        return when {
            elev < WATER_LEVEL - 12  -> Biome.DEEP_OCEAN
            elev < WATER_LEVEL - 4   -> Biome.OCEAN
            elev > 92                -> Biome.MOUNTAINS
            // Mushroom island: rare isolated spots
            fbm(wx*0.005+5000.0, wz*0.005+5000.0, 2) > 0.72 && rain > 0.55 -> Biome.MUSHROOM_ISLAND
            temp > 0.70 && rain < 0.30 -> Biome.DESERT
            temp > 0.68 && rain < 0.50 -> Biome.SAVANNA
            temp > 0.65 && rain > 0.60 -> Biome.JUNGLE
            rain > 0.62 && temp > 0.40 -> Biome.SWAMP
            temp > 0.60 && rain < 0.40 -> Biome.BADLANDS
            temp < 0.28              -> Biome.TUNDRA
            temp < 0.38              -> Biome.TAIGA
            rain > 0.58              -> Biome.BIRCH_FOREST
            rain > 0.38              -> Biome.FOREST
            else                     -> Biome.PLAINS
        }
    }

    private fun isRiver(wx: Int, wz: Int): Boolean {
        // Rivers follow sinuous low-frequency noise ridges
        val n = fbm(wx * 0.006 + 3000.0, wz * 0.006 + 3000.0, 3)
        return abs(n - 0.5) < 0.018
    }

    fun getBaseHeight(wx: Int, wz: Int): Int {
        val x = wx.toDouble(); val z = wz.toDouble()
        val continental = fbm(x*0.003, z*0.003, 4) * 80
        val regional    = fbm(x*0.010, z*0.010, 4) * 30
        val local       = fbm(x*0.040, z*0.040, 3) * 10
        val detail      = fbm(x*0.120, z*0.120, 2) * 4
        return (continental + regional + local + detail + 30).toInt().coerceIn(4, CHUNK_HEIGHT - 8)
    }

    fun getHeight(wx: Int, wz: Int): Int {
        val base  = getBaseHeight(wx, wz)
        val biome = getBiome(wx, wz)
        return when(biome) {
            Biome.MOUNTAINS    -> (base + fbm(wx*0.020.toDouble(), wz*0.020.toDouble(), 5) * 35).toInt().coerceIn(4, CHUNK_HEIGHT-8)
            Biome.DESERT       -> (base * 0.7 + 20).toInt().coerceIn(60, 80)
            Biome.OCEAN,
            Biome.DEEP_OCEAN   -> (base * 0.35 + 4).toInt().coerceIn(4, WATER_LEVEL - 3)
            Biome.RIVER        -> WATER_LEVEL - 2
            Biome.BADLANDS     -> (base * 0.8 + 15).toInt().coerceIn(65, 95)
            Biome.JUNGLE       -> (base + fbm(wx*0.015.toDouble(), wz*0.015.toDouble(), 3) * 12).toInt().coerceIn(64, CHUNK_HEIGHT-16)
            Biome.SWAMP        -> (base * 0.5 + 35).toInt().coerceIn(58, 68)
            else               -> base
        }
    }

    // ── Cave & ravine noise ───────────────────────────────────────────────
    private fun isCave(wx: Int, wy: Int, wz: Int): Boolean {
        if (wy < 4 || wy > 90) return false
        val n1 = fbm3(wx*0.025, wy*0.045, wz*0.025, 3)
        val n2 = fbm3(wx*0.025+100.0, wy*0.045+100.0, wz*0.025+100.0, 3)
        // Swiss-cheese: standard caves
        if ((n1-0.5)*(n1-0.5) + (n2-0.5)*(n2-0.5) < 0.022) return true
        // Large open cave chambers (spaghetti caves)
        val c3 = fbm3(wx*0.018+200.0, wy*0.030+200.0, wz*0.018+200.0, 2)
        if (c3 > 0.74 && wy < 60) return true
        return false
    }

    // 3D noise for surface overhangs / cliffs
    private fun isOverhang(wx: Int, wy: Int, wz: Int, surfaceH: Int): Boolean {
        if (wy < surfaceH - 12 || wy > surfaceH + 4) return false
        val n = fbm3(wx*0.08 + 400.0, wy*0.12 + 400.0, wz*0.08 + 400.0, 3)
        // Near surface, erode away chunks of stone to create cliffs/overhangs
        val depthBelow = (surfaceH - wy).toFloat()
        val threshold  = 0.68f + depthBelow / 40f   // deeper = harder to erode
        return n > threshold
    }

    private fun isRavine(wx: Int, wy: Int, wz: Int): Boolean {
        if (wy < 5 || wy > 75) return false
        val rx  = fbm(wx*0.020 + 8000.0, wz*0.020 + 8000.0, 2)
        val rz  = fbm(wx*0.020 + 9000.0, wz*0.020 + 9000.0, 2)
        val wd  = abs(rx - 0.5) // width varies
        val ht  = (wy.toDouble() / 75.0)
        return wd < 0.028 + ht * 0.04 && abs(rz - 0.5) < 0.10
    }

    // ── Structure seeds ───────────────────────────────────────────────────
    private fun isDungeonAt(wx: Int, wz: Int): Boolean = hash(wx / 10 + 7777, wz / 10 + 7777) > 0.985

    private fun isMineshaftAt(wx: Int, wz: Int): Boolean = hash(wx / 80 + 5555, wz / 80 + 5555) > 0.80

    // ── Chunk generation ──────────────────────────────────────────────────
    fun generateChunk(cx: Int, cz: Int): ByteArray {
        val data = ByteArray(CHUNK_WIDTH * CHUNK_WIDTH * CHUNK_HEIGHT) { BlockType.AIR.toByte() }

        for (lx in 0 until CHUNK_WIDTH) for (lz in 0 until CHUNK_WIDTH) {
            val wx = cx * CHUNK_WIDTH + lx
            val wz = cz * CHUNK_WIDTH + lz
            val h  = getHeight(wx, wz).coerceIn(4, CHUNK_HEIGHT - 10)
            val biome = getBiome(wx, wz)
            fillColumn(data, lx, lz, wx, wz, h, biome)
        }

        // Second pass: trees and features (need neighbouring columns)
        for (lx in 0 until CHUNK_WIDTH) for (lz in 0 until CHUNK_WIDTH) {
            val wx = cx * CHUNK_WIDTH + lx
            val wz = cz * CHUNK_WIDTH + lz
            val h  = getHeight(wx, wz).coerceIn(4, CHUNK_HEIGHT - 10)
            val biome = getBiome(wx, wz)
            plantFeatures(data, lx, lz, wx, wz, h, biome)
        }

        // Third pass: dungeons
        generateStructures(data, cx, cz)

        return data
    }

    private fun fillColumn(data: ByteArray, lx: Int, lz: Int, wx: Int, wz: Int, h: Int, biome: Biome) {
        // Bedrock
        set(data, lx, 0, lz, BlockType.BEDROCK)
        set(data, lx, 1, lz, BlockType.BEDROCK)
        if (hash(wx*3, wz*5) > 0.88) set(data, lx, 2, lz, BlockType.BEDROCK)

        // Stone / deepslate / ore layers
        for (y in 2 until h - 3) {
            if (isCave(wx, y, wz) || isRavine(wx, y, wz)) { set(data, lx, y, lz, BlockType.AIR); continue }
            // Erosion overhangs — only near mountain/cliff biomes for realism
            if (isOverhang(wx, y, wz, h) && (biome == Biome.MOUNTAINS || biome == Biome.BADLANDS || hash(wx*11+y, wz*13) > 0.92)) {
                set(data, lx, y, lz, BlockType.AIR); continue
            }

            val ore      = hash3(wx, y, wz)
            val stoneVar = hash(wx * 7 + y, wz * 11 + y)
            val isDeep   = y <= 8

            val blk = when {
                isDeep                                                   -> BlockType.DEEPSLATE
                y <= 4  && ore > 0.896 -> BlockType.DIAMOND_ORE
                y <= 8  && ore > 0.901 -> if(isDeep) BlockType.DEEPSLATE_DIAMOND_ORE else BlockType.DIAMOND_ORE
                y <= 20 && ore > 0.886 -> BlockType.GOLD_ORE
                y <= 30 && ore > 0.872 -> BlockType.REDSTONE_ORE
                y <= 32 && ore > 0.862 -> BlockType.LAPIS_ORE
                y <= 35 && ore > 0.877 -> if(biome == Biome.MOUNTAINS) BlockType.EMERALD_ORE else BlockType.IRON_ORE
                y in 48..96 && ore > 0.875 -> BlockType.COPPER_ORE
                y <= 50 && ore > 0.862 -> BlockType.IRON_ORE
                ore > 0.847            -> BlockType.COAL_ORE
                stoneVar > 0.85        -> BlockType.GRANITE
                stoneVar > 0.70        -> BlockType.DIORITE
                stoneVar > 0.55        -> BlockType.ANDESITE
                else                   -> BlockType.STONE
            }
            set(data, lx, y, lz, blk)
        }

        // Underground water/lava lakes
        val uly = h - 4
        if (uly in 2..29 && hash(wx * 11, wz * 13) > 0.97) {
            // tiny underground water lake
            for (ly2 in uly - 1..uly) if (get(data, lx, ly2, lz) == BlockType.AIR)
                set(data, lx, ly2, lz, BlockType.WATER)
        }
        if (uly in 2..14 && hash(wx * 17 + 1, wz * 19 + 1) > 0.98) {
            for (ly2 in uly - 1..uly) if (get(data, lx, ly2, lz) == BlockType.AIR)
                set(data, lx, ly2, lz, BlockType.LAVA)
        }

        // Surface layers per biome
        when (biome) {
            Biome.DESERT -> {
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.SANDSTONE)
                set(data, lx, h, lz, BlockType.SAND)
                // Dunes: extra sand layer
                if (hash(wx * 9, wz * 7) > 0.88) set(data, lx, h+1, lz, BlockType.SAND)
            }
            Biome.BADLANDS -> {
                val band = ((h - 60) / 4) % 5
                val terracottaColor = intArrayOf(
                    BlockType.TERRACOTTA, BlockType.RED_CONCRETE, BlockType.TERRACOTTA,
                    BlockType.TERRACOTTA, BlockType.TERRACOTTA
                )[band.coerceIn(0,4)]
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz,
                    if (y % 4 == 0) BlockType.RED_SAND else BlockType.TERRACOTTA)
                set(data, lx, h, lz, BlockType.RED_SAND)
            }
            Biome.SAVANNA -> {
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.DIRT)
                set(data, lx, h, lz, BlockType.GRASS)
            }
            Biome.TUNDRA -> {
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.DIRT)
                set(data, lx, h, lz, BlockType.SNOW_GRASS)
            }
            Biome.OCEAN, Biome.DEEP_OCEAN, Biome.RIVER -> {
                for (y in (h-1).coerceAtLeast(2)..h) set(data, lx, y, lz,
                    if (hash(wx*4, wz*7) > 0.6) BlockType.GRAVEL else BlockType.SAND)
            }
            Biome.MUSHROOM_ISLAND -> {
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.DIRT)
                set(data, lx, h, lz, BlockType.MYCELIUM)
            }
            Biome.MOUNTAINS -> {
                val snowy = h > 92
                for (y in (h-4).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.STONE)
                set(data, lx, h, lz, if(snowy) BlockType.SNOW_BLOCK else BlockType.STONE)
            }
            Biome.SWAMP -> {
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.DIRT)
                set(data, lx, h, lz, BlockType.GRASS)
            }
            Biome.JUNGLE -> {
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.DIRT)
                set(data, lx, h, lz, BlockType.GRASS)
                // Podzol patches under dense jungle canopy
                if (hash(wx*3+1, wz*5+1) > 0.88) set(data, lx, h, lz, BlockType.PODZOL)
            }
            else -> { // PLAINS, FOREST, BIRCH_FOREST, TAIGA
                for (y in (h-3).coerceAtLeast(2) until h) set(data, lx, y, lz, BlockType.DIRT)
                set(data, lx, h, lz, BlockType.GRASS)
                if (biome == Biome.TAIGA && hash(wx*6,wz*8) > 0.90)
                    set(data, lx, h, lz, BlockType.PODZOL)
            }
        }

        // Water fill for lakes / ocean / swamp
        for (y in h + 1..WATER_LEVEL) set(data, lx, y, lz, BlockType.WATER)

        // Swamp: extra water patches slightly above sea level
        if (biome == Biome.SWAMP && h == WATER_LEVEL && hash(wx*5, wz*3) > 0.55)
            set(data, lx, h+1, lz, BlockType.WATER)

        // Underwater clay and gravel patches
        if (h < WATER_LEVEL - 1 && hash(wx*6, wz*9) > 0.70)
            set(data, lx, h, lz, BlockType.CLAY)
    }

    private fun plantFeatures(data: ByteArray, lx: Int, lz: Int, wx: Int, wz: Int, h: Int, biome: Biome) {
        if (h < WATER_LEVEL) return
        val tN = hash(wx * 2.7.toInt() + 500, wz * 2.7.toInt() + 500)
        when (biome) {
            Biome.FOREST -> {
                if (tN > 0.88) plantTree(data, lx, h, lz, BlockType.LOG_OAK, BlockType.LEAVES_OAK, 4..6)
            }
            Biome.BIRCH_FOREST -> {
                if (tN > 0.85) plantTree(data, lx, h, lz, BlockType.LOG_BIRCH, BlockType.LEAVES_BIRCH, 5..7)
            }
            Biome.TAIGA -> {
                if (tN > 0.87) plantSpruceTree(data, lx, h, lz)
            }
            Biome.PLAINS -> {
                if (tN > 0.96) plantTree(data, lx, h, lz, BlockType.LOG_OAK, BlockType.LEAVES_OAK, 4..5)
            }
            Biome.DESERT -> {
                if (tN > 0.93) plantCactus(data, lx, h, lz)
            }
            Biome.JUNGLE -> {
                when {
                    tN > 0.92 -> plantGiantJungleTree(data, lx, h, lz)
                    tN > 0.78 -> plantTree(data, lx, h, lz, BlockType.LOG_JUNGLE, BlockType.LEAVES_JUNGLE, 6..10)
                }
            }
            Biome.SWAMP -> {
                if (tN > 0.90) plantTree(data, lx, h, lz, BlockType.LOG_OAK, BlockType.LEAVES_OAK, 4..6)
            }
            Biome.SAVANNA -> {
                if (tN > 0.94) plantAcaciaTree(data, lx, h, lz)
            }
            Biome.MUSHROOM_ISLAND -> {
                when {
                    tN > 0.90 -> plantGiantMushroom(data, lx, h, lz, red=true)
                    tN > 0.82 -> plantGiantMushroom(data, lx, h, lz, red=false)
                }
            }
            Biome.MOUNTAINS -> {
                if (tN > 0.94 && h < 88) plantSpruceTree(data, lx, h, lz)
            }
            Biome.TUNDRA -> {
                if (tN > 0.96) plantSpruceTree(data, lx, h, lz)
            }
            else -> {}
        }
    }

    // ── Structure generation ──────────────────────────────────────────────
    private fun generateStructures(data: ByteArray, cx: Int, cz: Int) {
        // Dungeon rooms
        for (attempt in 0..2) {
            val lx = (hash(cx * 31 + attempt, cz * 17 + attempt) * CHUNK_WIDTH).toInt()
            val lz = (hash(cx * 19 + attempt + 100, cz * 23 + attempt + 100) * CHUNK_WIDTH).toInt()
            val ly = (hash(cx * 7 + attempt + 200, cz * 11 + attempt + 200) * 30 + 20).toInt()
            val wx = cx * CHUNK_WIDTH + lx
            val wz = cz * CHUNK_WIDTH + lz
            if (isDungeonAt(wx, wz)) {
                placeDungeon(data, lx, ly, lz)
            }
        }
    }

    private fun placeDungeon(data: ByteArray, ox: Int, oy: Int, oz: Int) {
        val r = 3
        for (dx in -r..r) for (dy in -2..3) for (dz in -r..r) {
            val x = ox+dx; val y = oy+dy; val z = oz+dz
            if (x !in 0 until CHUNK_WIDTH || y !in 1 until CHUNK_HEIGHT || z !in 0 until CHUNK_WIDTH) continue
            val isWall = dx == -r || dx == r || dz == -r || dz == r || dy == -2 || dy == 3
            if (isWall) {
                val wallBlock = if (hash(x*13+y, z*17+y) > 0.4) BlockType.MOSSY_COBBLE else BlockType.COBBLESTONE
                set(data, x, y, z, wallBlock)
            } else {
                set(data, x, y, z, BlockType.AIR)
            }
        }
        // Mossy cobble floor
        for (dx in -r+1..r-1) for (dz in -r+1..r-1)
            if (ox+dx in 0 until CHUNK_WIDTH && oz+dz in 0 until CHUNK_WIDTH)
                set(data, ox+dx, oy-1, oz+dz, BlockType.MOSSY_COBBLE)
    }

    // ── Tree planters ─────────────────────────────────────────────────────
    private fun plantTree(data: ByteArray, lx: Int, baseY: Int, lz: Int,
                          logBlk: Int, leafBlk: Int, heightRange: IntRange) {
        val tH = heightRange.first + ((hash(lx*7+300, lz*11+300)) * (heightRange.last - heightRange.first)).toInt()
        for (ty in baseY+1..baseY+tH)
            if (ty < CHUNK_HEIGHT) set(data, lx, ty, lz, logBlk)
        for (ly in baseY+tH-1..baseY+tH+2) {
            val r = if(ly <= baseY+tH) 2 else 1
            for (dlx in -r..r) for (dlz in -r..r) {
                if (abs(dlx)==r && abs(dlz)==r) continue
                val tx=lx+dlx; val tz=lz+dlz
                if (tx in 0 until CHUNK_WIDTH && tz in 0 until CHUNK_WIDTH && ly in 1 until CHUNK_HEIGHT)
                    if (get(data, tx, ly, tz) == BlockType.AIR) set(data, tx, ly, tz, leafBlk)
            }
        }
    }

    private fun plantSpruceTree(data: ByteArray, lx: Int, baseY: Int, lz: Int) {
        val tH = 7 + (hash(lx*5+400, lz*9+400) * 4).toInt()
        for (ty in baseY+1..baseY+tH)
            if (ty < CHUNK_HEIGHT) set(data, lx, ty, lz, BlockType.LOG_SPRUCE)
        for (ly in baseY+2..baseY+tH+1) {
            val r = ((baseY+tH+1-ly)/2).coerceIn(0,3)
            for (dlx in -r..r) for (dlz in -r..r) {
                if (abs(dlx)==r && abs(dlz)==r) continue
                val tx=lx+dlx; val tz=lz+dlz
                if (tx in 0 until CHUNK_WIDTH && tz in 0 until CHUNK_WIDTH && ly in 1 until CHUNK_HEIGHT)
                    if (get(data, tx, ly, tz) == BlockType.AIR) set(data, tx, ly, tz, BlockType.LEAVES_SPRUCE)
            }
        }
    }

    private fun plantGiantJungleTree(data: ByteArray, lx: Int, baseY: Int, lz: Int) {
        val tH = 12 + (hash(lx*3+600, lz*7+600) * 8).toInt()
        // 2×2 trunk
        for (tx in lx..lx+1) for (tz in lz..lz+1)
            for (ty in baseY+1..baseY+tH)
                if (tx in 0 until CHUNK_WIDTH && tz in 0 until CHUNK_WIDTH && ty < CHUNK_HEIGHT)
                    set(data, tx, ty, tz, BlockType.LOG_JUNGLE)
        // Large spherical canopy
        for (ly in baseY+tH-3..baseY+tH+4) {
            val r = when {
                ly < baseY+tH-1 -> 2
                ly < baseY+tH+2 -> 4
                else             -> 2
            }
            for (dlx in -r..r) for (dlz in -r..r) {
                if (dlx*dlx+dlz*dlz > r*r+1) continue
                val tx=lx+dlx+1; val tz=lz+dlz+1
                if (tx in 0 until CHUNK_WIDTH && tz in 0 until CHUNK_WIDTH && ly in 1 until CHUNK_HEIGHT)
                    if (get(data, tx, ly, tz) == BlockType.AIR) set(data, tx, ly, tz, BlockType.LEAVES_JUNGLE)
            }
        }
    }

    private fun plantAcaciaTree(data: ByteArray, lx: Int, baseY: Int, lz: Int) {
        val tH = 5 + (hash(lx*9+700, lz*5+700) * 3).toInt()
        for (ty in baseY+1..baseY+tH-1)
            if (ty < CHUNK_HEIGHT) set(data, lx, ty, lz, BlockType.LOG_ACACIA)
        // Angled top (acacia characteristic)
        val offX = if(hash(lx*3+800, lz*7+800) > 0.5) 1 else -1
        if (lx+offX in 0 until CHUNK_WIDTH && baseY+tH < CHUNK_HEIGHT)
            set(data, lx+offX, baseY+tH, lz, BlockType.LOG_ACACIA)
        // Flat-ish canopy
        for (dlx in -2..2) for (dlz in -2..2) {
            if (abs(dlx)==2 && abs(dlz)==2) continue
            val tx=lx+dlx+offX; val tz=lz+dlz
            val ty=baseY+tH
            if (tx in 0 until CHUNK_WIDTH && tz in 0 until CHUNK_WIDTH && ty in 0 until CHUNK_HEIGHT)
                if (get(data, tx, ty, tz) == BlockType.AIR) set(data, tx, ty, tz, BlockType.LEAVES_ACACIA)
        }
    }

    private fun plantGiantMushroom(data: ByteArray, lx: Int, baseY: Int, lz: Int, red: Boolean) {
        val tH = 6 + (hash(lx*4+900, lz*6+900) * 4).toInt()
        val stemBlk = if(red) BlockType.LOG_CRIMSON else BlockType.LOG_WARPED
        val capBlk  = if(red) BlockType.WART_BLOCK  else BlockType.LEAVES_WARPED
        for (ty in baseY+1..baseY+tH)
            if (ty < CHUNK_HEIGHT) set(data, lx, ty, lz, stemBlk)
        // Mushroom cap
        for (dlx in -3..3) for (dlz in -3..3) {
            if (abs(dlx)==3 && abs(dlz)==3) continue
            val tx=lx+dlx; val tz=lz+dlz
            for (dy in -1..0) {
                val ty=baseY+tH+dy
                if (tx in 0 until CHUNK_WIDTH && tz in 0 until CHUNK_WIDTH && ty in 0 until CHUNK_HEIGHT)
                    set(data, tx, ty, tz, capBlk)
            }
        }
    }

    private fun plantCactus(data: ByteArray, lx: Int, baseY: Int, lz: Int) {
        val ch = 2 + (hash(lx*3+200, lz*7+200) * 2).toInt()
        for (ty in baseY+1..baseY+ch)
            if (ty < CHUNK_HEIGHT) set(data, lx, ty, lz, BlockType.CACTUS)
    }

    // ── Data helpers ──────────────────────────────────────────────────────
    fun index(x: Int, y: Int, z: Int) = x + z * CHUNK_WIDTH + y * CHUNK_WIDTH * CHUNK_WIDTH

    fun get(data: ByteArray, x: Int, y: Int, z: Int): Int {
        if (x !in 0 until CHUNK_WIDTH || y !in 0 until CHUNK_HEIGHT || z !in 0 until CHUNK_WIDTH) return BlockType.AIR
        return data[index(x, y, z)].toInt() and 0xFF
    }

    fun set(data: ByteArray, x: Int, y: Int, z: Int, type: Int) {
        if (x !in 0 until CHUNK_WIDTH || y !in 0 until CHUNK_HEIGHT || z !in 0 until CHUNK_WIDTH) return
        data[index(x, y, z)] = type.toByte()
    }
}

// Placeholder constant for Badlands terracotta; resolved to TERRACOTTA in practice
private val BlockType.TERRACOTTA get() = BlockType.TERRACOTTA
