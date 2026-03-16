package com.neocraft.game.entity

import com.neocraft.game.world.*
import kotlin.math.*

/**
 * Manages all entity spawning, updating, and despawning.
 *
 * Spawn rules:
 * - Passive mobs: only on grass/dirt in daylight, max 20 per player
 * - Hostile mobs: only in darkness (y < 60 or night), max 30 per player
 * - No mob within 4 blocks of player spawn (avoids instant attack)
 * - Despawn if > 80 blocks from player for > 30 seconds
 */
class MobManager(private val world: World) {

    val entities = mutableListOf<Entity>()

    private var spawnTimer   = 0f
    private var totalTime    = 0f
    private var explosionQueue = mutableListOf<Triple<Float, Float, Float>>()  // pending TNT / Creeper blasts

    // ── Update all entities ───────────────────────────────────────────────
    fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float,
               dayFactor: Float, rainIntensity: Float): List<ExplosionEvent> {
        totalTime += dt
        spawnTimer += dt

        val explosions = mutableListOf<ExplosionEvent>()

        // Update entities
        val toRemove = mutableListOf<Entity>()
        for (e in entities) {
            e.update(dt, playerX, playerY, playerZ)

            // Attack player
            when (e) {
                is Zombie -> {
                    val dmg = e.tryAttackPlayer(playerX, playerY, playerZ)
                    if (dmg > 0) explosions.add(ExplosionEvent(e.x, e.y, e.z, 0f, dmg))
                }
                is Creeper -> {
                    if (e.exploded) {
                        explosions.add(ExplosionEvent(e.x, e.y, e.z, 3.5f, 0))
                        blastBlocks(e.x, e.y, e.z, 3.5f)
                    }
                }
                is Arrow -> {
                    if (e.hitPlayer) explosions.add(ExplosionEvent(e.x, e.y, e.z, 0f, e.damage))
                }
                is Skeleton -> {
                    val dist = e.distanceTo(playerX, playerY, playerZ)
                    if (e.tryShootArrow(dist)) {
                        val arrow = Arrow(world)
                        arrow.launch(e.x, e.y + e.eyeHeight, e.z, playerX, playerY + 1f, playerZ)
                        entities.add(arrow)
                    }
                }
                else -> {}
            }

            // Despawn rule
            val distToPlayer = e.distanceTo(playerX, playerY, playerZ)
            if (distToPlayer > 80f) e.despawnTimer += dt else e.despawnTimer = 0f
            if (e.dead || e.despawnTimer > 30f) toRemove.add(e)
        }
        entities.removeAll(toRemove)

        // Spawn new mobs periodically
        if (spawnTimer >= 3f) {
            spawnTimer = 0f
            val isNight = dayFactor < 0.3f
            val passiveCount = entities.count { it is Cow || it is Pig || it is Sheep || it is Chicken }
            val hostileCount = entities.count { it is Zombie || it is Skeleton || it is Creeper || it is Spider }

            if (passiveCount < 20 && dayFactor > 0.5f)
                trySpawnPassive(playerX, playerY, playerZ)
            if (hostileCount < 30 && (isNight || rainIntensity > 0.5f))
                trySpawnHostile(playerX, playerY, playerZ)
        }

        return explosions
    }

    // ── Spawn logic ───────────────────────────────────────────────────────
    private fun trySpawnPassive(px: Float, py: Float, pz: Float) {
        val candidate = findSpawnPos(px, pz, 12f, 48f, requireGrass = true) ?: return
        val mob: Entity = when ((sin(totalTime * 73.1f) * 0.5f + 0.5f) * 4f) {
            in 0f..1f  -> Cow(world)
            in 1f..2f  -> Pig(world)
            in 2f..3f  -> Sheep(world)
            else       -> Chicken(world)
        }
        mob.x = candidate.first + 0.5f
        mob.y = candidate.second.toFloat() + 1f
        mob.z = candidate.third + 0.5f
        mob.yaw = (sin(totalTime * 47.3f) * 180f)
        entities.add(mob)
    }

    private fun trySpawnHostile(px: Float, py: Float, pz: Float) {
        val candidate = findSpawnPos(px, pz, 16f, 48f, requireGrass = false) ?: return
        val mob: Entity = when ((sin(totalTime * 91.7f) * 0.5f + 0.5f) * 4f) {
            in 0f..1.6f -> Zombie(world)
            in 1.6f..2.8f -> Skeleton(world)
            in 2.8f..3.5f -> Creeper(world)
            else          -> Spider(world)
        }
        mob.x = candidate.first + 0.5f
        mob.y = candidate.second.toFloat() + 1f
        mob.z = candidate.third + 0.5f
        entities.add(mob)
    }

    private fun findSpawnPos(px: Float, pz: Float, minDist: Float, maxDist: Float,
                              requireGrass: Boolean): Triple<Float, Int, Float>? {
        repeat(12) {
            val angle  = (sin(totalTime * (it * 37.1f + 11.3f)) * 0.5f + 0.5f) * 2f * PI.toFloat()
            val dist   = minDist + (sin(totalTime * (it * 19.7f)) * 0.5f + 0.5f) * (maxDist - minDist)
            val cx     = px + cos(angle) * dist
            val cz     = pz + sin(angle) * dist
            val surfaceY = WorldGen.getHeight(cx.toInt(), cz.toInt())
            val surfaceBlk = world.getBlock(cx.toInt(), surfaceY, cz.toInt())
            if (requireGrass && surfaceBlk != BlockType.GRASS && surfaceBlk != BlockType.DIRT) continue
            if (!world.isChunkReady(World.worldToChunk(cx.toInt()), World.worldToChunk(cz.toInt()))) continue
            return Triple(cx, surfaceY, cz)
        }
        return null
    }

    // ── Explosion block damage ────────────────────────────────────────────
    private fun blastBlocks(cx: Float, cy: Float, cz: Float, radius: Float) {
        val r = radius.toInt() + 1
        for (dx in -r..r) for (dy in -r..r) for (dz in -r..r) {
            val dist = sqrt((dx*dx + dy*dy + dz*dz).toFloat())
            if (dist > radius) continue
            val wx = cx.toInt() + dx; val wy = cy.toInt() + dy; val wz = cz.toInt() + dz
            val blk = world.getBlock(wx, wy, wz)
            if (blk != BlockType.AIR && blk != BlockType.BEDROCK &&
                BlockType.hardness(blk) < 40f) {    // don't blast obsidian
                world.setBlock(wx, wy, wz, BlockType.AIR)
            }
        }
    }

    /** Hit entity nearest to ray origin within maxDist. Returns damage dealt. */
    fun hitEntity(rayOX: Float, rayOY: Float, rayOZ: Float,
                  rayDX: Float, rayDY: Float, rayDZ: Float,
                  maxDist: Float = 5f, damage: Int = 5): Int {
        var bestDist = Float.MAX_VALUE
        var bestEntity: Entity? = null
        for (e in entities) {
            if (e is Arrow) continue
            // Simple AABB-ray check: project entity onto ray
            val tx = e.x - rayOX; val ty = e.y + e.height * 0.5f - rayOY; val tz = e.z - rayOZ
            val t  = tx * rayDX + ty * rayDY + tz * rayDZ
            if (t < 0f || t > maxDist) continue
            val px = rayOX + rayDX * t; val py = rayOY + rayDY * t; val pz = rayOZ + rayDZ * t
            val distSq = (px - e.x).pow(2) + (py - (e.y + e.height * 0.5f)).pow(2) + (pz - e.z).pow(2)
            if (distSq < (e.halfW * 1.5f).pow(2) + 0.2f && t < bestDist) {
                bestDist = t; bestEntity = e
            }
        }
        bestEntity ?: return 0
        val kx = rayDX * 3f; val kz = rayDZ * 3f
        when (val e = bestEntity) {
            is Zombie   -> e.receiveHit(damage, kx, kz)
            is Skeleton -> e.receiveHit(damage)
            is Creeper  -> e.receiveHit(damage)
            is Spider   -> e.receiveHit(damage)
            else        -> bestEntity.takeDamage(damage)
        }
        return damage
    }

    data class ExplosionEvent(
        val x: Float, val y: Float, val z: Float,
        val radius: Float,   // 0 = melee attack, >0 = explosion
        val damage: Int
    )
}
