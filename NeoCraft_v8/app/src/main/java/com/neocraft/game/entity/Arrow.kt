package com.neocraft.game.entity

import com.neocraft.game.world.World
import kotlin.math.*

/** Projectile fired by Skeleton. Flies in arc, damages player on contact. */
class Arrow(world: World) : Entity(world) {
    override val maxHealth = 1
    override val halfW = 0.1f
    override val height = 0.1f
    var damage = 4
    var hitPlayer = false
    var lifetime = 0f

    init { health = 1 }

    fun launch(fromX: Float, fromY: Float, fromZ: Float,
               toX: Float, toY: Float, toZ: Float, speed: Float = 22f) {
        x = fromX; y = fromY; z = fromZ
        val dx = toX - fromX; val dy = toY - fromY + 1.5f; val dz = toZ - fromZ
        val len = sqrt(dx*dx + dy*dy + dz*dz).coerceAtLeast(0.001f)
        vx = (dx/len)*speed; vy = (dy/len)*speed; vz = (dz/len)*speed
    }

    override fun update(dt: Float, playerX: Float, playerY: Float, playerZ: Float) {
        lifetime += dt
        if (lifetime > 8f) { dead = true; return }

        vy -= 18f * dt   // gravity on arrow
        x += vx * dt; y += vy * dt; z += vz * dt

        yaw   = Math.toDegrees(atan2(-vx.toDouble(), -vz.toDouble())).toFloat()
        pitch = Math.toDegrees(atan2(vy.toDouble(), sqrt((vx*vx+vz*vz).toDouble()))).toFloat()

        // Hit block
        if (world.isSolidAt(x, y, z)) { dead = true; return }

        // Hit player
        val dist = distanceTo(playerX, playerY, playerZ)
        if (dist < 0.8f) { hitPlayer = true; dead = true }
    }
}
