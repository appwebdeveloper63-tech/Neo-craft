package com.neocraft.game.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.neocraft.game.world.*
import kotlin.math.*

class HudView(context: Context, private val touch: TouchController) : View(context) {

    // ── State set each frame by GameRenderer ──────────────────────────────
    var selectedSlot  = 0
    var fps           = 60
    var coordText     = ""
    var blockName     = ""
    var breakProgress = 0f
    var health        = 20;  var maxHealth = 20
    var hunger        = 20;  var maxHunger = 20
    var air           = 300; var maxAir   = 300
    var xpLevel       = 0;   var xpPoints  = 0
    var underwater    = false
    var dayFactor     = 1f
    var totalTime     = 0f
    var biomeText     = ""
    var gameModeName  = "Survival"
    var onFire        = false
    var inventoryOpen = false
    var hotbarBlocks  = IntArray(9) { BlockType.AIR }
    var score         = 0L
    var renderDistance = 6
    var weatherName    = "CLEAR"
    var rainIntensity  = 0f
    var thunderFlash   = 0f
    var mobCount       = 0
    var particleCount  = 0
    var drawnChunks    = 0
    var culledChunks   = 0
    var weatherName    = "CLEAR"
    var rainIntensity  = 0f

    // Inventory UI
    var inventorySlots: Array<ItemStack?> = Array(36) { null }
    var craftingOpen  = false
    var availableRecipes: List<CraftingSystem.Recipe> = emptyList()
    var craftCallback: ((CraftingSystem.Recipe) -> Unit)? = null

    // Pause menu state
    var paused        = false
    var pauseCallback:    (() -> Unit)? = null
    var settingsCallback: (() -> Unit)? = null
    var quitCallback:  (() -> Unit)? = null
    var gameModeCallback: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mono  = Typeface.MONOSPACE

    // Block colour palette for hotbar icons
    private val blockColors = mapOf(
        BlockType.GRASS        to intArrayOf(0xFF5ea82e.toInt(), 0xFF8b5e34.toInt()),
        BlockType.DIRT         to intArrayOf(0xFF8b5e34.toInt(), 0xFF7a5230.toInt()),
        BlockType.STONE        to intArrayOf(0xFF8a8a8a.toInt(), 0xFF7e7e7e.toInt()),
        BlockType.COBBLESTONE  to intArrayOf(0xFF888888.toInt(), 0xFF6e6e6e.toInt()),
        BlockType.SAND         to intArrayOf(0xFFd4b96a.toInt(), 0xFFcfb466.toInt()),
        BlockType.LOG_OAK      to intArrayOf(0xFF7a5230.toInt(), 0xFF5c3d1a.toInt()),
        BlockType.PLANKS_OAK   to intArrayOf(0xFFb8883a.toInt(), 0xFFa87c34.toInt()),
        BlockType.BRICKS       to intArrayOf(0xFFaa4433.toInt(), 0xFFc0b0a0.toInt()),
        BlockType.GLASS        to intArrayOf(0xFF9ad4f0.toInt(), 0x507ec8e8.toInt()),
        BlockType.STONE_BRICKS to intArrayOf(0xFF888888.toInt(), 0xFF777777.toInt()),
        BlockType.COAL_ORE     to intArrayOf(0xFF888888.toInt(), 0xFF1a1a1a.toInt()),
        BlockType.IRON_ORE     to intArrayOf(0xFF888888.toInt(), 0xFFd4b080.toInt()),
        BlockType.GOLD_ORE     to intArrayOf(0xFF888888.toInt(), 0xFFffd700.toInt()),
        BlockType.DIAMOND_ORE  to intArrayOf(0xFF888888.toInt(), 0xFF40e0d0.toInt()),
        BlockType.GLOWSTONE    to intArrayOf(0xFFddaa44.toInt(), 0xFFffee99.toInt()),
        BlockType.OBSIDIAN     to intArrayOf(0xFF1a0a2a.toInt(), 0xFF22103a.toInt()),
        BlockType.TNT          to intArrayOf(0xFFcc2222.toInt(), 0xFFdddddd.toInt()),
        BlockType.CRAFTING_TABLE to intArrayOf(0xFFb8883a.toInt(), 0xFF6e6e6e.toInt()),
        BlockType.TORCH        to intArrayOf(0xFFeecc44.toInt(), 0xFFb8883a.toInt()),
        BlockType.IRON_BLOCK   to intArrayOf(0xFFcccccc.toInt(), 0xFFaaaaaa.toInt()),
        BlockType.GOLD_BLOCK   to intArrayOf(0xFFeecc22.toInt(), 0xFFcc9900.toInt()),
        BlockType.DIAMOND_BLOCK to intArrayOf(0xFF22ddcc.toInt(), 0xFF1aaa99.toInt()),
        BlockType.NETHERRACK   to intArrayOf(0xFF6b2222.toInt(), 0xFF5c1e1e.toInt()),
        BlockType.DEEPSLATE    to intArrayOf(0xFF555566.toInt(), 0xFF444455.toInt()),
        BlockType.LAVA         to intArrayOf(0xFFff4400.toInt(), 0xFFff6600.toInt()),
        BlockType.WATER        to intArrayOf(0xFF2255cc.toInt(), 0xFF1a44bb.toInt()),
        BlockType.SNOW_BLOCK   to intArrayOf(0xFFe8e8f0.toInt(), 0xFFdddde8.toInt()),
        BlockType.ICE          to intArrayOf(0xFF9cd8f0.toInt(), 0xFF78bce0.toInt()),
        BlockType.WHITE_WOOL   to intArrayOf(0xFFeeeeee.toInt(), 0xFFdddddd.toInt()),
        BlockType.RED_WOOL     to intArrayOf(0xFFcc2222.toInt(), 0xFFaa1111.toInt()),
        BlockType.BLUE_WOOL    to intArrayOf(0xFF2244cc.toInt(), 0xFF1133aa.toInt()),
        BlockType.AMETHYST_BLOCK to intArrayOf(0xFF9966cc.toInt(), 0xFF7744aa.toInt()),
        BlockType.COPPER_BLOCK to intArrayOf(0xFFcc7744.toInt(), 0xFFaa5522.toInt()),
    )

    fun update(
        sel: Int, fps: Int, coords: String, blk: String, bp: Float,
        hp: Int, hg: Int, air: Int, uw: Boolean, df: Float, tt: Float,
        biome: String, mode: String, fire: Boolean, inv: Boolean,
        hotbar: IntArray, xpLv: Int, xpPts: Int, sc: Long,
        invSlots: Array<ItemStack?>, crafting: Boolean,
        recipes: List<CraftingSystem.Recipe>, rd: Int,
        weather: String = "CLEAR", rain: Float = 0f, thunder: Float = 0f, mobs: Int = 0,
        drawn: Int = 0, culled: Int = 0
    ) {
        selectedSlot=sel; this.fps=fps; coordText=coords; blockName=blk; breakProgress=bp
        health=hp; hunger=hg; this.air=air; underwater=uw; dayFactor=df; totalTime=tt
        biomeText=biome; gameModeName=mode; onFire=fire; inventoryOpen=inv
        hotbarBlocks=hotbar; xpLevel=xpLv; xpPoints=xpPts; score=sc
        inventorySlots=invSlots; craftingOpen=crafting; availableRecipes=recipes
        renderDistance=rd; weatherName=weather; rainIntensity=rain; thunderFlash=thunder; mobCount=mobs
        drawnChunks=drawn; culledChunks=culled
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val W = width.toFloat(); val H = height.toFloat()
        touch.screenW = W; touch.screenH = H

        if (paused) { drawPauseMenu(canvas, W, H); return }
        if (inventoryOpen) { drawInventory(canvas, W, H); return }
        if (craftingOpen)  { drawCraftingUI(canvas, W, H); return }

        drawCrosshair(canvas, W/2, H/2)
        drawHotbar(canvas, W, H)
        drawStatusBars(canvas, W, H)
        drawXPBar(canvas, W, H)
        drawTouchButtons(canvas, W, H)
        drawJoystickPad(canvas)
        drawDebugHud(canvas, W)
        drawBreakOverlay(canvas, W, H)
        drawVignetteEffects(canvas, W, H)
        if (onFire) drawFireOverlay(canvas, W, H)
        if (rainIntensity > 0.05f) drawRainOverlay(canvas, W, H)
        if (thunderFlash  > 0.05f) drawThunderOverlay(canvas, W, H)
    }

    // ── Crosshair ─────────────────────────────────────────────────────────
    private fun drawCrosshair(c: Canvas, cx: Float, cy: Float) {
        paint.strokeWidth=2f; paint.style=Paint.Style.STROKE
        val s=14f; val g=3f
        paint.color=0x88000000.toInt()
        c.drawLine(cx-s+1,cy+1,cx-g+1,cy+1,paint); c.drawLine(cx+g+1,cy+1,cx+s+1,cy+1,paint)
        c.drawLine(cx+1,cy-s+1,cx+1,cy-g+1,paint); c.drawLine(cx+1,cy+g+1,cx+1,cy+s+1,paint)
        paint.color=0xEEFFFFFF.toInt()
        c.drawLine(cx-s,cy,cx-g,cy,paint); c.drawLine(cx+g,cy,cx+s,cy,paint)
        c.drawLine(cx,cy-s,cx,cy-g,paint); c.drawLine(cx,cy+g,cx,cy+s,paint)
        paint.style=Paint.Style.FILL
    }

    // ── Hotbar ────────────────────────────────────────────────────────────
    private fun drawHotbar(c: Canvas, W: Float, H: Float) {
        val n=9; val slotSz=(W*0.055f).coerceIn(44f,62f); val gap=4f
        val barW=n*(slotSz+gap)-gap; val bx=(W-barW)/2f; val by=H-slotSz-16f

        paint.color=0xBB1a1a1a.toInt(); paint.style=Paint.Style.FILL
        c.drawRoundRect(bx-5f,by-5f,bx+barW+5f,by+slotSz+5f,7f,7f,paint)
        paint.color=0x55888888.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
        c.drawRoundRect(bx-5f,by-5f,bx+barW+5f,by+slotSz+5f,7f,7f,paint)
        paint.style=Paint.Style.FILL

        for (i in 0 until n) {
            val sx=bx+i*(slotSz+gap); val sel=i==selectedSlot
            paint.color=if(sel)0xDD555555.toInt() else 0xBB2a2a2a.toInt()
            c.drawRoundRect(sx,by,sx+slotSz,by+slotSz,4f,4f,paint)
            val blk=hotbarBlocks[i]
            drawBlockIcon(c, blk, sx, by, slotSz)
            if (sel) {
                paint.color=0xFFFFD700.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=2.5f
                c.drawRoundRect(sx,by,sx+slotSz,by+slotSz,4f,4f,paint); paint.style=Paint.Style.FILL
            }
            paint.typeface=mono; paint.textSize=slotSz*0.22f
            shadow(c,"${i+1}",sx+4f,by+slotSz*0.28f,0xCCFFFFFF.toInt())
            // Stack count
            val stack = inventorySlots.getOrNull(i)
            if (stack != null && stack.count > 1) {
                paint.textSize=slotSz*0.28f
                shadow(c,"${stack.count}",sx+slotSz-paint.measureText("${stack.count}")-3f,by+slotSz-4f,0xFFFFFFFF.toInt())
            }
        }
        if (blockName.isNotEmpty()) {
            paint.typeface=mono; paint.textSize=14f
            val tw=paint.measureText(blockName)
            shadow(c,blockName,(W-tw)/2,H-10f,0xCCFFFFFF.toInt())
        }
    }

    private fun drawBlockIcon(c: Canvas, blk: Int, sx: Float, by: Float, slotSz: Float) {
        if (blk == BlockType.AIR) return
        val cols = blockColors[blk]
        val pad = slotSz*0.12f; val iz = slotSz-pad*2
        if (cols != null) {
            paint.color=cols[0]; c.drawRect(sx+pad,by+pad,sx+pad+iz,by+pad+iz,paint)
            paint.color=lighten(cols[0],0.2f)
            c.drawRect(sx+pad,by+pad,sx+pad+iz,by+pad+iz*0.38f,paint)
            paint.color=darken(cols[0],0.3f)
            c.drawRect(sx+pad+iz*0.62f,by+pad+iz*0.42f,sx+pad+iz,by+pad+iz,paint)
        } else {
            // Generic colored square from block ID
            val hue = (blk * 37) % 360
            val hsv = floatArrayOf(hue.toFloat(), 0.6f, 0.7f)
            paint.color = Color.HSVToColor(hsv)
            c.drawRect(sx+pad,by+pad,sx+pad+iz,by+pad+iz,paint)
        }
    }

    // ── Status bars (health / hunger / air) ──────────────────────────────
    private fun drawStatusBars(c: Canvas, W: Float, H: Float) {
        val n=9; val slotSz=(W*0.055f).coerceIn(44f,62f); val gap=4f
        val barW=n*(slotSz+gap)-gap; val bx=(W-barW)/2f
        val baseY=H-slotSz-16f-30f
        val iconSz=13f; val spacing=16f

        val hearts=ceil(maxHealth/2.0).toInt()
        for (i in 0 until hearts) {
            val hx=bx+i*spacing
            val full=health>=(i+1)*2; val half=!full&&health>=i*2+1
            drawHeart(c,hx,baseY,iconSz,when{full->0xFFdd2222.toInt();half->0xFF992222.toInt();else->0xFF444444.toInt()},half)
        }

        val hungers=ceil(maxHunger/2.0).toInt()
        val hBase=bx+barW
        for (i in 0 until hungers) {
            val hx=hBase-(i+1)*spacing
            val full=hunger>=(i+1)*2; val half=!full&&hunger>=i*2+1
            drawChicken(c,hx,baseY,iconSz,when{full->0xFFcc8844.toInt();half->0xFF886633.toInt();else->0xFF444444.toInt()})
        }

        if (underwater) {
            val bubbles=ceil(maxAir/30.0).toInt()
            for (i in 0 until bubbles) {
                val bub=air>=(i+1)*30
                val hx=bx+i*spacing
                paint.color=if(bub)0xFF88ccff.toInt() else 0xFF334466.toInt()
                c.drawCircle(hx+iconSz/2,baseY-22f,5f,paint)
                if(bub){paint.color=0x66ffffff.toInt();c.drawCircle(hx+iconSz/2-1.5f,baseY-24f,2f,paint)}
            }
        }
    }

    // ── XP Bar ───────────────────────────────────────────────────────────
    private fun drawXPBar(c: Canvas, W: Float, H: Float) {
        val n=9; val slotSz=(W*0.055f).coerceIn(44f,62f); val gap=4f
        val barW=n*(slotSz+gap)-gap; val bx=(W-barW)/2f
        val barY=H-slotSz-16f-56f; val bh=6f

        paint.color=0x99000000.toInt(); c.drawRoundRect(bx,barY,bx+barW,barY+bh,3f,3f,paint)
        val levelThreshold=(10+xpLevel*5).toFloat()
        val xpFrac=(xpPoints/levelThreshold).coerceIn(0f,1f)
        if (xpFrac>0f) {
            paint.color=0xFF60ff20.toInt()
            c.drawRoundRect(bx,barY,bx+barW*xpFrac,barY+bh,3f,3f,paint)
        }
        if (xpLevel>0) {
            paint.typeface=mono; paint.textSize=11f; paint.textAlign=Paint.Align.CENTER
            shadow(c,"Lv $xpLevel",W/2,barY-2f,0xFF60ff20.toInt())
            paint.textAlign=Paint.Align.LEFT
        }
    }

    private fun drawHeart(c: Canvas, x: Float, y: Float, sz: Float, col: Int, isHalf: Boolean) {
        val s=sz/16f
        val px=intArrayOf(1,2,4,5,1,2,3,4,5,6,2,3,4,5,3,4,4)
        val py=intArrayOf(2,1,1,2,3,2,2,2,2,3,4,3,3,4,5,4,6)
        paint.color=0x88000000.toInt()
        for(i in px.indices)c.drawRect(x+px[i]*s+1,y+py[i]*s+1,x+px[i]*s+s+1,y+py[i]*s+s+1,paint)
        paint.color=col
        for(i in px.indices)c.drawRect(x+px[i]*s,y+py[i]*s,x+px[i]*s+s,y+py[i]*s+s,paint)
        if(isHalf){paint.color=0xFF222222.toInt();c.drawRect(x+4*s,y+s,x+8*s,y+7*s,paint)}
    }

    private fun drawChicken(c: Canvas, x: Float, y: Float, sz: Float, col: Int) {
        paint.color=col
        c.drawCircle(x+sz*0.5f,y+sz*0.3f,sz*0.32f,paint)
        paint.color=darken(col,0.3f)
        c.drawRect(x+sz*0.3f,y+sz*0.55f,x+sz*0.7f,y+sz*0.9f,paint)
    }

    // ── Touch Buttons ─────────────────────────────────────────────────────
    private fun drawTouchButtons(c: Canvas, W: Float, H: Float) {
        val btnW=W*0.11f; val btnH=H*0.16f; val mg=14f
        val row1=H-btnH-mg; val row2=H-btnH*2-mg*2
        drawBtn(c,W-btnW-mg,         row1,btnW,btnH,"JUMP",  0xCC1a3a6aL,touch.jumpHeld)
        drawBtn(c,W-btnW*2-mg*2,     row1,btnW,btnH,"BREAK", 0xCC6a1a1aL,touch.breakHeld)
        drawBtn(c,W-btnW-mg,         row2,btnW,btnH,"PLACE", 0xCC1a5a1aL,false)
        drawBtn(c,W-btnW*3-mg*3,     row1,btnW,btnH,"SNEAK", 0xCC2a2a2aL,touch.sneakHeld)
        drawBtn(c,W-btnW*4-mg*4,     row1,btnW,btnH,"SPRINT",0xCC1a4a1aL,false)
        drawBtn(c,W-btnW*5-mg*5,     row1,btnW,btnH,"INV",   0xCC3a1a5aL,false)
        drawBtn(c,W-btnW*6-mg*6,     row1,btnW,btnH,"CRAFT", 0xCC1a4a4aL,false)
    }

    private fun drawBtn(c: Canvas, x: Float, y: Float, w: Float, h: Float, lbl: String, baseCol: Long, active: Boolean) {
        val col=if(active)((baseCol.toInt() and 0x00FFFFFF) or 0xEE000000.toInt()) else baseCol.toInt()
        paint.color=col; paint.style=Paint.Style.FILL
        c.drawRoundRect(x,y,x+w,y+h,10f,10f,paint)
        paint.color=0x77CCCCCC.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.2f
        c.drawRoundRect(x,y,x+w,y+h,10f,10f,paint); paint.style=Paint.Style.FILL
        paint.typeface=mono; paint.textSize=h*0.23f; paint.textAlign=Paint.Align.CENTER
        shadow(c,lbl,x+w/2,y+h*0.62f,0xDDFFFFFF.toInt()); paint.textAlign=Paint.Align.LEFT
    }

    // ── Joystick ─────────────────────────────────────────────────────────
    private fun drawJoystickPad(c: Canvas) {
        val cx=touch.screenW*0.13f; val cy=touch.screenH*0.73f; val r=touch.screenW*0.09f
        paint.style=Paint.Style.STROKE; paint.strokeWidth=2f
        paint.color=0x33FFFFFF.toInt(); c.drawCircle(cx,cy,r,paint)
        paint.color=0x22FFFFFF.toInt(); c.drawCircle(cx,cy,r*0.5f,paint)
        val jx=cx+touch.moveX*r; val jy=cy+touch.moveZ*r
        paint.style=Paint.Style.FILL; paint.color=0x66FFFFFF.toInt()
        c.drawCircle(jx,jy,r*0.36f,paint)
        paint.style=Paint.Style.STROKE; paint.color=0x44FFFFFF.toInt(); paint.strokeWidth=1.5f
        c.drawCircle(jx,jy,r*0.36f,paint); paint.style=Paint.Style.FILL
        // WASD arrows (subtle)
        paint.color=0x22FFFFFF.toInt(); paint.textSize=r*0.35f; paint.textAlign=Paint.Align.CENTER
        c.drawText("▲",cx,cy-r*0.6f,paint); c.drawText("▼",cx,cy+r*0.7f,paint)
        c.drawText("◀",cx-r*0.65f,cy+r*0.12f,paint); c.drawText("▶",cx+r*0.65f,cy+r*0.12f,paint)
        paint.textAlign=Paint.Align.LEFT
    }

    // ── Debug HUD ─────────────────────────────────────────────────────────
    private fun drawDebugHud(c: Canvas, W: Float) {
        paint.typeface=mono; paint.textSize=18f
        shadow(c,"FPS: $fps",14f,30f,0xFFFFFFFF.toInt())
        shadow(c,coordText,14f,52f,0xFFFFFFFF.toInt())
        shadow(c,biomeText,14f,74f,0xFFaaffaa.toInt())
        shadow(c,"Mode: $gameModeName",14f,96f,0xFFaaddff.toInt())
        shadow(c,"Rd: ${renderDistance}ch",14f,118f,0xFFdddddd.toInt())
        shadow(c,"Score: $score",14f,140f,0xFFffdd44.toInt())
        // Weather icon
        val wEmoji = when(weatherName) {
            "RAIN"    -> "🌧 Rain"
            "THUNDER" -> "⚡ Storm"
            "OVERCAST"-> "☁ Overcast"
            else      -> "☀ Clear"
        }
        shadow(c,wEmoji,14f,162f,0xFFDDEEFF.toInt())
        shadow(c,"Mobs: $mobCount",14f,184f,0xFFFFBB88.toInt())
        shadow(c,"Chunks: $drawnChunks drawn/$culledChunks culled",14f,206f,0xFFBBFFBB.toInt())

        // Day/night indicator
        val isDay = dayFactor > 0.5f
        paint.color=if(isDay)0xFFffee44.toInt() else 0xFFddddff.toInt()
        c.drawCircle(W-32f,28f,10f,paint)
        if(!isDay){ paint.color=0xFF1a1a3a.toInt(); c.drawCircle(W-29f,25f,9f,paint) }
        paint.typeface=mono; paint.textSize=12f
        shadow(c,if(isDay)"Day" else "Night",W-60f,44f,0xCCFFFFFF.toInt())
    }

    // ── Break Progress ────────────────────────────────────────────────────
    private fun drawBreakOverlay(c: Canvas, W: Float, H: Float) {
        if (breakProgress<=0f) return
        val bw=W*0.26f; val bh=18f; val bx=(W-bw)/2f; val by=H/2f+56f
        paint.color=0x99000000.toInt(); c.drawRoundRect(bx,by,bx+bw,by+bh,6f,6f,paint)
        val prog=breakProgress.coerceIn(0f,1f)
        val crackColor=when{prog<0.33f->0xFFffaa00.toInt();prog<0.66f->0xFFff6600.toInt();else->0xFFff2200.toInt()}
        paint.color=crackColor; c.drawRoundRect(bx,by,bx+bw*prog,by+bh,6f,6f,paint)
        // Crack stage text
        val stage=(prog*7).toInt()+1
        paint.color=0x44FFFFFF.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1f
        c.drawRoundRect(bx,by,bx+bw,by+bh,6f,6f,paint); paint.style=Paint.Style.FILL
        paint.textSize=11f; paint.textAlign=Paint.Align.CENTER
        shadow(c,"Breaking... $stage/7",W/2,by+bh-4f,0xCCFFFFFF.toInt())
        paint.textAlign=Paint.Align.LEFT
    }

    // ── Vignette / Environmental effects ─────────────────────────────────
    private fun drawVignetteEffects(c: Canvas, W: Float, H: Float) {
        if (underwater) {
            paint.color=0x4400aaff.toInt(); c.drawRect(0f,0f,W,H,paint)
            val rad=RadialGradient(W/2,H/2,W*0.6f,0x0000aaff,0x8800aaff,Shader.TileMode.CLAMP)
            paint.shader=rad; c.drawRect(0f,0f,W,H,paint); paint.shader=null
        }
        if (dayFactor<0.8f) {
            val alpha=((1f-dayFactor)*0.55f*255).toInt().coerceIn(0,180)
            paint.color=(alpha shl 24); c.drawRect(0f,0f,W,H,paint)
        }
        val vign=RadialGradient(W/2,H/2,maxOf(W,H)*0.72f,0x00000000,0x66000000,Shader.TileMode.CLAMP)
        paint.shader=vign; c.drawRect(0f,0f,W,H,paint); paint.shader=null
    }

    private fun drawFireOverlay(c: Canvas, W: Float, H: Float) {
        paint.color=0x55ff4400.toInt(); c.drawRect(0f,0f,W,H,paint)
        // Simple flame border
        paint.color=0x88ff8800.toInt()
        c.drawRect(0f,0f,W,H*0.08f,paint); c.drawRect(0f,H*0.92f,W,H,paint)
        c.drawRect(0f,0f,W*0.04f,H,paint); c.drawRect(W*0.96f,0f,W,H,paint)
    }

    // ── Inventory UI ──────────────────────────────────────────────────────
    private fun drawInventory(c: Canvas, W: Float, H: Float) {
        // Dimmed background
        paint.color=0xCC000000.toInt(); c.drawRect(0f,0f,W,H,paint)
        val slotSz=48f; val gap=4f; val cols=9; val rows=4
        val gridW=cols*(slotSz+gap)-gap; val gridH=rows*(slotSz+gap)-gap
        val ox=(W-gridW)/2f; val oy=(H-gridH)/2f-30f

        // Title
        paint.typeface=mono; paint.textSize=22f; paint.textAlign=Paint.Align.CENTER
        shadow(c,"INVENTORY",W/2,oy-16f,0xFFFFFFFF.toInt())
        paint.textAlign=Paint.Align.LEFT

        // Draw slots
        for (row in 0 until rows) for (col in 0 until cols) {
            val idx=row*cols+col
            val sx=ox+col*(slotSz+gap); val sy=oy+row*(slotSz+gap)
            val isHotbar=idx<9; val isSel=idx==player_selectedSlot
            paint.color=when{ isSel->0xDD666666.toInt(); isHotbar->0xBB444444.toInt(); else->0xBB2a2a2a.toInt() }
            c.drawRoundRect(sx,sy,sx+slotSz,sy+slotSz,4f,4f,paint)
            paint.color=0x55888888.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1f
            c.drawRoundRect(sx,sy,sx+slotSz,sy+slotSz,4f,4f,paint); paint.style=Paint.Style.FILL
            val stack=inventorySlots.getOrNull(idx)
            if (stack!=null && !stack.isEmpty) {
                drawBlockIcon(c,stack.blockId,sx,sy,slotSz)
                if (stack.count>1) {
                    paint.textSize=12f; paint.typeface=mono
                    shadow(c,"${stack.count}",sx+slotSz-18f,sy+slotSz-4f,0xFFFFFFFF.toInt())
                }
            }
        }

        // Hotbar separator label
        paint.textSize=12f; paint.textAlign=Paint.Align.CENTER
        shadow(c,"— Hotbar —",W/2,oy+(slotSz+gap)*rows+14f,0xFFaaaaaa.toInt())
        paint.textAlign=Paint.Align.LEFT

        // Close hint
        shadow(c,"Tap INV to close",W/2,H-20f,0xFFcccccc.toInt())
    }

    private var player_selectedSlot = 0
    fun setPlayerSelectedSlot(s: Int) { player_selectedSlot=s }

    // ── Crafting UI ───────────────────────────────────────────────────────
    private fun drawCraftingUI(c: Canvas, W: Float, H: Float) {
        paint.color=0xCC000000.toInt(); c.drawRect(0f,0f,W,H,paint)
        paint.typeface=mono; paint.textSize=22f; paint.textAlign=Paint.Align.CENTER
        shadow(c,"CRAFTING",W/2,50f,0xFFFFFFFF.toInt())
        paint.textAlign=Paint.Align.LEFT

        val btnW=W*0.5f; val btnH=48f; val bx=(W-btnW)/2f
        if (availableRecipes.isEmpty()) {
            paint.textSize=15f; paint.textAlign=Paint.Align.CENTER
            shadow(c,"No recipes available",W/2,100f,0xFFaaaaaa.toInt())
            shadow(c,"Collect more materials!",W/2,122f,0xFF888888.toInt())
            paint.textAlign=Paint.Align.LEFT
        } else {
            var ry=80f
            for (recipe in availableRecipes.take(8)) {
                paint.color=0xBB1a4a1a.toInt(); paint.style=Paint.Style.FILL
                c.drawRoundRect(bx,ry,bx+btnW,ry+btnH,8f,8f,paint)
                paint.color=0x55FFFFFF.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1f
                c.drawRoundRect(bx,ry,bx+btnW,ry+btnH,8f,8f,paint); paint.style=Paint.Style.FILL
                val name=if(recipe.result<BlockType.NAMES.size) BlockType.NAMES[recipe.result] else "Unknown"
                paint.textSize=15f; paint.textAlign=Paint.Align.CENTER
                shadow(c,"Craft: $name ×${recipe.count}",W/2,ry+btnH*0.62f,0xFFFFFFFF.toInt())
                paint.textAlign=Paint.Align.LEFT
                ry+=btnH+6f
            }
        }
        paint.textSize=13f; paint.textAlign=Paint.Align.CENTER
        shadow(c,"Tap CRAFT to close",W/2,H-20f,0xFFcccccc.toInt())
        paint.textAlign=Paint.Align.LEFT
    }

    // ── Pause Menu ────────────────────────────────────────────────────────
    private fun drawPauseMenu(c: Canvas, W: Float, H: Float) {
        paint.color=0xDD000000.toInt(); c.drawRect(0f,0f,W,H,paint)
        paint.typeface=mono; paint.textSize=32f; paint.textAlign=Paint.Align.CENTER
        shadow(c,"PAUSED",W/2,H*0.25f,0xFFFFFFFF.toInt())
        val btnW=W*0.45f; val btnH=52f; val bx=(W-btnW)/2f
        drawMenuBtn(c,"▶  RESUME",bx,H*0.40f,btnW,btnH,0xCC1a5a1aL)
        drawMenuBtn(c,"🎮  GAME MODE",bx,H*0.52f,btnW,btnH,0xCC1a3a6aL)
        drawMenuBtn(c,"⚙  SETTINGS",bx,H*0.64f,btnW,btnH,0xCC3a3a1aL) // TODO: wire tap
        drawMenuBtn(c,"✕  QUIT",bx,H*0.76f,btnW,btnH,0xCC6a1a1aL)
        paint.textAlign=Paint.Align.LEFT
    }

    private fun drawMenuBtn(c: Canvas, label: String, x: Float, y: Float, w: Float, h: Float, col: Long) {
        paint.color=col.toInt(); paint.style=Paint.Style.FILL
        c.drawRoundRect(x,y,x+w,y+h,12f,12f,paint)
        paint.color=0x55FFFFFF.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
        c.drawRoundRect(x,y,x+w,y+h,12f,12f,paint); paint.style=Paint.Style.FILL
        paint.typeface=mono; paint.textSize=h*0.42f; paint.textAlign=Paint.Align.CENTER
        shadow(c,label,x+w/2,y+h*0.65f,0xFFFFFFFF.toInt())
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun shadow(c: Canvas, text: String, x: Float, y: Float, col: Int) {
        paint.style=Paint.Style.FILL
        paint.color=0xBB000000.toInt(); c.drawText(text,x+1.5f,y+1.5f,paint)
        paint.color=col; c.drawText(text,x,y,paint)
    }
    private fun lighten(col: Int, amt: Float): Int {
        val r=((Color.red(col)*(1+amt)).toInt()).coerceIn(0,255)
        val g=((Color.green(col)*(1+amt)).toInt()).coerceIn(0,255)
        val b=((Color.blue(col)*(1+amt)).toInt()).coerceIn(0,255)
        return Color.argb(Color.alpha(col),r,g,b)
    }
    private fun drawRainOverlay(c: Canvas, W: Float, H: Float) {
        val alpha = (rainIntensity * 60).toInt().coerceIn(0, 60)
        paint.color = (alpha shl 24) or 0x223344
        c.drawRect(0f, 0f, W, H, paint)
        // Rain streak lines
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        val t = (System.currentTimeMillis() % 500L).toFloat() / 500f
        val count = (rainIntensity * 80).toInt()
        for (i in 0 until count) {
            val x = ((i * 137.3f + t * W) % W)
            val y = ((i * 97.7f  + t * H * 2.0f) % H)
            paint.color = (((rainIntensity * 80).toInt()) shl 24) or 0xAABBCC
            c.drawLine(x, y, x - rainIntensity * 4f, y + 18f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawThunderOverlay(c: Canvas, W: Float, H: Float) {
        val alpha = (thunderFlash * 180).toInt().coerceIn(0, 180)
        paint.color = (alpha shl 24) or 0xCCDDFF
        c.drawRect(0f, 0f, W, H, paint)
    }

        private fun darken(col: Int, amt: Float): Int {
        val r=((Color.red(col)*(1-amt)).toInt()).coerceIn(0,255)
        val g=((Color.green(col)*(1-amt)).toInt()).coerceIn(0,255)
        val b=((Color.blue(col)*(1-amt)).toInt()).coerceIn(0,255)
        return Color.argb(Color.alpha(col),r,g,b)
    }
}

// ── Extension: add weather display + rain vignette ──────────────
// These are added to HudView as extension for v4 — patch the update
// signature in a companion extension file
