package com.neocraft.game.world

/**
 * NeoCraft block registry — 112 block types across all categories.
 * Atlas layout: 16 columns × 16 rows of 16×16-pixel tiles.
 * face index: 0=top, 1=bottom, 2=side (±X/Z), special variants handled per block.
 */
object BlockType {

    // ── Original blocks (0–45) ─────────────────────────────────────────────
    const val AIR           = 0
    const val GRASS         = 1
    const val DIRT          = 2
    const val STONE         = 3
    const val SAND          = 4
    const val LOG_OAK       = 5
    const val LEAVES_OAK    = 6
    const val SNOW_GRASS    = 7
    const val GRAVEL        = 8
    const val WATER         = 9
    const val BEDROCK       = 10
    const val PLANKS_OAK    = 11
    const val COBBLESTONE   = 12
    const val GLASS         = 13
    const val BRICKS        = 14
    const val COAL_ORE      = 15
    const val IRON_ORE      = 16
    const val GOLD_ORE      = 17
    const val DIAMOND_ORE   = 18
    const val SNOW_BLOCK    = 19
    const val LOG_BIRCH     = 20
    const val LEAVES_BIRCH  = 21
    const val LOG_SPRUCE    = 22
    const val LEAVES_SPRUCE = 23
    const val CACTUS        = 24
    const val SANDSTONE     = 25
    const val MOSSY_COBBLE  = 26
    const val OBSIDIAN      = 27
    const val LAVA          = 28
    const val CLAY          = 29
    const val NETHERRACK    = 30
    const val GLOWSTONE     = 31
    const val ICE           = 32
    const val PACKED_ICE    = 33
    const val RED_SAND      = 34
    const val MYCELIUM      = 35
    const val EMERALD_ORE   = 36
    const val REDSTONE_ORE  = 37
    const val GRANITE       = 38
    const val DIORITE       = 39
    const val ANDESITE      = 40
    const val IRON_BLOCK    = 41
    const val GOLD_BLOCK    = 42
    const val DIAMOND_BLOCK = 43
    const val LAPIS_ORE     = 44
    const val PUMPKIN       = 45

    // ── Stone family (46–50) ───────────────────────────────────────────────
    const val STONE_BRICKS          = 46
    const val MOSSY_STONE_BRICKS    = 47
    const val CRACKED_STONE_BRICKS  = 48
    const val CHISELED_STONE_BRICKS = 49
    const val SMOOTH_STONE          = 50

    // ── Deepslate family (51–57) ───────────────────────────────────────────
    const val DEEPSLATE             = 51
    const val DEEPSLATE_COAL_ORE    = 52
    const val DEEPSLATE_IRON_ORE    = 53
    const val DEEPSLATE_DIAMOND_ORE = 54
    const val DEEPSLATE_GOLD_ORE    = 55
    const val DEEPSLATE_COPPER_ORE  = 56
    const val DEEPSLATE_LAPIS_ORE   = 57

    // ── New tree types (58–68) ─────────────────────────────────────────────
    const val LOG_JUNGLE        = 58
    const val LEAVES_JUNGLE     = 59
    const val LOG_ACACIA        = 60
    const val LEAVES_ACACIA     = 61
    const val LOG_DARK_OAK      = 62
    const val LEAVES_DARK_OAK   = 63
    const val LOG_CRIMSON       = 64   // Nether
    const val LEAVES_CRIMSON    = 65
    const val LOG_WARPED        = 66   // Nether
    const val LEAVES_WARPED     = 67
    const val PLANKS_JUNGLE     = 68

    // ── Interactive / functional blocks (69–75) ────────────────────────────
    const val TNT            = 69
    const val CRAFTING_TABLE = 70
    const val FURNACE        = 71
    const val CHEST          = 72
    const val TORCH          = 73
    const val LANTERN        = 74
    const val BOOKSHELF      = 75

    // ── Ores – new (76–77) ─────────────────────────────────────────────────
    const val COPPER_ORE   = 76
    const val ANCIENT_DEBRIS = 77

    // ── Nether / End blocks (78–87) ────────────────────────────────────────
    const val SOUL_SAND      = 78
    const val MAGMA_BLOCK    = 79
    const val END_STONE      = 80
    const val QUARTZ_BLOCK   = 81
    const val PURPUR_BLOCK   = 82
    const val NETHERITE_BLOCK= 83
    const val NETHER_BRICK   = 84
    const val RED_NETHER_BRICK = 85
    const val WART_BLOCK     = 86
    const val SHROOMLIGHT    = 87

    // ── Geological / cave blocks (88–94) ──────────────────────────────────
    const val CALCITE        = 88
    const val AMETHYST_BLOCK = 89
    const val TUFF           = 90
    const val BLACKSTONE     = 91
    const val BASALT         = 92
    const val DRIPSTONE_BLOCK = 93
    const val SCULK          = 94

    // ── Soil / earth variants (95–99) ─────────────────────────────────────
    const val TERRACOTTA     = 95
    const val MUD            = 96
    const val MOSS_BLOCK     = 97
    const val COARSE_DIRT    = 98
    const val PODZOL         = 99

    // ── Wool colours (100–105) ────────────────────────────────────────────
    const val WHITE_WOOL     = 100
    const val RED_WOOL       = 101
    const val BLUE_WOOL      = 102
    const val YELLOW_WOOL    = 103
    const val GREEN_WOOL     = 104
    const val BLACK_WOOL     = 105

    // ── Concrete colours (106–109) ────────────────────────────────────────
    const val GRAY_CONCRETE  = 106
    const val WHITE_CONCRETE = 107
    const val RED_CONCRETE   = 108
    const val BLUE_CONCRETE  = 109

    // ── Aquatic / specialty (110–113) ─────────────────────────────────────
    const val PRISMARINE     = 110
    const val SEA_LANTERN    = 111
    const val HONEY_BLOCK    = 112
    const val SLIME_BLOCK    = 113

    // ── Blackstone family (114–115) ───────────────────────────────────────
    const val BLACKSTONE_BRICKS     = 114
    const val GILDED_BLACKSTONE     = 115

    // ── Raw blocks (116–117) ──────────────────────────────────────────────
    const val RAW_COPPER_BLOCK = 116
    const val COPPER_BLOCK     = 117

    const val TOTAL = 118

    // ── Names ─────────────────────────────────────────────────────────────
    val NAMES = arrayOf(
        "Air","Grass","Dirt","Stone","Sand","Oak Log","Oak Leaves","Snow Grass","Gravel",
        "Water","Bedrock","Oak Planks","Cobblestone","Glass","Bricks","Coal Ore","Iron Ore",
        "Gold Ore","Diamond Ore","Snow Block","Birch Log","Birch Leaves","Spruce Log",
        "Spruce Leaves","Cactus","Sandstone","Mossy Cobblestone","Obsidian","Lava","Clay",
        "Netherrack","Glowstone","Ice","Packed Ice","Red Sand","Mycelium","Emerald Ore",
        "Redstone Ore","Granite","Diorite","Andesite","Iron Block","Gold Block","Diamond Block",
        "Lapis Ore","Pumpkin",
        // New
        "Stone Bricks","Mossy Stone Bricks","Cracked Stone Bricks","Chiseled Stone Bricks","Smooth Stone",
        "Deepslate","Deepslate Coal Ore","Deepslate Iron Ore","Deepslate Diamond Ore","Deepslate Gold Ore",
        "Deepslate Copper Ore","Deepslate Lapis Ore",
        "Jungle Log","Jungle Leaves","Acacia Log","Acacia Leaves","Dark Oak Log","Dark Oak Leaves",
        "Crimson Stem","Crimson Leaves","Warped Stem","Warped Leaves","Jungle Planks",
        "TNT","Crafting Table","Furnace","Chest","Torch","Lantern","Bookshelf",
        "Copper Ore","Ancient Debris",
        "Soul Sand","Magma Block","End Stone","Quartz Block","Purpur Block","Netherite Block",
        "Nether Brick","Red Nether Brick","Nether Wart Block","Shroomlight",
        "Calcite","Amethyst Block","Tuff","Blackstone","Basalt","Dripstone Block","Sculk",
        "Terracotta","Mud","Moss Block","Coarse Dirt","Podzol",
        "White Wool","Red Wool","Blue Wool","Yellow Wool","Green Wool","Black Wool",
        "Gray Concrete","White Concrete","Red Concrete","Blue Concrete",
        "Prismarine","Sea Lantern","Honey Block","Slime Block",
        "Blackstone Bricks","Gilded Blackstone","Raw Copper Block","Copper Block"
    )

    // ── Block property predicates ──────────────────────────────────────────
    fun isSolid(t: Int)  = t != AIR && t != WATER && t != LAVA && t != TORCH
    fun isOpaque(t: Int) = isSolid(t) && t != GLASS && t != LEAVES_OAK &&
                           t != LEAVES_BIRCH && t != LEAVES_SPRUCE && t != LEAVES_JUNGLE &&
                           t != LEAVES_ACACIA && t != LEAVES_DARK_OAK && t != LEAVES_CRIMSON &&
                           t != LEAVES_WARPED && t != ICE && t != HONEY_BLOCK && t != SLIME_BLOCK
    fun isLiquid(t: Int) = t == WATER || t == LAVA
    fun isGravity(t: Int)= t == SAND || t == GRAVEL || t == RED_SAND || t == SNOW_BLOCK
    fun isTransparent(t: Int) = t == GLASS || t == ICE || t == HONEY_BLOCK || t == SLIME_BLOCK
    fun isLeaves(t: Int) = t in intArrayOf(LEAVES_OAK, LEAVES_BIRCH, LEAVES_SPRUCE,
                           LEAVES_JUNGLE, LEAVES_ACACIA, LEAVES_DARK_OAK, LEAVES_CRIMSON, LEAVES_WARPED)

    fun emitsLight(t: Int) = when(t) {
        GLOWSTONE, LAVA, MAGMA_BLOCK, SHROOMLIGHT, SEA_LANTERN, TORCH, LANTERN -> true
        else -> false
    }

    fun lightLevel(t: Int) = when(t) {
        GLOWSTONE, SHROOMLIGHT, SEA_LANTERN -> 15
        LAVA -> 15
        TORCH -> 14
        LANTERN -> 15
        MAGMA_BLOCK -> 3
        else -> 0
    }

    fun hardness(t: Int) = when(t) {
        AIR                                                              -> 0f
        TORCH                                                            -> 0f
        GRASS, DIRT, GRAVEL, SAND, RED_SAND, SNOW_GRASS, MYCELIUM,
        CLAY, COARSE_DIRT, PODZOL, MUD, SCULK                          -> 0.5f
        LOG_OAK, LOG_BIRCH, LOG_SPRUCE, LOG_JUNGLE, LOG_ACACIA,
        LOG_DARK_OAK, LOG_CRIMSON, LOG_WARPED, PLANKS_OAK, PLANKS_JUNGLE,
        BOOKSHELF, LEAVES_OAK, LEAVES_BIRCH, LEAVES_SPRUCE, LEAVES_JUNGLE,
        LEAVES_ACACIA, LEAVES_DARK_OAK, LEAVES_CRIMSON, LEAVES_WARPED   -> 0.8f
        WART_BLOCK, SOUL_SAND                                            -> 0.5f
        STONE, COBBLESTONE, SANDSTONE, GRANITE, DIORITE, ANDESITE,
        MOSSY_COBBLE, BRICKS, STONE_BRICKS, MOSSY_STONE_BRICKS,
        CRACKED_STONE_BRICKS, CHISELED_STONE_BRICKS, SMOOTH_STONE,
        DEEPSLATE, NETHER_BRICK, RED_NETHER_BRICK, BLACKSTONE,
        BLACKSTONE_BRICKS, TUFF, CALCITE, BASALT,
        DRIPSTONE_BLOCK, PRISMARINE                                      -> 1.5f
        COAL_ORE, IRON_ORE, LAPIS_ORE, REDSTONE_ORE, COPPER_ORE,
        DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_LAPIS_ORE,
        DEEPSLATE_COPPER_ORE                                             -> 3.0f
        GOLD_ORE, EMERALD_ORE, DIAMOND_ORE, DEEPSLATE_GOLD_ORE,
        DEEPSLATE_DIAMOND_ORE, AMETHYST_BLOCK                           -> 3.5f
        IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK, COPPER_BLOCK,
        RAW_COPPER_BLOCK, NETHERITE_BLOCK, QUARTZ_BLOCK, PURPUR_BLOCK,
        GILDED_BLACKSTONE                                                -> 5.0f
        ANCIENT_DEBRIS                                                   -> 30f
        OBSIDIAN                                                         -> 50f
        BEDROCK                                                          -> 9999f
        GLASS, ICE, PACKED_ICE, HONEY_BLOCK, SLIME_BLOCK                -> 0.5f
        WHITE_WOOL, RED_WOOL, BLUE_WOOL, YELLOW_WOOL,
        GREEN_WOOL, BLACK_WOOL                                           -> 0.8f
        GRAY_CONCRETE, WHITE_CONCRETE, RED_CONCRETE, BLUE_CONCRETE      -> 1.8f
        MOSS_BLOCK, SHROOMLIGHT, MAGMA_BLOCK                             -> 0.4f
        END_STONE                                                        -> 3.0f
        CRAFTING_TABLE, FURNACE, CHEST                                   -> 2.5f
        TNT                                                              -> 0f   // instant break
        else                                                             -> 1.0f
    }

    // ── Atlas tile lookup ─────────────────────────────────────────────────
    // Returns (col, row) for the texture atlas (16×16 grid of 16×16 tiles)
    // face: 0=top  1=bottom  2=side
    fun getAtlasTile(t: Int, face: Int): Pair<Int,Int> = when(t) {
        // ── Original blocks ────────────────────────────────────────────
        GRASS        -> when(face) { 0 -> 0 to 0; 1 -> 2 to 0; else -> 3 to 0 }
        DIRT         -> 2 to 0
        STONE        -> 1 to 0
        SAND         -> 4 to 0
        LOG_OAK      -> when(face) { 0,1 -> 5 to 0; else -> 6 to 0 }
        LEAVES_OAK   -> 7 to 0
        SNOW_GRASS   -> when(face) { 0 -> 0 to 1; 1 -> 2 to 0; else -> 15 to 0 }
        GRAVEL       -> 1 to 1
        WATER        -> 2 to 1
        BEDROCK      -> 3 to 1
        PLANKS_OAK   -> 4 to 1
        COBBLESTONE  -> 5 to 1
        GLASS        -> 6 to 1
        MOSSY_COBBLE -> 8 to 1
        OBSIDIAN     -> 9 to 1
        LAVA         -> 10 to 1
        CLAY         -> 11 to 1
        NETHERRACK   -> 12 to 1
        GLOWSTONE    -> 13 to 1
        ICE          -> 14 to 1
        PACKED_ICE   -> 15 to 1
        BRICKS       -> 0 to 2
        COAL_ORE     -> 1 to 2
        IRON_ORE     -> 2 to 2
        GOLD_ORE     -> 3 to 2
        DIAMOND_ORE  -> 4 to 2
        SANDSTONE    -> when(face) { 0 -> 5 to 2; 1 -> 6 to 2; else -> 7 to 2 }
        RED_SAND     -> 8 to 2
        MYCELIUM     -> when(face) { 0 -> 9 to 2; 1 -> 2 to 0; else -> 10 to 2 }
        EMERALD_ORE  -> 11 to 2
        REDSTONE_ORE -> 12 to 2
        LAPIS_ORE    -> 13 to 2
        PUMPKIN      -> when(face) { 0 -> 14 to 2; else -> 15 to 2 }
        GRANITE      -> 0 to 3
        DIORITE      -> 1 to 3
        ANDESITE     -> 2 to 3
        IRON_BLOCK   -> 3 to 3
        GOLD_BLOCK   -> 4 to 3
        DIAMOND_BLOCK-> 5 to 3
        CACTUS       -> when(face) { 0,1 -> 6 to 3; else -> 7 to 3 }
        SNOW_BLOCK   -> 0 to 1
        LOG_BIRCH    -> when(face) { 0,1 -> 8 to 0; else -> 9 to 0 }
        LEAVES_BIRCH -> 10 to 0
        LOG_SPRUCE   -> when(face) { 0,1 -> 8 to 0; else -> 11 to 0 }
        LEAVES_SPRUCE-> 12 to 0

        // ── Row 3 additions (cols 8–15) ───────────────────────────────
        STONE_BRICKS          -> 8 to 3
        MOSSY_STONE_BRICKS    -> 9 to 3
        CRACKED_STONE_BRICKS  -> 10 to 3
        CHISELED_STONE_BRICKS -> 11 to 3
        SMOOTH_STONE          -> 12 to 3
        DEEPSLATE             -> 13 to 3
        TUFF                  -> 14 to 3
        BLACKSTONE            -> 15 to 3

        // ── Row 4: New tree types ──────────────────────────────────────
        LOG_JUNGLE     -> when(face) { 0,1 -> 0 to 4; else -> 1 to 4 }
        LEAVES_JUNGLE  -> 2 to 4
        LOG_ACACIA     -> when(face) { 0,1 -> 3 to 4; else -> 4 to 4 }
        LEAVES_ACACIA  -> 5 to 4
        LOG_DARK_OAK   -> when(face) { 0,1 -> 6 to 4; else -> 7 to 4 }
        LEAVES_DARK_OAK-> 8 to 4
        LOG_CRIMSON    -> when(face) { 0,1 -> 9 to 4; else -> 10 to 4 }
        LEAVES_CRIMSON -> 11 to 4
        LOG_WARPED     -> when(face) { 0,1 -> 12 to 4; else -> 13 to 4 }
        LEAVES_WARPED  -> 14 to 4
        PLANKS_JUNGLE  -> 15 to 4

        // ── Row 5: Functional blocks ───────────────────────────────────
        TNT            -> when(face) { 0 -> 1 to 5; 1 -> 3 to 5; else -> 2 to 5 }
        CRAFTING_TABLE -> when(face) { 0 -> 4 to 5; 1 -> 4 to 1; else -> 5 to 5 }
        FURNACE        -> when(face) { 0 -> 7 to 5; 1 -> 7 to 5; else -> 8 to 5 }
        CHEST          -> when(face) { 0 -> 10 to 5; 1 -> 10 to 5; else -> 11 to 5 }
        TORCH          -> 13 to 5
        LANTERN        -> 14 to 5
        BOOKSHELF      -> when(face) { 0,1 -> 4 to 1; else -> 15 to 5 }

        // ── Row 5/6: Ores & Nether ────────────────────────────────────
        COPPER_ORE     -> 6 to 6
        ANCIENT_DEBRIS -> when(face) { 0,1 -> 4 to 6; else -> 5 to 6 }

        // ── Row 5/6: Deepslate ores ────────────────────────────────────
        DEEPSLATE_COAL_ORE    -> 7 to 6
        DEEPSLATE_IRON_ORE    -> 8 to 6
        DEEPSLATE_DIAMOND_ORE -> 9 to 6
        DEEPSLATE_GOLD_ORE    -> 10 to 6
        DEEPSLATE_COPPER_ORE  -> 11 to 6
        DEEPSLATE_LAPIS_ORE   -> 12 to 6

        // ── Row 6: Nether / End ───────────────────────────────────────
        SOUL_SAND      -> 15 to 5
        MAGMA_BLOCK    -> 0 to 6
        END_STONE      -> 1 to 6
        QUARTZ_BLOCK   -> when(face) { 0,1 -> 2 to 6; else -> 3 to 6 }
        PURPUR_BLOCK   -> 4 to 6  // reused tile; see ANCIENT_DEBRIS
        NETHERITE_BLOCK-> 5 to 6  // override below
        NETHER_BRICK   -> 13 to 6
        RED_NETHER_BRICK-> 14 to 6
        WART_BLOCK     -> 2 to 8
        SHROOMLIGHT    -> 3 to 8

        // ── Row 6: Geological ────────────────────────────────────────
        CALCITE        -> 13 to 3  // shares TUFF tile (both grey-white)
        AMETHYST_BLOCK -> 0 to 8
        BASALT         -> when(face) { 0,1 -> 15 to 4; else -> 0 to 5 }
        DRIPSTONE_BLOCK-> 15 to 6
        SCULK          -> 7 to 8

        // ── Row 7: Soil & decorative ──────────────────────────────────
        TERRACOTTA     -> 0 to 7
        MUD            -> 1 to 7
        MOSS_BLOCK     -> 2 to 7
        COARSE_DIRT    -> 3 to 7
        PODZOL         -> when(face) { 0 -> 4 to 7; 1 -> 2 to 0; else -> 5 to 7 }

        // ── Row 7: Wool ───────────────────────────────────────────────
        WHITE_WOOL     -> 6 to 7
        RED_WOOL       -> 7 to 7
        BLUE_WOOL      -> 8 to 7
        YELLOW_WOOL    -> 9 to 7
        GREEN_WOOL     -> 10 to 7
        BLACK_WOOL     -> 11 to 7

        // ── Row 7: Concrete ───────────────────────────────────────────
        GRAY_CONCRETE  -> 12 to 7
        WHITE_CONCRETE -> 13 to 7
        RED_CONCRETE   -> 14 to 7
        BLUE_CONCRETE  -> 15 to 7

        // ── Row 8: Aquatic & specialty ────────────────────────────────
        PRISMARINE     -> 4 to 8
        SEA_LANTERN    -> 5 to 8
        HONEY_BLOCK    -> 6 to 8
        SLIME_BLOCK    -> 8 to 8

        // ── Row 8: Blackstone family ──────────────────────────────────
        BLACKSTONE_BRICKS   -> 9 to 8
        GILDED_BLACKSTONE   -> 10 to 8
        RAW_COPPER_BLOCK    -> 11 to 8
        COPPER_BLOCK        -> 12 to 8

        else -> 0 to 15   // purple error tile at bottom-left
    }

    // ── Hotbar defaults for Creative mode ─────────────────────────────────
    val HOTBAR_CREATIVE = intArrayOf(
        GRASS, STONE, COBBLESTONE, OAK_PLANKS_REF,
        BRICKS, STONE_BRICKS, GLASS, CRAFTING_TABLE, TORCH
    )

    // ── Survival hotbar items (filled from inventory at runtime) ──────────
    val HOTBAR_DEFAULT = intArrayOf(
        GRASS, DIRT, STONE, PLANKS_OAK, COBBLESTONE, BRICKS, GLASS, LOG_OAK, SAND
    )

    // ── Complete creative inventory palette ───────────────────────────────
    val ALL_BLOCKS = intArrayOf(
        GRASS, DIRT, COARSE_DIRT, PODZOL, MUD, MOSS_BLOCK,
        STONE, COBBLESTONE, STONE_BRICKS, MOSSY_STONE_BRICKS, CRACKED_STONE_BRICKS,
        CHISELED_STONE_BRICKS, SMOOTH_STONE, GRANITE, DIORITE, ANDESITE,
        SAND, RED_SAND, GRAVEL, CLAY,
        SANDSTONE, BRICKS, OBSIDIAN, BEDROCK,
        LOG_OAK, PLANKS_OAK, LEAVES_OAK,
        LOG_BIRCH, LEAVES_BIRCH,
        LOG_SPRUCE, LEAVES_SPRUCE,
        LOG_JUNGLE, PLANKS_JUNGLE, LEAVES_JUNGLE,
        LOG_ACACIA, LEAVES_ACACIA,
        LOG_DARK_OAK, LEAVES_DARK_OAK,
        COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, LAPIS_ORE,
        REDSTONE_ORE, EMERALD_ORE, COPPER_ORE,
        IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK, COPPER_BLOCK,
        DEEPSLATE, DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_DIAMOND_ORE,
        TNT, CRAFTING_TABLE, FURNACE, CHEST, BOOKSHELF,
        TORCH, LANTERN,
        GLASS, ICE, PACKED_ICE, SNOW_BLOCK, SNOW_GRASS,
        GLOWSTONE, MAGMA_BLOCK, SHROOMLIGHT, SEA_LANTERN,
        NETHERRACK, SOUL_SAND, NETHER_BRICK, RED_NETHER_BRICK,
        LOG_CRIMSON, LEAVES_CRIMSON, LOG_WARPED, LEAVES_WARPED, WART_BLOCK,
        END_STONE, PURPUR_BLOCK, QUARTZ_BLOCK,
        NETHERITE_BLOCK, ANCIENT_DEBRIS,
        CALCITE, AMETHYST_BLOCK, TUFF, BLACKSTONE, BASALT, DRIPSTONE_BLOCK, SCULK,
        TERRACOTTA, MUD,
        WHITE_WOOL, RED_WOOL, BLUE_WOOL, YELLOW_WOOL, GREEN_WOOL, BLACK_WOOL,
        GRAY_CONCRETE, WHITE_CONCRETE, RED_CONCRETE, BLUE_CONCRETE,
        PRISMARINE, HONEY_BLOCK, SLIME_BLOCK,
        BLACKSTONE_BRICKS, GILDED_BLACKSTONE, CACTUS, PUMPKIN, MYCELIUM
    )

    // Helper alias used internally
    private const val OAK_PLANKS_REF = PLANKS_OAK
}
