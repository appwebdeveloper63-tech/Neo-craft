package com.neocraft.game.world

/**
 * Player inventory system — 36 slots (9 hotbar + 27 main storage)
 * Plus 4 armor slots and off-hand.
 */
data class ItemStack(val blockId: Int, var count: Int) {
    val isEmpty get() = blockId == BlockType.AIR || count <= 0
    val maxStack get() = when(blockId) {
        BlockType.WATER, BlockType.LAVA -> 1
        else -> 64
    }
    fun canStackWith(other: ItemStack) = other.blockId == blockId && count < maxStack
}

class Inventory {

    companion object {
        const val HOTBAR_SIZE = 9
        const val MAIN_SIZE   = 27
        const val TOTAL_SLOTS = HOTBAR_SIZE + MAIN_SIZE   // 36
        const val ARMOR_SLOTS = 4   // helmet, chest, legs, boots
    }

    // Main storage: slots 0–8 = hotbar, slots 9–35 = main inventory
    val slots = Array<ItemStack?>(TOTAL_SLOTS) { null }

    // Hotbar selected index
    var selectedSlot = 0

    val hotbarItem get() = slots[selectedSlot]
    val hotbarBlockId get() = hotbarItem?.blockId ?: BlockType.AIR

    // ── Scroll / select ───────────────────────────────────────────────────
    fun scrollHotbar(dir: Int) {
        selectedSlot = ((selectedSlot + dir) + HOTBAR_SIZE) % HOTBAR_SIZE
    }

    fun selectSlot(idx: Int) {
        if (idx in 0 until HOTBAR_SIZE) selectedSlot = idx
    }

    // ── Add items ─────────────────────────────────────────────────────────
    fun addItem(blockId: Int, count: Int = 1): Int {
        var remaining = count
        // First fill existing stacks in hotbar, then main
        for (i in 0 until TOTAL_SLOTS) {
            val slot = slots[i]
            if (slot != null && slot.blockId == blockId && !slot.isEmpty) {
                val canAdd = slot.maxStack - slot.count
                val add = minOf(canAdd, remaining)
                slot.count += add; remaining -= add
                if (remaining <= 0) return 0
            }
        }
        // Then fill empty slots
        for (i in 0 until TOTAL_SLOTS) {
            if (slots[i] == null || slots[i]!!.isEmpty) {
                val add = minOf(64, remaining)
                slots[i] = ItemStack(blockId, add)
                remaining -= add
                if (remaining <= 0) return 0
            }
        }
        return remaining // return what couldn't fit
    }

    fun removeFromHotbar(count: Int = 1): Boolean {
        val slot = slots[selectedSlot] ?: return false
        if (slot.count < count) return false
        slot.count -= count
        if (slot.count <= 0) slots[selectedSlot] = null
        return true
    }

    fun removeItem(blockId: Int, count: Int = 1): Boolean {
        val total = countItem(blockId)
        if (total < count) return false
        var remaining = count
        for (i in 0 until TOTAL_SLOTS) {
            val slot = slots[i] ?: continue
            if (slot.blockId == blockId) {
                val rem = minOf(slot.count, remaining)
                slot.count -= rem; remaining -= rem
                if (slot.count <= 0) slots[i] = null
                if (remaining <= 0) return true
            }
        }
        return true
    }

    fun countItem(blockId: Int): Int =
        slots.filterNotNull().filter { it.blockId == blockId }.sumOf { it.count }

    fun swapSlots(a: Int, b: Int) {
        val tmp = slots[a]; slots[a] = slots[b]; slots[b] = tmp
    }

    fun clear() { for (i in slots.indices) slots[i] = null }

    // ── Creative inventory setup ───────────────────────────────────────────
    fun fillCreative() {
        val all = BlockType.ALL_BLOCKS
        for (i in 0 until minOf(TOTAL_SLOTS, all.size))
            slots[i] = ItemStack(all[i], 64)
    }

    // ── Hotbar display ────────────────────────────────────────────────────
    fun getHotbarBlocks(): IntArray {
        return IntArray(HOTBAR_SIZE) { i ->
            slots[i]?.blockId ?: BlockType.AIR
        }
    }

    // ── Serialization helpers ─────────────────────────────────────────────
    fun serialize(): IntArray {
        val out = IntArray(TOTAL_SLOTS * 2)
        for (i in 0 until TOTAL_SLOTS) {
            val s = slots[i]
            out[i*2]   = s?.blockId ?: 0
            out[i*2+1] = s?.count   ?: 0
        }
        return out
    }

    fun deserialize(data: IntArray) {
        for (i in 0 until TOTAL_SLOTS) {
            val bid = data.getOrElse(i*2)   { 0 }
            val cnt = data.getOrElse(i*2+1) { 0 }
            slots[i] = if (bid == 0 || cnt == 0) null else ItemStack(bid, cnt)
        }
    }
}
