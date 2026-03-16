package com.neocraft.game.ai

import com.neocraft.game.world.BlockType
import com.neocraft.game.world.World
import kotlin.math.*

/**
 * Pre-built structure templates the AI can place instantly.
 * Each template is a list of relative block placements (dx, dy, dz, blockId).
 */
object BuildingTemplates {

    data class Template(
        val name: String,
        val description: String,
        val blocks: List<BlockPlacement>,
        val sizeX: Int, val sizeY: Int, val sizeZ: Int
    )

    data class BlockPlacement(val dx: Int, val dy: Int, val dz: Int, val blockId: Int)

    val templates = mapOf(
        "cabin"         to buildCabin(),
        "tower"         to buildTower(),
        "pyramid"       to buildPyramid(),
        "bridge"        to buildBridge(),
        "house"         to buildHouse(),
        "gazebo"        to buildGazebo(),
        "castle_wall"   to buildCastleWall(),
        "lighthouse"    to buildLighthouse(),
        "well"          to buildWell(),
        "arch"          to buildArch()
    )

    fun place(world: World, templateName: String, originX: Int, originY: Int, originZ: Int): Boolean {
        val t = templates[templateName.lowercase().replace(" ", "_")] ?: return false
        for (b in t.blocks) {
            val wx = originX + b.dx; val wy = originY + b.dy; val wz = originZ + b.dz
            if (wy in 0..127) world.setBlock(wx, wy, wz, b.blockId)
        }
        return true
    }

    fun getNames() = templates.keys.toList()

    // ── Template builders ─────────────────────────────────────────────────

    private fun buildCabin(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 7×5×7 log cabin
        // Floor
        for (x in 0..6) for (z in 0..6) blocks += bp(x,0,z, B.PLANKS_OAK)
        // Walls
        for (y in 1..4) {
            for (x in 0..6) { blocks += bp(x,y,0,B.LOG_OAK); blocks += bp(x,y,6,B.LOG_OAK) }
            for (z in 1..5) { blocks += bp(0,y,z,B.LOG_OAK); blocks += bp(6,y,z,B.LOG_OAK) }
        }
        // Windows
        for (z in intArrayOf(2,4)) { blocks += bp(0,2,z,B.GLASS); blocks += bp(6,2,z,B.GLASS) }
        for (x in intArrayOf(2,4)) { blocks += bp(x,2,0,B.GLASS); blocks += bp(x,2,6,B.GLASS) }
        // Door opening
        blocks += bp(3,1,0,B.AIR); blocks += bp(3,2,0,B.AIR)
        // Roof
        for (y in 5..7) {
            val inset = y - 5
            for (x in (0+inset)..(6-inset)) {
                blocks += bp(x,y,3-inset,B.LOG_SPRUCE); blocks += bp(x,y,3+inset,B.LOG_SPRUCE)
                if (inset==0) for (z in 0..6) blocks += bp(x,y,z,B.LOG_SPRUCE)
            }
        }
        // Torches inside
        blocks += bp(1,1,1,B.TORCH); blocks += bp(5,1,1,B.TORCH)
        blocks += bp(1,1,5,B.TORCH); blocks += bp(5,1,5,B.TORCH)
        return Template("Cozy Log Cabin","A warm 7×7 log cabin with windows and torches", blocks,7,8,7)
    }

    private fun buildTower(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 5×24×5 stone tower
        for (y in 0..23) {
            for (x in 0..4) for (z in 0..4) {
                val wall = x==0||x==4||z==0||z==4
                if (wall) blocks += bp(x,y,z, if(y%4==0) B.STONE_BRICKS else B.COBBLESTONE)
            }
            // Windows every 6 blocks
            if (y%6==3) {
                blocks += bp(0,y,2,B.GLASS); blocks += bp(4,y,2,B.GLASS)
                blocks += bp(2,y,0,B.GLASS); blocks += bp(2,y,4,B.GLASS)
            }
            // Interior torches every 6
            if (y%6==0 && y>0) blocks += bp(2,y,2,B.TORCH)
        }
        // Battlements at top
        for (x in 0..4 step 2) { blocks += bp(x,24,0,B.STONE_BRICKS); blocks += bp(x,24,4,B.STONE_BRICKS) }
        for (z in 0..4 step 2) { blocks += bp(0,24,z,B.STONE_BRICKS); blocks += bp(4,24,z,B.STONE_BRICKS) }
        // Floor planks at intervals
        for (y in intArrayOf(6,12,18,24)) for (x in 1..3) for (z in 1..3) blocks += bp(x,y,z,B.PLANKS_OAK)
        return Template("Stone Tower","A 5×5×24 tower with battlements and windows", blocks,5,25,5)
    }

    private fun buildPyramid(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        // 15×8×15 sandstone pyramid
        for (y in 0..7) {
            val inset = y
            for (x in inset..(14-inset)) for (z in inset..(14-inset)) {
                val edge = x==inset||x==14-inset||z==inset||z==14-inset||y==7
                if (edge) blocks += bp(x,y,z,BlockType.SANDSTONE)
            }
        }
        // Hidden chamber at base
        for (x in 5..9) for (y in 0..2) for (z in 5..9) {
            if (x==5||x==9||z==5||z==9||y==0) blocks += bp(x,y,z,BlockType.SANDSTONE)
            else blocks += bp(x,y,z,BlockType.AIR)
        }
        // Torch in chamber
        blocks += bp(7,1,7,BlockType.TORCH)
        return Template("Sandstone Pyramid","A 15×15×8 pyramid with a hidden chamber", blocks,15,8,15)
    }

    private fun buildBridge(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 3×4×16 stone bridge with railings
        for (z in 0..15) {
            // Bridge deck
            for (x in 0..2) blocks += bp(x,0,z,B.STONE_BRICKS)
            // Railings (every 2 blocks)
            if (z%2==0) { blocks += bp(0,1,z,B.COBBLESTONE); blocks += bp(2,1,z,B.COBBLESTONE) }
            // Arch supports at quarter and three-quarter
            if (z==4||z==12) for (y in -3..-1) { blocks += bp(0,y,z,B.STONE_BRICKS); blocks += bp(2,y,z,B.STONE_BRICKS) }
        }
        // Lanterns
        for (z in intArrayOf(2,7,12)) { blocks += bp(0,2,z,B.LANTERN); blocks += bp(2,2,z,B.LANTERN) }
        return Template("Stone Bridge","A 3×16 stone bridge with lanterns and railings", blocks,3,4,16)
    }

    private fun buildHouse(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 9×6×7 house
        // Foundation
        for (x in 0..8) for (z in 0..6) blocks += bp(x,-1,z,B.STONE_BRICKS)
        // Walls
        for (y in 0..3) {
            for (x in 0..8) { blocks += bp(x,y,0,B.PLANKS_OAK); blocks += bp(x,y,6,B.PLANKS_OAK) }
            for (z in 1..5) { blocks += bp(0,y,z,B.PLANKS_OAK); blocks += bp(8,y,z,B.PLANKS_OAK) }
        }
        // Door
        blocks += bp(4,0,0,B.AIR); blocks += bp(4,1,0,B.AIR)
        // Windows
        for (x in intArrayOf(1,6)) for (z in intArrayOf(1,5)) blocks += bp(x,1,z,B.GLASS)
        for (z in intArrayOf(1,5)) blocks += bp(4,1,z,B.GLASS)
        // Log corners
        for (y in 0..3) for (corner in listOf(Triple(0,y,0),Triple(8,y,0),Triple(0,y,6),Triple(8,y,6)))
            blocks += bp(corner.first,corner.second,corner.third,B.LOG_OAK)
        // Simple gable roof
        for (y in 4..7) {
            val inset = y-4
            for (x in 0..8) {
                if (inset<=3) {
                    blocks += bp(x,y,inset,B.LOG_SPRUCE)
                    if (inset!=3) blocks += bp(x,y,6-inset,B.LOG_SPRUCE)
                }
            }
        }
        // Interior floor
        for (x in 1..7) for (z in 1..5) blocks += bp(x,0,z,B.PLANKS_OAK)
        // Torches
        blocks += bp(2,3,1,B.TORCH); blocks += bp(6,3,1,B.TORCH)
        blocks += bp(2,3,5,B.TORCH); blocks += bp(6,3,5,B.TORCH)
        // Chest and crafting
        blocks += bp(1,0,1,B.CHEST); blocks += bp(7,0,1,B.CRAFTING_TABLE)
        blocks += bp(7,0,5,B.FURNACE)
        return Template("Starter House","A complete 9×7 house with furnishings", blocks,9,8,7)
    }

    private fun buildGazebo(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 7×5×7 octagonal gazebo
        val cx=3; val cz=3
        // Floor
        for (x in 0..6) for (z in 0..6) {
            val d = max(abs(x-cx), abs(z-cz))
            if (d<=3) blocks += bp(x,0,z,B.PLANKS_OAK)
        }
        // Pillars at corners
        for ((px,pz) in listOf(0 to 0, 6 to 0, 0 to 6, 6 to 6, 3 to 0, 3 to 6, 0 to 3, 6 to 3))
            for (y in 1..4) blocks += bp(px,y,pz,B.LOG_OAK)
        // Roof
        for (y in 5..7) {
            val r = 3-(y-5)
            for (x in (cx-r)..(cx+r)) for (z in (cz-r)..(cz+r)) {
                if (max(abs(x-cx),abs(z-cz))==r) blocks += bp(x,y,z,B.LOG_SPRUCE)
            }
        }
        blocks += bp(cx,8,cz,B.GLOWSTONE)
        // Benches
        for (x in 1..5) { blocks += bp(x,1,1,B.PLANKS_OAK); blocks += bp(x,1,5,B.PLANKS_OAK) }
        // Hanging lanterns
        for ((lx,lz) in listOf(0 to 0, 6 to 0, 0 to 6, 6 to 6))
            blocks += bp(lx,4,lz,B.LANTERN)
        return Template("Garden Gazebo","A beautiful 7×7 gazebo with benches and lanterns", blocks,7,9,7)
    }

    private fun buildCastleWall(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 20×8×3 castle wall section
        for (z in 0..2) for (x in 0..19) {
            for (y in 0..6) blocks += bp(x,y,z,B.STONE_BRICKS)
            // Battlements
            if (x%2==0) blocks += bp(x,7,z,B.STONE_BRICKS)
        }
        // Arrow slits
        for (x in intArrayOf(4,9,14)) for (z in 0..2) blocks += bp(x,3,z,B.AIR)
        // Walk path top
        for (x in 0..19) blocks += bp(x,6,1,B.STONE_BRICKS)
        // Torches along wall
        for (x in intArrayOf(2,9,17)) blocks += bp(x,5,1,B.TORCH)
        return Template("Castle Wall","A 20-block castle wall section with battlements", blocks,20,8,3)
    }

    private fun buildLighthouse(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 5×32×5 lighthouse
        for (y in 0..31) {
            for (x in 0..4) for (z in 0..4) {
                if (x==0||x==4||z==0||z==4)
                    blocks += bp(x,y,z,if(y%8<2) B.RED_WOOL else B.WHITE_WOOL)
            }
        }
        // Glass lantern room at top
        for (x in 0..4) for (z in 0..4) blocks += bp(x,32,z,B.GLASS)
        // Bright light inside lantern
        blocks += bp(2,33,2,B.GLOWSTONE)
        blocks += bp(2,32,2,B.GLOWSTONE)
        // Balcony
        for (x in -1..5) for (z in -1..5) blocks += bp(x,28,z,B.STONE_BRICKS)
        // Door
        blocks += bp(2,0,0,B.AIR); blocks += bp(2,1,0,B.AIR)
        // Torches on balcony corners
        for ((lx,lz) in listOf(-1 to -1, 5 to -1, -1 to 5, 5 to 5))
            blocks += bp(lx,29,lz,B.TORCH)
        return Template("Lighthouse","A 5×5×33 lighthouse with red/white bands", blocks,5,34,5)
    }

    private fun buildWell(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 5×4×5 stone well
        // Base ring
        for (x in 0..4) for (z in 0..4) {
            if (x==0||x==4||z==0||z==4) blocks += bp(x,0,z,B.STONE_BRICKS)
        }
        // Wall 2 layers
        for (y in 1..2) for (x in 0..4) for (z in 0..4) {
            if ((x==0||x==4||z==0||z==4)) blocks += bp(x,y,z,B.COBBLESTONE)
        }
        // Water inside
        for (x in 1..3) for (z in 1..3) blocks += bp(x,-1,z,B.WATER)
        // Roof posts
        blocks += bp(0,3,0,B.LOG_OAK); blocks += bp(4,3,0,B.LOG_OAK)
        blocks += bp(0,3,4,B.LOG_OAK); blocks += bp(4,3,4,B.LOG_OAK)
        // Crossbeam
        for (x in 0..4) blocks += bp(x,4,2,B.LOG_OAK)
        // Overhang
        for (x in 0..4) for (z in 0..4) blocks += bp(x,5,z,B.PLANKS_OAK)
        // Lantern
        blocks += bp(2,4,2,B.LANTERN)
        return Template("Stone Well","A 5×5 well with roof and lantern", blocks,5,6,5)
    }

    private fun buildArch(): Template {
        val blocks = mutableListOf<BlockPlacement>()
        val B = BlockType
        // 9×7×3 stone arch
        for (z in 0..2) {
            // Pillars
            for (y in 0..5) { blocks += bp(0,y,z,B.STONE_BRICKS); blocks += bp(8,y,z,B.STONE_BRICKS) }
            // Arch curve
            val archPoints = listOf(1 to 5, 2 to 6, 3 to 6, 4 to 6, 5 to 6, 6 to 6, 7 to 5)
            for ((x,y) in archPoints) blocks += bp(x,y,z,B.STONE_BRICKS)
            // Keystone
            blocks += bp(4,7,z,B.GOLD_BLOCK)
        }
        return Template("Stone Arch","A decorative 9×7 stone arch with gold keystone", blocks,9,8,3)
    }

    private fun bp(dx: Int, dy: Int, dz: Int, blockId: Int) = BlockPlacement(dx, dy, dz, blockId)
}
