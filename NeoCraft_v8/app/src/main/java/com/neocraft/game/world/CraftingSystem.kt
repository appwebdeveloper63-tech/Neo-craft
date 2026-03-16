package com.neocraft.game.world

/**
 * Basic crafting recipe system.
 * Supports 2×2 (survival hand) and 3×3 (crafting table) grids.
 */
object CraftingSystem {

    data class Recipe(
        val result: Int,
        val count: Int = 1,
        val ingredients: Map<Int, Int>  // blockId → quantity needed
    )

    private val recipes = listOf(
        // ── Wood & planks ──────────────────────────────────────────────
        Recipe(BlockType.PLANKS_OAK,    4, mapOf(BlockType.LOG_OAK    to 1)),
        Recipe(BlockType.PLANKS_JUNGLE, 4, mapOf(BlockType.LOG_JUNGLE to 1)),
        Recipe(BlockType.PLANKS_OAK,    4, mapOf(BlockType.LOG_BIRCH  to 1)),
        Recipe(BlockType.PLANKS_OAK,    4, mapOf(BlockType.LOG_SPRUCE to 1)),
        Recipe(BlockType.PLANKS_OAK,    4, mapOf(BlockType.LOG_ACACIA to 1)),
        Recipe(BlockType.PLANKS_OAK,    4, mapOf(BlockType.LOG_DARK_OAK to 1)),

        // ── Basic crafting ────────────────────────────────────────────
        Recipe(BlockType.CRAFTING_TABLE, 1, mapOf(BlockType.PLANKS_OAK to 4)),
        Recipe(BlockType.TORCH,         4, mapOf(BlockType.COAL_ORE   to 1, BlockType.PLANKS_OAK to 1)),
        Recipe(BlockType.CHEST,         1, mapOf(BlockType.PLANKS_OAK to 8)),

        // ── Stone ─────────────────────────────────────────────────────
        Recipe(BlockType.STONE_BRICKS,  4, mapOf(BlockType.STONE to 4)),
        Recipe(BlockType.COBBLESTONE,   4, mapOf(BlockType.STONE to 4)),  // smelting result approximation
        Recipe(BlockType.BRICKS,        4, mapOf(BlockType.CLAY  to 4)),

        // ── Sand/glass ────────────────────────────────────────────────
        Recipe(BlockType.GLASS,         1, mapOf(BlockType.SAND  to 1)),  // smelting

        // ── Iron/gold/diamond blocks ──────────────────────────────────
        Recipe(BlockType.IRON_BLOCK,    1, mapOf(BlockType.IRON_ORE    to 9)),
        Recipe(BlockType.GOLD_BLOCK,    1, mapOf(BlockType.GOLD_ORE    to 9)),
        Recipe(BlockType.DIAMOND_BLOCK, 1, mapOf(BlockType.DIAMOND_ORE to 9)),
        Recipe(BlockType.COPPER_BLOCK,  1, mapOf(BlockType.COPPER_ORE  to 9)),

        // ── Furnace ───────────────────────────────────────────────────
        Recipe(BlockType.FURNACE,       1, mapOf(BlockType.COBBLESTONE to 8)),

        // ── Snow ──────────────────────────────────────────────────────
        Recipe(BlockType.SNOW_BLOCK,    1, mapOf(BlockType.SNOW_GRASS  to 4)),

        // ── Bookshelf ─────────────────────────────────────────────────
        Recipe(BlockType.BOOKSHELF,     1, mapOf(BlockType.PLANKS_OAK  to 6, BlockType.COAL_ORE to 3)),
    )

    /**
     * Given current inventory, return list of things the player can craft.
     */
    fun availableRecipes(inv: Inventory): List<Recipe> =
        recipes.filter { canCraft(inv, it) }

    fun canCraft(inv: Inventory, recipe: Recipe): Boolean =
        recipe.ingredients.all { (blockId, qty) -> inv.countItem(blockId) >= qty }

    /**
     * Attempt to craft a recipe. Returns true on success.
     */
    fun craft(inv: Inventory, recipe: Recipe): Boolean {
        if (!canCraft(inv, recipe)) return false
        for ((blockId, qty) in recipe.ingredients)
            if (!inv.removeItem(blockId, qty)) return false
        inv.addItem(recipe.result, recipe.count)
        return true
    }

    /** Convenience: find recipe for a result block */
    fun findRecipe(resultBlockId: Int): Recipe? =
        recipes.firstOrNull { it.result == resultBlockId }
}
