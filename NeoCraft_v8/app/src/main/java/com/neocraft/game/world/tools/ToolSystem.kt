package com.neocraft.game.world.tools

import com.neocraft.game.world.BlockType

/**
 * Tool and equipment system.
 *
 * Defines tools (pickaxe, shovel, axe, sword, hoe) across 5 tiers
 * (Wood → Stone → Iron → Diamond → Netherite) plus bow and shield.
 *
 * Each tool:
 *  - Has a durability (uses before breaking)
 *  - Has a speed multiplier for the blocks it's efficient against
 *  - Has an attack damage value
 *  - Drops the right item when a block is broken with silk touch
 */
enum class ToolMaterial(
    val displayName: String,
    val durability: Int,
    val miningSpeed: Float,
    val attackBonus: Int,
    val enchantability: Int
) {
    WOOD     ("Wood",      59,   2.0f, 0, 15),
    STONE    ("Stone",     131,  4.0f, 1, 5),
    IRON     ("Iron",      250,  6.0f, 2, 14),
    DIAMOND  ("Diamond",   1561, 8.0f, 3, 10),
    NETHERITE("Netherite", 2031, 9.0f, 4, 15),
    GOLD     ("Gold",      32,  12.0f, 0, 22)   // Fast but fragile
}

enum class ToolType(val displayName: String) {
    PICKAXE("Pickaxe"),
    SHOVEL ("Shovel"),
    AXE    ("Axe"),
    SWORD  ("Sword"),
    HOE    ("Hoe"),
    BOW    ("Bow"),
    SHIELD ("Shield"),
    NONE   ("Hand")
}

data class Tool(
    val type:     ToolType,
    val material: ToolMaterial,
    var durability: Int = material.durability
) {
    val name get() = "${material.displayName} ${type.displayName}"
    val isBroken get() = durability <= 0

    /** Returns effective mining speed multiplier for a block. */
    fun speedFor(blockId: Int): Float {
        if (type == ToolType.NONE) return 1.0f
        val isEffective = when (type) {
            ToolType.PICKAXE -> BlockType.isPickaxeEffective(blockId)
            ToolType.SHOVEL  -> BlockType.isShovelEffective(blockId)
            ToolType.AXE     -> BlockType.isAxeEffective(blockId)
            ToolType.HOE     -> BlockType.isHoeEffective(blockId)
            else             -> false
        }
        return if (isEffective) material.miningSpeed else 1.0f
    }

    /** Damage dealt to entity. */
    val attackDamage get() = when(type) {
        ToolType.SWORD  -> 4 + material.attackBonus
        ToolType.AXE    -> 3 + material.attackBonus
        ToolType.PICKAXE,
        ToolType.SHOVEL -> 2 + material.attackBonus
        else            -> 1
    }

    fun use(): Boolean {
        if (type == ToolType.NONE || type == ToolType.BOW || type == ToolType.SHIELD) return false
        durability--
        return isBroken
    }
}

// ── Extend BlockType with tool effectiveness predicates ──────────────────
fun BlockType.Companion.isPickaxeEffective(blockId: Int) = blockId in intArrayOf(
    BlockType.STONE, BlockType.COBBLESTONE, BlockType.STONE_BRICKS,
    BlockType.GRANITE, BlockType.DIORITE, BlockType.ANDESITE,
    BlockType.SMOOTH_STONE, BlockType.SANDSTONE, BlockType.MOSSY_COBBLE,
    BlockType.DEEPSLATE, BlockType.BLACKSTONE, BlockType.BASALT, BlockType.TUFF,
    BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.GOLD_ORE,
    BlockType.DIAMOND_ORE, BlockType.EMERALD_ORE, BlockType.LAPIS_ORE,
    BlockType.REDSTONE_ORE, BlockType.COPPER_ORE, BlockType.ANCIENT_DEBRIS,
    BlockType.DEEPSLATE_COAL_ORE, BlockType.DEEPSLATE_IRON_ORE,
    BlockType.DEEPSLATE_DIAMOND_ORE, BlockType.DEEPSLATE_GOLD_ORE,
    BlockType.DEEPSLATE_COPPER_ORE, BlockType.DEEPSLATE_LAPIS_ORE,
    BlockType.IRON_BLOCK, BlockType.GOLD_BLOCK, BlockType.DIAMOND_BLOCK,
    BlockType.NETHERITE_BLOCK, BlockType.COPPER_BLOCK,
    BlockType.OBSIDIAN, BlockType.BRICKS, BlockType.NETHER_BRICK,
    BlockType.PRISMARINE, BlockType.QUARTZ_BLOCK, BlockType.FURNACE,
    BlockType.BLACKSTONE_BRICKS, BlockType.GILDED_BLACKSTONE
)

fun BlockType.Companion.isShovelEffective(blockId: Int) = blockId in intArrayOf(
    BlockType.DIRT, BlockType.GRASS, BlockType.GRAVEL, BlockType.SAND,
    BlockType.RED_SAND, BlockType.COARSE_DIRT, BlockType.PODZOL,
    BlockType.CLAY, BlockType.SNOW_BLOCK, BlockType.SNOW_GRASS,
    BlockType.MUD, BlockType.MYCELIUM, BlockType.MOSS_BLOCK
)

fun BlockType.Companion.isAxeEffective(blockId: Int) = blockId in intArrayOf(
    BlockType.LOG_OAK, BlockType.LOG_BIRCH, BlockType.LOG_SPRUCE,
    BlockType.LOG_JUNGLE, BlockType.LOG_ACACIA, BlockType.LOG_DARK_OAK,
    BlockType.LOG_CRIMSON, BlockType.LOG_WARPED,
    BlockType.PLANKS_OAK, BlockType.PLANKS_JUNGLE,
    BlockType.CRAFTING_TABLE, BlockType.BOOKSHELF, BlockType.CHEST,
    BlockType.PUMPKIN, BlockType.HONEY_BLOCK
)

fun BlockType.Companion.isHoeEffective(blockId: Int) = blockId in intArrayOf(
    BlockType.LEAVES_OAK, BlockType.LEAVES_BIRCH, BlockType.LEAVES_SPRUCE,
    BlockType.LEAVES_JUNGLE, BlockType.LEAVES_ACACIA, BlockType.LEAVES_DARK_OAK,
    BlockType.LEAVES_CRIMSON, BlockType.LEAVES_WARPED,
    BlockType.WART_BLOCK, BlockType.MOSS_BLOCK, BlockType.SCULK
)

// ── Tool crafting recipes (returns a Tool or null) ────────────────────────
object ToolCrafting {
    /** Given inventory contents, determine what tool can be crafted. */
    fun canCraftTool(inv: Map<Int, Int>): Tool? {
        // Check each material tier from best to worst
        for (mat in ToolMaterial.values().reversed()) {
            val matBlock = materialBlock(mat) ?: continue
            val count = inv.getOrDefault(matBlock, 0)
            val planks = inv.getOrDefault(BlockType.PLANKS_OAK, 0)
                            .let { it + inv.getOrDefault(BlockType.PLANKS_JUNGLE, 0) }
            // Pickaxe: 3 material + 2 sticks (sticks from planks)
            if (count >= 3 && planks >= 1)
                return Tool(ToolType.PICKAXE, mat)
            // Axe: 3 material + 2 sticks
            if (count >= 3 && planks >= 1)
                return Tool(ToolType.AXE, mat)
            // Shovel: 1 material + 2 sticks
            if (count >= 1 && planks >= 1)
                return Tool(ToolType.SHOVEL, mat)
            // Sword: 2 material + 1 stick
            if (count >= 2 && planks >= 1)
                return Tool(ToolType.SWORD, mat)
        }
        return null
    }

    private fun materialBlock(mat: ToolMaterial) = when(mat) {
        ToolMaterial.WOOD      -> BlockType.PLANKS_OAK
        ToolMaterial.STONE     -> BlockType.COBBLESTONE
        ToolMaterial.IRON      -> BlockType.IRON_BLOCK
        ToolMaterial.DIAMOND   -> BlockType.DIAMOND_BLOCK
        ToolMaterial.NETHERITE -> BlockType.NETHERITE_BLOCK
        ToolMaterial.GOLD      -> BlockType.GOLD_BLOCK
    }
}
