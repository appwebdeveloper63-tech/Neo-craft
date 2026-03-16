package com.neocraft.game.player

import com.neocraft.game.world.*
import kotlin.math.*

class Player(private val world: World) {

    // ── Position (feet) ───────────────────────────────────────────────────
    var x = 8.5f; var y = 80f; var z = 8.5f
    var vx = 0f;  var vy = 0f; var vz = 0f

    // ── Look ──────────────────────────────────────────────────────────────
    var yaw = 0f; var pitch = 0f

    // ── State flags ───────────────────────────────────────────────────────
    var onGround   = false
    var inWater    = false
    var flyMode    = false
    var sneaking   = false
    var sprinting  = false
    var underwater = false
    var onFire     = false

    // ── Dimensions ───────────────────────────────────────────────────────
    private val halfW = 0.30f
    val height       get() = if (sneaking) 1.5f else 1.8f
    val eyeOffsetY   get() = if (sneaking) 1.27f else 1.62f

    // ── Physics constants ─────────────────────────────────────────────────
    private val gravity     = 28f
    private val waterGrav   = 5f
    private val jumpVel     = 8.6f
    private val waterJump   = 3.5f
    private val walkSpd     = 4.3f
    private val sprintSpd   = 6.8f
    private val sneakSpd    = 1.8f
    private val flySpd      = 14f
    private val swimSpd     = 3.5f

    // ── Stats ─────────────────────────────────────────────────────────────
    var health    = 20; val maxHealth = 20
    var hunger    = 20; val maxHunger = 20
    var air       = 300; val maxAir  = 300
    var xpLevel   = 0
    var xpPoints  = 0
    var score     = 0L

    private var hungerTimer     = 0f
    private var damageCooldown  = 0f
    private var fireTick        = 0f
    private var fallDistance    = 0f
    private var prevY           = 0f

    // ── Inventory & game mode ─────────────────────────────────────────────
    val inventory = Inventory()
    var gameMode  = GameMode.SURVIVAL
        set(value) {
            field = value
            flyMode = value.canFly
            if (value == GameMode.CREATIVE) inventory.fillCreative()
        }

    // ── Block interaction ─────────────────────────────────────────────────
    var breakProgress  = 0f
    var breakTarget: Triple<Int,Int,Int>? = null

    // ── Double-tap jump for fly toggle ────────────────────────────────────
    private var lastJumpTime = 0L

    // ── Inventory UI state (read by HUD) ──────────────────────────────────
    var inventoryOpen = false

    // ── Status effects ────────────────────────────────────────────────────
    var hasNightVision = false
    var hasHaste       = false
    var hasSpeed       = false
    var effectTimer    = 0f

    // ── Update ────────────────────────────────────────────────────────────
    fun update(dt: Float, moveX: Float, moveZ: Float, jump: Boolean) {
        prevY = y
        updateEnvironment()
        if (!inventoryOpen) updateMovement(dt, moveX, moveZ, jump)
        updateStats(dt)
        updateEffects(dt)
    }

    private fun updateMovement(dt: Float, moveX: Float, moveZ: Float, jump: Boolean) {
        val mode = gameMode
        val spd = when {
            mode.instantBreak && flyMode -> flySpd * 1.5f
            flyMode    -> flySpd
            inWater    -> swimSpd * (if (hasSpeed) 1.3f else 1f)
            sneaking   -> sneakSpd
            sprinting  -> sprintSpd * (if (hasSpeed) 1.2f else 1f)
            else       -> walkSpd   * (if (hasSpeed) 1.2f else 1f)
        }

        val yr  = Math.toRadians(yaw.toDouble()).toFloat()
        val fwdX = -sin(yr); val fwdZ = -cos(yr)
        val rgtX =  cos(yr); val rgtZ = -sin(yr)

        val wMX  = fwdX * -moveZ + rgtX * moveX
        val wMZ  = fwdZ * -moveZ + rgtZ * moveX
        val len  = sqrt(wMX*wMX + wMZ*wMZ).coerceAtLeast(1f)
        val hasMov = moveX != 0f || moveZ != 0f

        if (flyMode) {
            vx = if (hasMov) (wMX/len)*spd else 0f
            vz = if (hasMov) (wMZ/len)*spd else 0f
            vy = when { jump -> flySpd*0.5f; sneaking -> -flySpd*0.5f; else -> vy*0.85f }
        } else if (inWater) {
            vx = if (hasMov) (wMX/len)*spd else vx*0.8f
            vz = if (hasMov) (wMZ/len)*spd else vz*0.8f
            vy -= waterGrav * dt
            if (jump) vy = waterJump
            vy = vy.coerceIn(-8f, 8f)
        } else {
            vx = if (hasMov) (wMX/len)*spd else 0f
            vz = if (hasMov) (wMZ/len)*spd else 0f
            vy -= gravity * dt
            if (jump && onGround) { vy = jumpVel; onGround = false }
        }

        // Per-axis collision resolution
        x += vx * dt; if (collidesWorld()) { x -= vx * dt; vx = 0f }
        z += vz * dt; if (collidesWorld()) { z -= vz * dt; vz = 0f }
        val prevYPos = y
        y += vy * dt
        if (collidesWorld()) {
            if (vy < 0) {
                // Landing
                val dropped = prevYPos - y + fallDistance
                if (dropped > 3f && !flyMode && gameMode.takeDamage) {
                    val dmg = (dropped - 3f).toInt()
                    takeDamage(dmg)
                }
                onGround = true; fallDistance = 0f
                while (collidesWorld()) y += 0.005f
            } else {
                while (collidesWorld()) y -= 0.005f
            }
            vy = 0f
        } else if (y < prevYPos) {
            onGround = false
            fallDistance += prevYPos - y
        } else {
            fallDistance = 0f
        }

        // Void fall
        if (y < -40f) {
            y = WorldGen.getHeight(x.toInt(), z.toInt()) + 5f
            vy = 0f
            if (gameMode.takeDamage) health = (health - 4).coerceAtLeast(0)
        }

        // Lava damage
        if (world.isLiquidAt(x, y + 0.1f, z) && world.getBlock(
                floor(x.toDouble()).toInt(), floor((y+0.1).toDouble()).toInt(), floor(z.toDouble()).toInt()
            ) == BlockType.LAVA) {
            onFire = true
        }
    }

    private fun updateEnvironment() {
        val eyeY = y + eyeOffsetY
        inWater    = world.isLiquidAt(x, y + 0.1f, z)
        underwater = world.isLiquidAt(x, eyeY, z)
    }

    private fun updateStats(dt: Float) {
        damageCooldown = (damageCooldown - dt).coerceAtLeast(0f)

        // Hunger drain
        if (gameMode.hungerEnabled) {
            hungerTimer += dt + if (sprinting) dt * 0.5f else 0f
            if (hungerTimer >= 30f) {
                hungerTimer = 0f
                hunger = (hunger - 1).coerceAtLeast(0)
            }
            // Regenerate health when well-fed
            if (hunger >= 18 && health < maxHealth && damageCooldown <= 0f) {
                health++; damageCooldown = 1f
            }
        }

        // Drowning
        if (underwater) {
            air = (air - 1).coerceAtLeast(0)
            if (air == 0 && damageCooldown <= 0f && gameMode.takeDamage) {
                health--; damageCooldown = 1f
            }
        } else air = (air + 5).coerceAtMost(maxAir)

        // Fire damage
        if (onFire && gameMode.takeDamage) {
            fireTick += dt
            if (fireTick >= 1f) { fireTick = 0f; health = (health-1).coerceAtLeast(0) }
        } else { fireTick = 0f; onFire = false }
    }

    private fun updateEffects(dt: Float) {
        if (effectTimer > 0f) {
            effectTimer -= dt
            if (effectTimer <= 0f) {
                hasNightVision = false; hasHaste = false; hasSpeed = false
            }
        }
    }

    fun takeDamage(amount: Int) {
        if (!gameMode.takeDamage || damageCooldown > 0f) return
        health = (health - amount).coerceAtLeast(0)
        damageCooldown = 0.5f
    }

    fun addXP(points: Int) {
        xpPoints += points
        val levelThreshold = 10 + xpLevel * 5
        while (xpPoints >= levelThreshold) {
            xpPoints -= levelThreshold; xpLevel++
        }
        score += points.toLong()
    }

    // ── Fly toggle via double-tap ─────────────────────────────────────────
    fun tryToggleFly() {
        if (!gameMode.canFly && gameMode != GameMode.CREATIVE) return
        val now = System.currentTimeMillis()
        if (now - lastJumpTime < 300L) { flyMode = !flyMode; vy = 0f }
        lastJumpTime = now
    }

    // ── Collision ─────────────────────────────────────────────────────────
    private fun collidesWorld(): Boolean {
        val hw = halfW; val h = height
        for (dx in floatArrayOf(-hw, hw)) for (dz in floatArrayOf(-hw, hw))
            for (dy in floatArrayOf(0f, h*0.33f, h*0.66f, h-0.01f))
                if (world.isSolidAt(x+dx, y+dy, z+dz)) return true
        return false
    }

    // ── Look ─────────────────────────────────────────────────────────────
    fun lookDir(): Triple<Float,Float,Float> {
        val pr = Math.toRadians(pitch.toDouble()).toFloat()
        val yr = Math.toRadians(yaw.toDouble()).toFloat()
        return Triple(-sin(yr)*cos(pr), sin(pr), -cos(yr)*cos(pr))
    }

    fun eyePos() = Triple(x, y + eyeOffsetY, z)

    // ── Block interaction ─────────────────────────────────────────────────
    fun tickBreak(dt: Float, breaking: Boolean): Boolean {
        if (inventoryOpen) return false
        val (ex,ey,ez) = eyePos(); val (ldx,ldy,ldz) = lookDir()
        val ray = world.raycast(ex, ey, ez, ldx, ldy, ldz)
        if (!breaking || !ray.hit) { breakProgress = 0f; breakTarget = null; return false }
        val tgt = Triple(ray.bx, ray.by, ray.bz)
        if (breakTarget != tgt) { breakProgress = 0f; breakTarget = tgt }
        val blk = world.getBlock(ray.bx, ray.by, ray.bz)
        val speed = if (gameMode.instantBreak) 999f else (1f / BlockType.hardness(blk).coerceAtLeast(0.01f))
        breakProgress += dt * speed
        if (breakProgress >= 1f) {
            if (gameMode != GameMode.SPECTATOR) {
                world.setBlock(ray.bx, ray.by, ray.bz, BlockType.AIR)
                // Survival: add block to inventory
                if (!gameMode.infiniteBlocks && blk != BlockType.AIR) {
                    inventory.addItem(blk, 1)
                    addXP(1)
                }
            }
            breakProgress = 0f; breakTarget = null; return true
        }
        return false
    }

    fun placeBlock() {
        if (inventoryOpen || gameMode == GameMode.SPECTATOR) return
        val blockType = inventory.hotbarBlockId
        if (blockType == BlockType.AIR) return
        val (ex,ey,ez) = eyePos(); val (ldx,ldy,ldz) = lookDir()
        val ray = world.raycast(ex, ey, ez, ldx, ldy, ldz)
        if (!ray.hit) return
        val px = ray.prevX; val py = ray.prevY; val pz = ray.prevZ
        if (!isInsidePlayer(px+0.5f, py.toFloat(), pz+0.5f)) {
            world.setBlock(px, py, pz, blockType)
            if (!gameMode.infiniteBlocks) inventory.removeFromHotbar(1)
        }
    }

    private fun isInsidePlayer(bx: Float, bby: Float, bz: Float) =
        abs(bx-x) < halfW+0.5f && bby < y+height && bby+1 > y && abs(bz-z) < halfW+0.5f

    fun heal(amt: Int) { health = (health + amt).coerceAtMost(maxHealth) }
    fun eat(foodVal: Int) { hunger = (hunger + foodVal).coerceAtMost(maxHunger) }
}
