package com.neocraft.game.ui

import android.view.MotionEvent
import kotlin.math.sqrt

class TouchController {

    var moveX = 0f; var moveZ = 0f
    var lookDeltaX = 0f; var lookDeltaY = 0f
    var jumpHeld        = false
    var breakHeld       = false
    var sneakHeld       = false
    var placeJustPressed      = false
    var sprintJustToggled     = false
    var inventoryJustToggled  = false
    var craftingJustToggled   = false
    var doubleTapDetected     = false

    var screenW = 1f; var screenH = 1f
    var lookSensitivity = 0.22f

    private var joystickId: Int? = null
    private var joystickOX = 0f; private var joystickOY = 0f

    private var lookId:   Int? = null
    private var lookLX = 0f; private var lookLY = 0f

    private var jumpId:      Int? = null
    private var breakId:     Int? = null
    private var sneakId:     Int? = null
    private var placeId:     Int? = null
    private var sprintId:    Int? = null
    private var inventoryId: Int? = null
    private var craftingId:  Int? = null

    private var lastJoystickDownTime = 0L
    private var joystickDoubleTap    = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)
        val ex  = event.getX(idx)
        val ey  = event.getY(idx)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onDown(pid, ex, ey)
            MotionEvent.ACTION_MOVE   -> onMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> onUp(pid)
        }
        return true
    }

    private fun onDown(pid: Int, ex: Float, ey: Float) {
        val half  = screenW * 0.5f
        val btnW  = screenW * 0.11f
        val btnH  = screenH * 0.16f
        val mg    = 16f

        // Right-side button layout (bottom-right)
        val row1Y  = screenH - btnH - mg
        val row2Y  = screenH - btnH*2 - mg*2

        // Buttons from right: JUMP | BREAK | PLACE | SNEAK | SPRINT | INV | CRAFT
        val jumpX   = screenW - btnW - mg
        val breakX  = jumpX - btnW - mg
        val placeX  = jumpX
        val placeY  = row2Y
        val sneakX  = breakX - btnW - mg
        val sprintX = sneakX - btnW - mg
        val invX    = sprintX - btnW - mg
        val craftX  = invX - btnW - mg

        when {
            ex >= jumpX && ey >= row1Y && jumpId == null -> {
                jumpId = pid; jumpHeld = true
            }
            ex in breakX..(breakX+btnW) && ey >= row1Y && breakId == null -> {
                breakId = pid; breakHeld = true
            }
            ex in placeX..(placeX+btnW) && ey in placeY..(placeY+btnH) && placeId == null -> {
                placeId = pid; placeJustPressed = true
            }
            ex in sneakX..(sneakX+btnW) && ey >= row1Y && sneakId == null -> {
                sneakId = pid; sneakHeld = true
            }
            ex in sprintX..(sprintX+btnW) && ey >= row1Y && sprintId == null -> {
                sprintId = pid; sprintJustToggled = true
            }
            ex in invX..(invX+btnW) && ey >= row1Y && inventoryId == null -> {
                inventoryId = pid; inventoryJustToggled = true
            }
            ex in craftX..(craftX+btnW) && ey >= row1Y && craftingId == null -> {
                craftingId = pid; craftingJustToggled = true
            }
            ex < half && joystickId == null -> {
                val now = System.currentTimeMillis()
                if (now - lastJoystickDownTime < 280L) joystickDoubleTap = true
                lastJoystickDownTime = now
                joystickId = pid; joystickOX = ex; joystickOY = ey; moveX = 0f; moveZ = 0f
            }
            ex >= half && lookId == null -> {
                lookId = pid; lookLX = ex; lookLY = ey
            }
        }
    }

    private fun onMove(event: MotionEvent) {
        for (i in 0 until event.pointerCount) {
            val pid = event.getPointerId(i)
            val ex = event.getX(i); val ey = event.getY(i)
            when (pid) {
                joystickId -> {
                    val maxR = screenW * 0.11f
                    var dx = ex - joystickOX; var dy = ey - joystickOY
                    val len = sqrt(dx*dx + dy*dy)
                    if (len > maxR) { val s = maxR/len; dx *= s; dy *= s }
                    moveX = dx / maxR; moveZ = dy / maxR
                }
                lookId -> {
                    lookDeltaX += (ex - lookLX) * lookSensitivity
                    lookDeltaY += (ey - lookLY) * lookSensitivity
                    lookLX = ex; lookLY = ey
                }
            }
        }
    }

    private fun onUp(pid: Int) {
        when (pid) {
            joystickId  -> { joystickId=null;  moveX=0f; moveZ=0f }
            lookId      -> { lookId=null }
            jumpId      -> { jumpId=null;      jumpHeld=false }
            breakId     -> { breakId=null;     breakHeld=false }
            sneakId     -> { sneakId=null;     sneakHeld=false }
            placeId     -> { placeId=null }
            sprintId    -> { sprintId=null }
            inventoryId -> { inventoryId=null }
            craftingId  -> { craftingId=null }
        }
    }

    fun consumeLookDelta(): Pair<Float,Float> {
        val d = Pair(lookDeltaX, lookDeltaY); lookDeltaX=0f; lookDeltaY=0f; return d
    }
    fun consumePlace():           Boolean { val v=placeJustPressed;      placeJustPressed=false;     return v }
    fun consumeSprintToggle():    Boolean { val v=sprintJustToggled;     sprintJustToggled=false;    return v }
    fun consumeDoubleTap():       Boolean { val v=joystickDoubleTap;     joystickDoubleTap=false;    return v }
    fun consumeInventoryToggle(): Boolean { val v=inventoryJustToggled;  inventoryJustToggled=false; return v }
    fun consumeCraftingToggle():  Boolean { val v=craftingJustToggled;   craftingJustToggled=false;  return v }
}
