package com.neocraft.game.entity

import com.neocraft.game.world.World
import kotlin.math.*

// ── Zombie ───────────────────────────────────────────────────────────────
class Zombie(world: World) : Entity(world) {
    override val maxHealth = 20
    override val halfW = 0.35f

    enum class State { IDLE, CHASE, ATTACK, STUNNED }
    var state = State.IDLE
    private var attackCooldown = 0f
    private var stunTimer = 0f
    private var wanderTimer = 0f
    private var wanderX = 0f; private var wanderZ = 0f

    // Knockback flash for visual feedback
    var hurtFlash = 0f

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt
        attackCooldown  = (attackCooldown  - dt).coerceAtLeast(0f)
        stunTimer       = (stunTimer       - dt).coerceAtLeast(0f)
        hurtFlash       = (hurtFlash       - dt * 4f).coerceAtLeast(0f)

        val dist = distanceTo(playerX, playerY, playerZ)

        state = when {
            stunTimer > 0f  -> State.STUNNED
            dist < 1.8f     -> State.ATTACK
            dist < 24f      -> State.CHASE
            else            -> State.IDLE
        }

        when (state) {
            State.IDLE -> {
                wanderTimer -= dt
                if (wanderTimer <= 0f) {
                    wanderTimer = 3f + (sin(age * 3.7f) * 0.5f + 0.5f) * 4f
                    val angle = sin(age * 2.9f) * PI.toFloat()
                    wanderX = x + cos(angle) * 4f; wanderZ = z + sin(angle) * 4f
                }
                val d = distanceTo(wanderX, y, wanderZ)
                if (d > 1f) walkToward(wanderX, wanderZ, 1.2f)
                else { vx *= 0.5f; vz *= 0.5f }
            }
            State.CHASE -> {
                walkToward(playerX, playerZ, 3.2f)
                jumpIfStuck()
            }
            State.ATTACK -> {
                vx *= 0.4f; vz *= 0.4f
            }
            State.STUNNED -> {
                vx *= 0.6f; vz *= 0.6f
            }
        }

        applyPhysics(dt)
        vx *= if (onGround) 0.65f else 0.95f
        vz *= if (onGround) 0.65f else 0.95f
    }

    /** Returns damage dealt this tick (0 if not attacking). */
    fun tryAttackPlayer(playerX: Float, playerY: Float, playerZ: Float): Int {
        if (state != State.ATTACK || attackCooldown > 0f) return 0
        attackCooldown = 1.2f
        return 3
    }

    fun receiveHit(damage: Int, knockX: Float, knockZ: Float) {
        takeDamage(damage)
        vx += knockX * 5f; vz += knockZ * 5f; vy = 3f
        stunTimer = 0.3f; hurtFlash = 1f
    }
}

// ── Skeleton ──────────────────────────────────────────────────────────────
class Skeleton(world: World) : Entity(world) {
    override val maxHealth = 20
    override val halfW = 0.3f

    var shootCooldown = 0f
    var hurtFlash = 0f
    private var strafeAngle = 0f

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt
        shootCooldown = (shootCooldown - dt).coerceAtLeast(0f)
        hurtFlash     = (hurtFlash - dt * 4f).coerceAtLeast(0f)

        val dist = distanceTo(playerX, playerY, playerZ)

        if (dist < 20f) {
            // Strafe around player while keeping mid-range distance
            strafeAngle += dt * 1.2f
            val idealDist = 10f
            val toPlayerX = playerX - x; val toPlayerZ = playerZ - z
            val toPlayerLen = sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ).coerceAtLeast(0.001f)
            val strafeX = -toPlayerZ / toPlayerLen
            val strafeZ  =  toPlayerX / toPlayerLen
            val pullSign = if (dist < idealDist) -1f else 1f
            vx = strafeX * 2.5f * cos(strafeAngle) + (toPlayerX / toPlayerLen) * pullSign * 2f
            vz = strafeZ * 2.5f * cos(strafeAngle) + (toPlayerZ / toPlayerLen) * pullSign * 2f
            yaw = Math.toDegrees(atan2(-toPlayerX.toDouble(), -toPlayerZ.toDouble())).toFloat()
            jumpIfStuck()
        } else { vx *= 0.5f; vz *= 0.5f }

        applyPhysics(dt)
        vx *= if (onGround) 0.7f else 0.96f
        vz *= if (onGround) 0.7f else 0.96f
    }

    /** Returns true if an arrow should be fired this tick. */
    fun tryShootArrow(dist: Float): Boolean {
        if (dist > 18f || shootCooldown > 0f) return false
        shootCooldown = 2.5f
        return true
    }

    fun receiveHit(damage: Int) {
        takeDamage(damage); hurtFlash = 1f
        vy = 2.5f
    }
}

// ── Creeper ───────────────────────────────────────────────────────────────
class Creeper(world: World) : Entity(world) {
    override val maxHealth = 20
    override val halfW = 0.3f

    enum class State { IDLE, CHASE, PRIMING, EXPLODED }
    var state = State.IDLE
    var fuseTimer = 0f        // counts up 0→1.5 s
    var hurtFlash = 0f
    var exploded  = false

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt
        hurtFlash = (hurtFlash - dt * 4f).coerceAtLeast(0f)

        val dist = distanceTo(playerX, playerY, playerZ)

        when (state) {
            State.IDLE -> {
                vx *= 0.5f; vz *= 0.5f
                if (dist < 16f) state = State.CHASE
            }
            State.CHASE -> {
                if (dist < 2.5f) { state = State.PRIMING; vx = 0f; vz = 0f }
                else if (dist > 20f) state = State.IDLE
                else walkToward(playerX, playerZ, 3.0f)
                jumpIfStuck()
            }
            State.PRIMING -> {
                fuseTimer += dt
                vx *= 0.3f; vz *= 0.3f
                // Back off slightly if player runs away
                if (dist > 5f) { state = State.CHASE; fuseTimer = 0f }
                if (fuseTimer >= 1.5f) { state = State.EXPLODED; exploded = true; dead = true }
            }
            State.EXPLODED -> { dead = true }
        }

        applyPhysics(dt)
        vx *= if (onGround) 0.65f else 0.95f
        vz *= if (onGround) 0.65f else 0.95f
    }

    fun receiveHit(damage: Int) { takeDamage(damage); hurtFlash = 1f }
}

// ── Spider ────────────────────────────────────────────────────────────────
class Spider(world: World) : Entity(world) {
    override val maxHealth = 16
    override val halfW = 0.7f
    override val height = 0.9f
    private var attackCooldown = 0f
    var hurtFlash = 0f

    init { health = maxHealth }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        age += dt
        attackCooldown = (attackCooldown - dt).coerceAtLeast(0f)
        hurtFlash      = (hurtFlash - dt * 4f).coerceAtLeast(0f)

        val dist = distanceTo(playerX, playerY, playerZ)
        if (dist < 20f) {
            walkToward(playerX, playerZ, if (dist < 6f) 4.5f else 3.5f)
            jumpIfStuck()
        } else { vx *= 0.5f; vz *= 0.5f }

        applyPhysics(dt)
        vx *= if (onGround) 0.7f else 0.96f
        vz *= if (onGround) 0.7f else 0.96f
    }

    fun tryAttack(dist: Float): Int {
        if (dist > 2.2f || attackCooldown > 0f) return 0
        attackCooldown = 1.0f; return 2
    }

    fun receiveHit(damage: Int) { takeDamage(damage); hurtFlash = 1f }
}
