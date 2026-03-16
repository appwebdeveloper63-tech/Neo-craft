package com.neocraft.game.entity

import com.neocraft.game.world.BlockType
import com.neocraft.game.world.World
import kotlin.math.*

// ── Cow ──────────────────────────────────────────────────────────────────
class Cow(world: World) : Entity(world) {
    override val maxHealth = 10
    override val halfW = 0.45f
    override val height = 1.4f

    private var wanderTimer = 0f
    private var wanderX = 0f; private var wanderZ = 0f
    private var panicTimer = 0f

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt; despawnTimer += dt
        val distToPlayer = distanceTo(playerX, playerY, playerZ)

        // Panic-flee if player is close
        if (distToPlayer < 5f) { panicTimer = 3f }
        panicTimer = (panicTimer - dt).coerceAtLeast(0f)

        if (panicTimer > 0f) {
            // Flee away from player
            val dx = x - playerX; val dz = z - playerZ
            val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            vx = (dx / len) * 4.5f; vz = (dz / len) * 4.5f
            yaw = Math.toDegrees(atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
        } else {
            // Wander randomly
            wanderTimer -= dt
            if (wanderTimer <= 0f) {
                wanderTimer = 2f + (sin(age * 7.3f) * 0.5f + 0.5f) * 3f
                val angle = (sin(age * 5.7f) * 0.5f + 0.5f) * 2f * PI.toFloat()
                val dist  = 3f + (sin(age * 3.1f) * 0.5f + 0.5f) * 5f
                wanderX = x + cos(angle) * dist; wanderZ = z + sin(angle) * dist
            }
            val d = distanceTo(wanderX, y, wanderZ)
            if (d > 1f) walkToward(wanderX, wanderZ, 1.5f)
            else { vx = 0f; vz = 0f }
        }

        jumpIfStuck()
        applyPhysics(dt)
        // Friction
        vx *= if (onGround) 0.7f else 0.95f
        vz *= if (onGround) 0.7f else 0.95f
    }
}

// ── Pig ──────────────────────────────────────────────────────────────────
class Pig(world: World) : Entity(world) {
    override val maxHealth = 10
    override val halfW = 0.45f
    override val height = 0.9f
    private var wanderTimer = 0f
    private var wanderX = 0f; private var wanderZ = 0f

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt
        wanderTimer -= dt
        if (wanderTimer <= 0f) {
            wanderTimer = 1.5f + (sin(age * 6.1f) * 0.5f + 0.5f) * 4f
            val angle = (sin(age * 4.9f) * 0.5f + 0.5f) * 2f * PI.toFloat()
            wanderX = x + cos(angle) * 4f; wanderZ = z + sin(angle) * 4f
        }
        val d = distanceTo(wanderX, y, wanderZ)
        if (d > 1f) walkToward(wanderX, wanderZ, 1.8f) else { vx = 0f; vz = 0f }
        jumpIfStuck(); applyPhysics(dt)
        vx *= if (onGround) 0.65f else 0.95f; vz *= if (onGround) 0.65f else 0.95f
    }
}

// ── Sheep ─────────────────────────────────────────────────────────────────
class Sheep(world: World) : Entity(world) {
    override val maxHealth = 8
    override val halfW = 0.45f
    override val height = 1.3f
    val woolColor = listOf(
        0xFFeeeeee.toInt(), 0xFFcccccc.toInt(), 0xFFcc2222.toInt(),
        0xFF2222cc.toInt(), 0xFF228822.toInt(), 0xFFeecc22.toInt()
    ).random()
    private var wanderTimer = 0f
    private var wanderX = 0f; private var wanderZ = 0f

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt
        wanderTimer -= dt
        if (wanderTimer <= 0f) {
            wanderTimer = 3f + (sin(age * 4.7f) * 0.5f + 0.5f) * 5f
            val angle = (sin(age * 3.3f) * 0.5f + 0.5f) * 2f * PI.toFloat()
            wanderX = x + cos(angle) * 5f; wanderZ = z + sin(angle) * 5f
        }
        val d = distanceTo(wanderX, y, wanderZ)
        if (d > 1f) walkToward(wanderX, wanderZ, 1.6f) else { vx = 0f; vz = 0f }
        jumpIfStuck(); applyPhysics(dt)
        vx *= if (onGround) 0.65f else 0.95f; vz *= if (onGround) 0.65f else 0.95f
    }
}

// ── Chicken ───────────────────────────────────────────────────────────────
class Chicken(world: World) : Entity(world) {
    override val maxHealth = 4
    override val halfW = 0.2f
    override val height = 0.7f
    private var wanderTimer = 0f
    private var wanderX = 0f; private var wanderZ = 0f
    var flapPhase = 0f

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt; flapPhase += dt * 8f
        // Chickens fall slowly
        vy = vy.coerceAtLeast(-2f)
        wanderTimer -= dt
        if (wanderTimer <= 0f) {
            wanderTimer = 1f + (sin(age * 9.1f) * 0.5f + 0.5f) * 2f
            val angle = (sin(age * 8.3f) * 0.5f + 0.5f) * 2f * PI.toFloat()
            wanderX = x + cos(angle) * 3f; wanderZ = z + sin(angle) * 3f
        }
        val d = distanceTo(wanderX, y, wanderZ)
        if (d > 0.5f) walkToward(wanderX, wanderZ, 2.2f) else { vx = 0f; vz = 0f }
        jumpIfStuck(); applyPhysics(dt)
        vx *= if (onGround) 0.6f else 0.95f; vz *= if (onGround) 0.6f else 0.95f
    }
}
