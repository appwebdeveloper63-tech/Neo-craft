package com.neocraft.game.engine

import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLUtils
import com.neocraft.game.world.*
import kotlin.math.*

/**
 * Procedural texture atlas — 16×16 columns/rows, each tile 16×16 px → 256×256 total.
 * Row/col layout matches BlockType.getAtlasTile().
 */
object TextureAtlas {

    var textureId = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun init() {
        val W = TILE_PX * ATLAS_COLS   // 256
        val H = TILE_PX * ATLAS_ROWS   // 256 (16 rows now)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawAll(c)

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
    }

    // ── Noise helpers ─────────────────────────────────────────────────────
    private fun rng(x: Int, y: Int, s: Int = 0): Float {
        val n = sin((x * 127.1 + y * 311.7 + s * 74.3) * 43758.5453)
        return (n - floor(n)).toFloat()
    }

    // ── Tile helpers ──────────────────────────────────────────────────────
    private fun at(col: Int, row: Int) = col * TILE_PX to row * TILE_PX
    private fun fill(c: Canvas, ox: Int, oy: Int, col: Int) {
        p.style = Paint.Style.FILL; p.color = col
        c.drawRect(ox.f, oy.f, (ox + TILE_PX).f, (oy + TILE_PX).f, p)
    }
    private fun rect(c: Canvas, ox: Int, oy: Int, x: Int, y: Int, w: Int, h: Int, col: Int) {
        p.color = col; p.style = Paint.Style.FILL
        c.drawRect((ox + x).f, (oy + y).f, (ox + x + w).f, (oy + y + h).f, p)
    }
    private fun noisy(c: Canvas, ox: Int, oy: Int, cols: IntArray, seed: Int = 0) {
        for (py in 0 until TILE_PX) for (px in 0 until TILE_PX) {
            p.color = cols[(rng(px, py, seed) * cols.size).toInt().coerceIn(0, cols.size - 1)]
            c.drawPoint((ox + px).f, (oy + py).f, p)
        }
    }
    private fun oreOverlay(c: Canvas, ox: Int, oy: Int, oreCol: Int, seed: Int) {
        noisy(c, ox, oy, intArrayOf(0xFF8a8a8a.toInt(), 0xFF7e7e7e.toInt(), 0xFF929292.toInt()), seed)
        p.color = oreCol; p.style = Paint.Style.FILL
        for ((x, y, w, h) in listOf(
            intArrayOf(3, 3, 3, 3), intArrayOf(9, 2, 3, 3), intArrayOf(2, 9, 3, 3), intArrayOf(10, 10, 3, 3)
        )) c.drawRect((ox + x).f, (oy + y).f, (ox + x + w).f, (oy + y + h).f, p)
    }
    private fun deepslateOre(c: Canvas, ox: Int, oy: Int, oreCol: Int, seed: Int) {
        noisy(c, ox, oy, intArrayOf(0xFF555566.toInt(), 0xFF444455.toInt(), 0xFF666677.toInt()), seed)
        p.color = oreCol; p.style = Paint.Style.FILL
        for ((x, y, w, h) in listOf(
            intArrayOf(2, 4, 3, 3), intArrayOf(10, 3, 3, 3), intArrayOf(3, 10, 3, 3), intArrayOf(9, 9, 4, 4)
        )) c.drawRect((ox + x).f, (oy + y).f, (ox + x + w).f, (oy + y + h).f, p)
    }

    private fun drawAll(c: Canvas) {
        // ── ROW 0 ─────────────────────────────────────────────────────────
        at(0,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF5ea82e.toInt(),0xFF4d9224.toInt(),0xFF69b234.toInt(),0xFF58a028.toInt()))}
        at(1,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF8a8a8a.toInt(),0xFF7e7e7e.toInt(),0xFF929292.toInt()))
            p.color=0xFF555555.toInt();p.style=Paint.Style.FILL
            for((x,y,w,h) in listOf(intArrayOf(3,5,1,3),intArrayOf(10,9,1,4),intArrayOf(7,1,1,2)))c.drawRect((ox+x).f,(oy+y).f,(ox+x+w).f,(oy+y+h).f,p)}
        at(2,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF8b5e34.toInt(),0xFF7a5230.toInt(),0xFF956437.toInt()))}
        at(3,0).let{(ox,oy)->noisy(c,ox,oy+3,intArrayOf(0xFF8b5e34.toInt(),0xFF7a5230.toInt()))
            for(py in 0..2) for(px in 0 until TILE_PX){p.color=if(rng(px,py)>0.5f)0xFF5ea82e.toInt() else 0xFF4d9224.toInt();c.drawPoint((ox+px).f,(oy+py).f,p)}}
        at(4,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFd4b96a.toInt(),0xFFc8ad5e.toInt(),0xFFdcc072.toInt()))}
        at(5,0).let{(ox,oy)->fill(c,ox,oy,0xFFc4a45c.toInt())
            val sp=Paint().apply{style=Paint.Style.STROKE;strokeWidth=1f}
            for(r in 1..6){sp.color=if(r%2==0)0xFFa08840.toInt() else 0xFFb49038.toInt();c.drawCircle((ox+8).f,(oy+8).f,r.f,sp)}}
        at(6,0).let{(ox,oy)->for(py in 0 until TILE_PX) for(px in 0 until TILE_PX){p.color=if(rng(px,py,3)>0.65f)0xFF5c3d1a.toInt() else 0xFF7a5230.toInt();c.drawPoint((ox+px).f,(oy+py).f,p)}
            p.color=0xFF6b4a28.toInt();p.style=Paint.Style.FILL
            for(px in intArrayOf(2,8,13))c.drawRect((ox+px).f,oy.f,(ox+px+1).f,(oy+TILE_PX).f,p)}
        at(7,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF2d7a1e.toInt(),0xFF237015.toInt(),0xFF357f23.toInt()),5)}
        at(8,0).let{(ox,oy)->fill(c,ox,oy,0xFFd0c88a.toInt())}
        at(9,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFd8d0a0.toInt(),0xFFe0d8a8.toInt(),0xFFccc880.toInt()))
            for(py in 0 until TILE_PX step 3){p.color=0xFF888060.toInt();p.style=Paint.Style.FILL;c.drawRect(ox.f,(oy+py).f,(ox+TILE_PX).f,(oy+py+1).f,p)}}
        at(10,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF4caa2a.toInt(),0xFF3d9e1f.toInt(),0xFF5ab230.toInt()),7)}
        at(11,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF4a3220.toInt(),0xFF3e2a18.toInt(),0xFF563a26.toInt()))}
        at(12,0).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF1c5c1a.toInt(),0xFF145216.toInt(),0xFF24641e.toInt()),9)}
        at(13,0).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt())}
        at(14,0).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt())}
        at(15,0).let{(ox,oy)->noisy(c,ox,oy+2,intArrayOf(0xFF8b5e34.toInt(),0xFF7a5230.toInt()))
            for(py in 0..1) for(px in 0 until TILE_PX){p.color=if(rng(px,py,2)>0.5f)0xFFe8e8f0.toInt() else 0xFFdddde8.toInt();c.drawPoint((ox+px).f,(oy+py).f,p)}}

        // ── ROW 1 ─────────────────────────────────────────────────────────
        at(0,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFe8e8f0.toInt(),0xFFdddde8.toInt(),0xFFf0f0f8.toInt()),2)}
        at(1,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF8a8a8a.toInt(),0xFF7c7c7c.toInt(),0xFF979797.toInt()),4)
            p.color=0xFF999999.toInt();p.style=Paint.Style.FILL
            for((x,y,w,h) in listOf(intArrayOf(2,2,4,3),intArrayOf(9,6,5,4)))c.drawRect((ox+x).f,(oy+y).f,(ox+x+w).f,(oy+y+h).f,p)}
        at(2,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF2255cc.toInt(),0xFF1a44bb.toInt(),0xFF2a66dd.toInt()),6)
            p.color=0x407ec8ff.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,(oy+4).f,(ox+TILE_PX).f,(oy+7).f,p)}
        at(3,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF333333.toInt(),0xFF2a2a2a.toInt(),0xFF3d3d3d.toInt()),8)}
        at(4,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFb8883a.toInt(),0xFFa87c34.toInt(),0xFFc0923e.toInt()))
            p.color=0xFF8a6020.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,oy.f,(ox+TILE_PX).f,(oy+1).f,p);c.drawRect(ox.f,(oy+8).f,(ox+TILE_PX).f,(oy+9).f,p)
            c.drawRect((ox+8).f,oy.f,(ox+9).f,(oy+8).f,p)}
        at(5,1).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt())
            val sc=intArrayOf(0xFF7a7a7a.toInt(),0xFF929292.toInt())
            for((i,s) in listOf(intArrayOf(0,0,7,6),intArrayOf(8,0,7,4),intArrayOf(0,7,4,7)).withIndex())
                rect(c,ox,oy,s[0]+1,s[1]+1,s[2]-1,s[3]-1,sc[i%sc.size])
            p.color=0xFF555555.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+7).f,oy.f,(ox+8).f,(oy+7).f,p);c.drawRect(ox.f,(oy+6).f,(ox+TILE_PX).f,(oy+7).f,p)}
        at(6,1).let{(ox,oy)->fill(c,ox,oy,0x309ad4f0.toInt())
            val bp=Paint().apply{style=Paint.Style.STROKE;strokeWidth=1.2f;color=0xFF9ad4f0.toInt()}
            c.drawRect(ox.f+0.5f,oy.f+0.5f,(ox+TILE_PX).f-0.5f,(oy+TILE_PX).f-0.5f,bp)
            p.color=0x50FFFFFF.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+2).f,(oy+2).f,(ox+6).f,(oy+5).f,p)}
        at(7,1).let{(ox,oy)->fill(c,ox,oy,0xFF777777.toInt())}
        at(8,1).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt())
            val sc=intArrayOf(0xFF7a7a7a.toInt(),0xFF929292.toInt())
            for((i,s) in listOf(intArrayOf(0,0,7,6),intArrayOf(8,0,7,4)).withIndex())
                rect(c,ox,oy,s[0]+1,s[1]+1,s[2]-1,s[3]-1,sc[i%sc.size])
            p.color=0x9927aa22.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX) for(px in 0 until TILE_PX) if(rng(px,py,12)>0.78f){p.alpha=120;c.drawPoint((ox+px).f,(oy+py).f,p)};p.alpha=255}
        at(9,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF1a0a2a.toInt(),0xFF22103a.toInt(),0xFF150820.toInt()),11)
            p.color=0x404488ff.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 4) for(px in 0 until TILE_PX step 4) if(rng(px,py,13)>0.8f) c.drawRect((ox+px).f,(oy+py).f,(ox+px+2).f,(oy+py+2).f,p)}
        at(10,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFff4400.toInt(),0xFFe83000.toInt(),0xFFff6600.toInt()))
            p.color=0xFFffcc00.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 3) for(px in 0 until TILE_PX step 3) if(rng(px,py,14)>0.85f) c.drawRect((ox+px).f,(oy+py).f,(ox+px+2).f,(oy+py+2).f,p)}
        at(11,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF9aA0b4.toInt(),0xFF8a9098.toInt(),0xFFaaaabc.toInt()),15)}
        at(12,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF6b2222.toInt(),0xFF7a2a2a.toInt(),0xFF5c1e1e.toInt()),16)
            p.color=0xFF3a1010.toInt();p.style=Paint.Style.FILL
            for((x,y,w,h) in listOf(intArrayOf(3,5,2,3),intArrayOf(10,2,1,4)))c.drawRect((ox+x).f,(oy+y).f,(ox+x+w).f,(oy+y+h).f,p)}
        at(13,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFddaa44.toInt(),0xFFcc9933.toInt(),0xFFeecc55.toInt()),17)
            p.color=0xFFffee99.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 2) for(px in 0 until TILE_PX step 2) if(rng(px,py,18)>0.75f) c.drawRect((ox+px).f,(oy+py).f,(ox+px+2).f,(oy+py+2).f,p)}
        at(14,1).let{(ox,oy)->fill(c,ox,oy,0xBB9cd8f0.toInt())
            val ip=Paint().apply{style=Paint.Style.STROKE;strokeWidth=1f;color=0xFF78bce0.toInt()}
            c.drawLine((ox+3).f,(oy+3).f,(ox+13).f,(oy+5).f,ip)
            c.drawLine((ox+1).f,(oy+8).f,(ox+10).f,(oy+14).f,ip)}
        at(15,1).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF7ab8e0.toInt(),0xFF6aaad0.toInt(),0xFF8ac8f0.toInt()),19)
            p.color=0xFF5090c0.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=0.5f
            for(py in 0 until TILE_PX step 4) c.drawLine(ox.f,(oy+py).f,(ox+TILE_PX).f,(oy+py).f,p);p.style=Paint.Style.FILL}

        // ── ROW 2 ─────────────────────────────────────────────────────────
        at(0,2).let{(ox,oy)->fill(c,ox,oy,0xFFaa4433.toInt())
            p.color=0xFFc0b0a0.toInt();p.style=Paint.Style.FILL
            for((x,y,w,h) in listOf(intArrayOf(0,4,16,2),intArrayOf(0,12,16,2),intArrayOf(0,0,2,4),intArrayOf(8,0,2,4),intArrayOf(0,8,2,4),intArrayOf(10,8,2,4)))
                c.drawRect((ox+x).f,(oy+y).f,(ox+x+w).f,(oy+y+h).f,p)}
        at(1,2).let{(ox,oy)->oreOverlay(c,ox,oy,0xFF1a1a1a.toInt(),20)}
        at(2,2).let{(ox,oy)->oreOverlay(c,ox,oy,0xFFd4b080.toInt(),21)}
        at(3,2).let{(ox,oy)->oreOverlay(c,ox,oy,0xFFffd700.toInt(),22)}
        at(4,2).let{(ox,oy)->oreOverlay(c,ox,oy,0xFF40e0d0.toInt(),23)}
        at(5,2).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFddc86a.toInt(),0xFFd4c060.toInt(),0xFFe4d070.toInt()),24)}
        at(6,2).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFd4b86a.toInt(),0xFFc8ac58.toInt()),25)}
        at(7,2).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFd4b96a.toInt(),0xFFcaaf60.toInt()),26)
            p.color=0xFFb89848.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,(oy+5).f,(ox+TILE_PX).f,(oy+6).f,p)
            c.drawRect(ox.f,(oy+11).f,(ox+TILE_PX).f,(oy+12).f,p)}
        at(8,2).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFcc6622.toInt(),0xFFbb5a18.toInt(),0xFFd47030.toInt()),27)}
        at(9,2).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF887788.toInt(),0xFF7a6a7a.toInt(),0xFF988898.toInt()),28)}
        at(10,2).let{(ox,oy)->noisy(c,ox,oy+3,intArrayOf(0xFF8b5e34.toInt(),0xFF7a5230.toInt()))
            for(py in 0..2) for(px in 0 until TILE_PX){p.color=if(rng(px,py,30)>0.5f)0xFF887788.toInt() else 0xFF7a6a7a.toInt();c.drawPoint((ox+px).f,(oy+py).f,p)}}
        at(11,2).let{(ox,oy)->oreOverlay(c,ox,oy,0xFF22cc44.toInt(),31)}
        at(12,2).let{(ox,oy)->oreOverlay(c,ox,oy,0xFFcc2222.toInt(),32)}
        at(13,2).let{(ox,oy)->oreOverlay(c,ox,oy,0xFF2244cc.toInt(),33)}
        at(14,2).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFdd9922.toInt(),0xFFcc8818.toInt(),0xFFee9a28.toInt()),34)}
        at(15,2).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFdd9922.toInt(),0xFFcc8818.toInt()),35)
            p.color=0xFF442200.toInt()
            c.drawRect((ox+2).f,(oy+6).f,(ox+6).f,(oy+9).f,p);c.drawRect((ox+10).f,(oy+6).f,(ox+14).f,(oy+9).f,p)}

        // ── ROW 3 ─────────────────────────────────────────────────────────
        at(0,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFb06050.toInt(),0xFFa05848.toInt(),0xFFbc6858.toInt()),36)}
        at(1,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFd0d0d0.toInt(),0xFFc0c0c0.toInt(),0xFFdcdcdc.toInt()),37)}
        at(2,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF888888.toInt(),0xFF7a7a7a.toInt(),0xFF929292.toInt()),38)}
        at(3,3).let{(ox,oy)->fill(c,ox,oy,0xFFcccccc.toInt())
            p.color=0xFFaaaaaa.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,oy.f,(ox+TILE_PX).f,(oy+1).f,p);c.drawRect(ox.f,(oy+15).f,(ox+TILE_PX).f,(oy+TILE_PX).f,p)}
        at(4,3).let{(ox,oy)->fill(c,ox,oy,0xFFeecc22.toInt())
            p.color=0xFFcc9900.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,oy.f,(ox+TILE_PX).f,(oy+2).f,p);c.drawRect(ox.f,(oy+14).f,(ox+TILE_PX).f,(oy+TILE_PX).f,p)}
        at(5,3).let{(ox,oy)->fill(c,ox,oy,0xFF22ddcc.toInt())
            p.color=0xFF1aaa99.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,oy.f,(ox+TILE_PX).f,(oy+2).f,p);c.drawRect(ox.f,(oy+14).f,(ox+TILE_PX).f,(oy+TILE_PX).f,p)}
        at(6,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF226622.toInt(),0xFF2a6e2a.toInt(),0xFF1a5018.toInt()),40)}
        at(7,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF2e7a2a.toInt(),0xFF246620.toInt(),0xFF369038.toInt()),41)
            p.color=0xFF1a5018.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 4) c.drawRect(ox.f,(oy+py).f,(ox+TILE_PX).f,(oy+py+1).f,p)}
        // Stone bricks (8,3)
        at(8,3).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt())
            p.color=0xFF555555.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,(oy+7).f,(ox+TILE_PX).f,(oy+8).f,p)
            c.drawRect((ox+7).f,oy.f,(ox+8).f,(oy+7).f,p);c.drawRect((ox+3).f,(oy+8).f,(ox+4).f,(oy+TILE_PX).f,p)
            p.color=0xFF9a9a9a.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+1).f,(oy+1).f,(ox+6).f,(oy+6).f,p);c.drawRect((ox+9).f,(oy+1).f,(ox+15).f,(oy+6).f,p)}
        // Mossy stone bricks (9,3)
        at(9,3).let{(ox,oy)->fill(c,ox,oy,0xFF778866.toInt())
            p.color=0xFF446633.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 2) for(px in 0 until TILE_PX step 3) if(rng(px,py,50)>0.7f)c.drawRect((ox+px).f,(oy+py).f,(ox+px+2).f,(oy+py+2).f,p)}
        // Cracked stone bricks (10,3)
        at(10,3).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt())
            p.color=0xFF333333.toInt();p.style=Paint.Style.FILL
            for(i in 0..4){val x=(rng(i,0,51)*14).toInt();val y=(rng(i,1,51)*14).toInt();c.drawRect((ox+x).f,(oy+y).f,(ox+x+2).f,(oy+y+1).f,p)}}
        // Chiseled stone bricks (11,3)
        at(11,3).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt())
            val sp=Paint().apply{style=Paint.Style.STROKE;strokeWidth=1f;color=0xFF555555.toInt()}
            c.drawCircle((ox+8).f,(oy+8).f,6f,sp);c.drawCircle((ox+8).f,(oy+8).f,3f,sp)}
        // Smooth stone (12,3)
        at(12,3).let{(ox,oy)->fill(c,ox,oy,0xFF9a9a9a.toInt())
            p.color=0xFF666666.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,oy.f,(ox+TILE_PX).f,(oy+1).f,p);c.drawRect(ox.f,(oy+15).f,(ox+TILE_PX).f,(oy+TILE_PX).f,p)}
        // Deepslate (13,3)
        at(13,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF555566.toInt(),0xFF444455.toInt(),0xFF666677.toInt()),52)}
        // Tuff (14,3)
        at(14,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF787868.toInt(),0xFF706858.toInt(),0xFF807870.toInt()),53)}
        // Blackstone (15,3)
        at(15,3).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF1a1a22.toInt(),0xFF151520.toInt(),0xFF22222a.toInt()),54)}

        // ── ROW 4: New tree types ──────────────────────────────────────────
        // (0,4) jungle log top / (1,4) jungle log side
        at(0,4).let{(ox,oy)->fill(c,ox,oy,0xFF8a6a3a.toInt())}
        at(1,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF6a4a1a.toInt(),0xFF7a5a2a.toInt()),60)
            p.color=0xFF4a3010.toInt();p.style=Paint.Style.FILL
            for(px in intArrayOf(3,10))c.drawRect((ox+px).f,oy.f,(ox+px+2).f,(oy+TILE_PX).f,p)}
        // (2,4) jungle leaves
        at(2,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF2a8a1a.toInt(),0xFF1a7a0a.toInt(),0xFF3a9a2a.toInt()),61)}
        // (3,4) acacia log top / (4,4) side
        at(3,4).let{(ox,oy)->fill(c,ox,oy,0xFF9a8040.toInt())}
        at(4,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFaa7030.toInt(),0xFF8a5a20.toInt()),62)}
        // (5,4) acacia leaves
        at(5,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF8ab820.toInt(),0xFF7aa010.toInt(),0xFF9ac830.toInt()),63)}
        // (6,4) dark oak log top / (7,4) side
        at(6,4).let{(ox,oy)->fill(c,ox,oy,0xFF3a2a10.toInt())}
        at(7,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF3a2010.toInt(),0xFF4a3020.toInt()),64)}
        // (8,4) dark oak leaves
        at(8,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF1a4a0a.toInt(),0xFF0a3a00.toInt(),0xFF2a5a18.toInt()),65)}
        // (9,4) crimson stem top / (10,4) side
        at(9,4).let{(ox,oy)->fill(c,ox,oy,0xFF8a2a3a.toInt())}
        at(10,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF7a1a2a.toInt(),0xFF9a3a4a.toInt()),66)}
        // (11,4) crimson leaves
        at(11,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF8a1a2a.toInt(),0xFF6a0a1a.toInt()),67)}
        // (12,4) warped stem top / (13,4) side
        at(12,4).let{(ox,oy)->fill(c,ox,oy,0xFF1a8a7a.toInt())}
        at(13,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF0a7a6a.toInt(),0xFF2a9a8a.toInt()),68)}
        // (14,4) warped leaves
        at(14,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF1a8a7a.toInt(),0xFF0a6a5a.toInt()),69)}
        // (15,4) basalt side / jungle planks
        at(15,4).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF303038.toInt(),0xFF282830.toInt()),70)}

        // ── ROW 5: Functional blocks ───────────────────────────────────────
        at(0,5).let{(ox,oy)->fill(c,ox,oy,0xFF3a2a18.toInt())} // basalt side alt
        at(1,5).let{(ox,oy)->fill(c,ox,oy,0xFFdddddd.toInt()) // TNT top (grey)
            p.color=0xFFcc2222.toInt();p.style=Paint.Style.FILL;c.drawRect((ox+3).f,(oy+3).f,(ox+13).f,(oy+13).f,p)}
        at(2,5).let{(ox,oy)->fill(c,ox,oy,0xFFcc2222.toInt()) // TNT side
            p.color=0xFFeeeeee.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+2).f,(oy+5).f,(ox+14).f,(oy+11).f,p)
            paint.typeface=Typeface.MONOSPACE;p.color=0xFFcc2222.toInt();p.textSize=6f
            c.drawText("TNT",(ox+3).f,(oy+9).f,p)}
        at(3,5).let{(ox,oy)->fill(c,ox,oy,0xFFaaaaaa.toInt())} // TNT bottom
        at(4,5).let{(ox,oy)-> // Crafting table top
            fill(c,ox,oy,0xFFb8883a.toInt())
            p.color=0xFF7a5020.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+6).f,(oy+1).f,(ox+10).f,(oy+15).f,p);c.drawRect((ox+1).f,(oy+6).f,(ox+15).f,(oy+10).f,p)}
        at(5,5).let{(ox,oy)-> // Crafting table side
            fill(c,ox,oy,0xFFb8883a.toInt())
            p.color=0xFF8a6030.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,(oy+3).f,(ox+TILE_PX).f,(oy+4).f,p)
            c.drawRect(ox.f,(oy+11).f,(ox+TILE_PX).f,(oy+12).f,p)}
        at(7,5).let{(ox,oy)->fill(c,ox,oy,0xFF666666.toInt())} // Furnace top
        at(8,5).let{(ox,oy)->fill(c,ox,oy,0xFF888888.toInt()) // Furnace front
            p.color=0xFF333333.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+2).f,(oy+3).f,(ox+14).f,(oy+12).f,p)
            p.color=0xFFff6600.toInt();c.drawRect((ox+5).f,(oy+6).f,(ox+11).f,(oy+10).f,p)}
        at(10,5).let{(ox,oy)->fill(c,ox,oy,0xFF8a6a3a.toInt())} // Chest top
        at(11,5).let{(ox,oy)->fill(c,ox,oy,0xFFa07840.toInt()) // Chest front
            p.color=0xFF6a4a20.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+1).f,(oy+1).f,(ox+15).f,(oy+15).f,p)
            p.color=0xFF8a6030.toInt();c.drawRect((ox+2).f,(oy+2).f,(ox+14).f,(oy+14).f,p)
            p.color=0xFFcc9900.toInt();c.drawCircle((ox+8).f,(oy+8).f,2f,p)}
        // Torch
        at(13,5).let{(ox,oy)->fill(c,ox,oy,0x00000000.toInt()) // transparent bg
            p.color=0xFF8b5e34.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+7).f,(oy+6).f,(ox+9).f,(oy+15).f,p)
            p.color=0xFFeecc44.toInt();c.drawRect((ox+6).f,(oy+2).f,(ox+10).f,(oy+7).f,p)
            p.color=0xFFffffff.toInt();c.drawRect((ox+7).f,(oy+1).f,(ox+9).f,(oy+4).f,p)}
        // Lantern
        at(14,5).let{(ox,oy)->
            p.color=0xFF888888.toInt();p.style=Paint.Style.FILL
            c.drawRect((ox+4).f,(oy+3).f,(ox+12).f,(oy+13).f,p)
            p.color=0xFFeecc44.toInt();c.drawRect((ox+5).f,(oy+4).f,(ox+11).f,(oy+12).f,p)
            p.color=0xFF666666.toInt();c.drawRect((ox+7).f,oy.f,(ox+9).f,(oy+3).f,p)}
        // Bookshelf side (15,5)
        at(15,5).let{(ox,oy)->fill(c,ox,oy,0xFFb8883a.toInt())
            val bookCols=intArrayOf(0xFFcc2222.toInt(),0xFF2222cc.toInt(),0xFF228822.toInt(),0xFFcc9900.toInt(),0xFF882288.toInt())
            for(i in 0..4){p.color=bookCols[i];p.style=Paint.Style.FILL;c.drawRect((ox+1+i*3).f,(oy+2).f,(ox+3+i*3).f,(oy+14).f,p)}}
        // Soul sand (15,5 used) — put at row 5 col 15 alt
        at(15,5).also {} // already done above (bookshelf wins, soul sand at row 6)

        // ── ROW 6: Ores, Nether, Deep ─────────────────────────────────────
        at(0,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF331a1a.toInt(),0xFF441a1a.toInt()),71)} // magma
        at(1,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFddd8c8.toInt(),0xFFccc8b8.toInt()),72)} // end stone
        at(2,6).let{(ox,oy)->fill(c,ox,oy,0xFFeeeeee.toInt()) // quartz top
            val sp=Paint().apply{style=Paint.Style.STROKE;strokeWidth=1f;color=0xFFcccccc.toInt()}
            for(r in 2..6 step 2)c.drawLine((ox+8).f,(oy+8-r).f,(ox+8).f,(oy+8+r).f,sp)}
        at(3,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFeeeeee.toInt(),0xFFdddddd.toInt()),73)} // quartz side
        at(4,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF9966aa.toInt(),0xFF8855aa.toInt(),0xFFaa77bb.toInt()),74)} // purpur/ancient debris top
        at(5,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF2a2a2a.toInt(),0xFF333338.toInt()),75)} // netherite/ancient debris side
        at(6,6).let{(ox,oy)->oreOverlay(c,ox,oy,0xFFcc7744.toInt(),76)} // copper ore
        at(7,6).let{(ox,oy)->deepslateOre(c,ox,oy,0xFF1a1a1a.toInt(),77)} // deepslate coal
        at(8,6).let{(ox,oy)->deepslateOre(c,ox,oy,0xFFd4b080.toInt(),78)} // deepslate iron
        at(9,6).let{(ox,oy)->deepslateOre(c,ox,oy,0xFF40e0d0.toInt(),79)} // deepslate diamond
        at(10,6).let{(ox,oy)->deepslateOre(c,ox,oy,0xFFffd700.toInt(),80)} // deepslate gold
        at(11,6).let{(ox,oy)->deepslateOre(c,ox,oy,0xFFcc7744.toInt(),81)} // deepslate copper
        at(12,6).let{(ox,oy)->deepslateOre(c,ox,oy,0xFF2244cc.toInt(),82)} // deepslate lapis
        at(13,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF4a1a1a.toInt(),0xFF5a2a2a.toInt()),83)} // nether brick
        at(14,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF6a1a1a.toInt(),0xFF7a2a2a.toInt()),84)} // red nether brick
        at(15,6).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF6a4a38.toInt(),0xFF5a3a28.toInt()),85)} // dripstone

        // ── ROW 7: Soil + Wool + Concrete ─────────────────────────────────
        at(0,7).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFaa7755.toInt(),0xFF9a6644.toInt(),0xFFbb8866.toInt()),86)} // terracotta
        at(1,7).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF553322.toInt(),0xFF442211.toInt(),0xFF664433.toInt()),87)} // mud
        at(2,7).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF448833.toInt(),0xFF337722.toInt(),0xFF559944.toInt()),88)} // moss
        at(3,7).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF6a4422.toInt(),0xFF5a3312.toInt(),0xFF7a5532.toInt()),89)} // coarse dirt
        at(4,7).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF4a7722.toInt(),0xFF3a6612.toInt()),90)} // podzol top
        at(5,7).let{(ox,oy)->noisy(c,ox,oy+3,intArrayOf(0xFF8b5e34.toInt(),0xFF7a5230.toInt()))  // podzol side
            for(py in 0..2) for(px in 0 until TILE_PX){p.color=if(rng(px,py,91)>0.5f)0xFF4a7722.toInt() else 0xFF3a6612.toInt();c.drawPoint((ox+px).f,(oy+py).f,p)}}
        at(6,7).let{(ox,oy)->fill(c,ox,oy,0xFFeeeeee.toInt())} // white wool
        at(7,7).let{(ox,oy)->fill(c,ox,oy,0xFFcc2222.toInt())} // red wool
        at(8,7).let{(ox,oy)->fill(c,ox,oy,0xFF2244cc.toInt())} // blue wool
        at(9,7).let{(ox,oy)->fill(c,ox,oy,0xFFeecc22.toInt())} // yellow wool
        at(10,7).let{(ox,oy)->fill(c,ox,oy,0xFF228822.toInt())} // green wool
        at(11,7).let{(ox,oy)->fill(c,ox,oy,0xFF222222.toInt())} // black wool
        at(12,7).let{(ox,oy)->fill(c,ox,oy,0xFF666666.toInt())} // gray concrete
        at(13,7).let{(ox,oy)->fill(c,ox,oy,0xFFdddddd.toInt())} // white concrete
        at(14,7).let{(ox,oy)->fill(c,ox,oy,0xFFcc3322.toInt())} // red concrete
        at(15,7).let{(ox,oy)->fill(c,ox,oy,0xFF2233cc.toInt())} // blue concrete

        // ── ROW 8: Aquatic + Specialty ─────────────────────────────────────
        at(0,8).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF9966cc.toInt(),0xFF8855bb.toInt(),0xFFaa77dd.toInt()),92)} // amethyst
        at(1,8).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF303038.toInt(),0xFF282830.toInt()),93)} // sculk
        at(2,8).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF881a22.toInt(),0xFF771218.toInt()),94)} // wart block
        at(3,8).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFFdd8800.toInt(),0xFFcc7700.toInt(),0xFFeeaa00.toInt()),95) // shroomlight
            p.color=0xFFffee88.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 2) for(px in 0 until TILE_PX step 2) if(rng(px,py,96)>0.75f)c.drawRect((ox+px).f,(oy+py).f,(ox+px+1).f,(oy+py+1).f,p)}
        at(4,8).let{(ox,oy)->noisy(c,ox,oy,intArrayOf(0xFF226688.toInt(),0xFF1a5577.toInt(),0xFF337799.toInt()),97)} // prismarine
        at(5,8).let{(ox,oy)->fill(c,ox,oy,0xFFaaddcc.toInt()) // sea lantern
            p.color=0xFF66bbaa.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 3) for(px in 0 until TILE_PX step 3) if(rng(px,py,98)>0.6f)c.drawRect((ox+px).f,(oy+py).f,(ox+px+2).f,(oy+py+2).f,p)}
        at(6,8).let{(ox,oy)->fill(c,ox,oy,0xFFddaa22.toInt()) // honey block
            p.color=0xFFcc9910.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 2) for(px in 0 until TILE_PX step 4) if(rng(px,py,99)>0.7f)c.drawRect((ox+px).f,(oy+py).f,(ox+px+3).f,(oy+py+1).f,p)}
        at(7,8).let{(ox,oy)->fill(c,ox,oy,0xFF7ACC44.toInt())} // placeholder
        at(8,8).let{(ox,oy)->fill(c,ox,oy,0xFF88CC44.toInt())} // slime block
        at(9,8).let{(ox,oy)->fill(c,ox,oy,0xFF222222.toInt()) // blackstone bricks
            p.color=0xFF111111.toInt();p.style=Paint.Style.FILL
            c.drawRect(ox.f,(oy+7).f,(ox+TILE_PX).f,(oy+8).f,p);c.drawRect((ox+7).f,oy.f,(ox+8).f,(oy+7).f,p)}
        at(10,8).let{(ox,oy)->fill(c,ox,oy,0xFF222222.toInt()) // gilded blackstone
            p.color=0xFFccaa00.toInt();p.style=Paint.Style.FILL
            for(py in 0 until TILE_PX step 3) for(px in 0 until TILE_PX step 4) if(rng(px,py,100)>0.65f)c.drawRect((ox+px).f,(oy+py).f,(ox+px+3).f,(oy+py+2).f,p)}
        at(11,8).let{(ox,oy)->fill(c,ox,oy,0xFFcc6633.toInt())} // raw copper block
        at(12,8).let{(ox,oy)->fill(c,ox,oy,0xFF44aacc.toInt())} // copper block (oxidized)

        // Fill all remaining rows with error/checkerboard tiles (rows 9–15)
        for (row in 9 until ATLAS_ROWS) for (col in 0 until ATLAS_COLS) {
            at(col, row).let { (ox, oy) -> fill(c, ox, oy, if ((col + row) % 2 == 0) 0xFFff00ff.toInt() else 0xFF110011.toInt()) }
        }
        // Bottom-left tile (0,15) = error block for unmapped IDs → solid dark purple
        at(0,15).let{(ox,oy)->fill(c,ox,oy,0xFF880088.toInt())}
    }

    private val Int.f get() = toFloat()
}
