package com.neocraft.game.entity

import com.neocraft.game.world.BlockType
import com.neocraft.game.world.World
import kotlin.math.*

/** Base class for all living entities (mobs + dropped items). */
abstract class Entity(protected val world: World) {

    var x = 0f; var y = 0f; var z = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var yaw = 0f; var pitch = 0f

    var health   = 20; open val maxHealth get() = 20
    var dead     = false
    var onGround = false
    var inWater  = false
    var age      = 0f           // seconds since spawn
    var despawnTimer = 0f

    open val halfW = 0.3f
    open val height = 1.8f
    open val eyeHeight get() = height * 0.9f

    protected val gravity   = 25f
    protected val waterGrav = 4f

    abstract fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float)

    protected fun applyPhysics(dt: Float) {
        val eyeY = y + eyeHeight * 0.5f
        inWater = world.isLiquidAt(x, eyeY, z)

        if (!inWater) vy -= gravity * dt else { vy -= waterGrav * dt; vy = vy.coerceIn(-6f, 6f) }

        x += vx * dt; if (collidesWorld()) { x -= vx * dt; vx = 0f }
        z += vz * dt; if (collidesWorld()) { z -= vz * dt; vz = 0f }
        y += vy * dt
        if (collidesWorld()) {
            if (vy < 0) { onGround = true; while (collidesWorld()) y += 0.01f }
            else        { while (collidesWorld()) y -= 0.01f }
            vy = 0f
        } else onGround = false

        if (y < -20f) dead = true
    }

    private fun collidesWorld(): Boolean {
        for (dx in floatArrayOf(-halfW, halfW))
            for (dz in floatArrayOf(-halfW, halfW))
                for (dy in floatArrayOf(0f, height * 0.5f, height - 0.01f))
                    if (world.isSolidAt(x + dx, y + dy, z + dz)) return true
        return false
    }

    fun takeDamage(amount: Int) {
        health -= amount
        if (health <= 0) { health = 0; dead = true }
    }

    fun distanceTo(px: Float, py: Float, pz: Float) =
        sqrt((x - px).pow(2) + (y - py).pow(2) + (z - pz).pow(2))

    /** Walk toward (tx,tz) at given speed. */
    protected fun walkToward(tx: Float, tz: Float, speed: Float) {
        val dx = tx - x; val dz = tz - z
        val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        vx = (dx / len) * speed; vz = (dz / len) * speed
        yaw = Math.toDegrees(atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
    }

    /** Jump if blocked horizontally. */
    protected fun jumpIfStuck() {
        if (onGround && (abs(vx) < 0.05f || abs(vz) < 0.05f)) {
            vy = 7.5f; onGround = false
        }
    }
}
