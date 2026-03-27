package com.hia.cashia

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import kotlin.math.sqrt

class ScratchCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var scratchPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 65f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private var textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }

    private var subtitlePaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private var scratchBitmap: Bitmap? = null
    private var scratchCanvas: Canvas? = null
    private var lastX = 0f
    private var lastY = 0f
    private var isScratched = false
    private var revealAnimationRunning = false
    private var onScratchCompleteListener: ((Boolean) -> Unit)? = null

    var rewardAmount: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    fun setOnScratchCompleteListener(listener: (Boolean) -> Unit) {
        onScratchCompleteListener = listener
    }

    fun resetScratch() {
        isScratched = false
        revealAnimationRunning = false
        createScratchLayer()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        createScratchLayer()
    }

    private fun createScratchLayer() {
        if (width > 0 && height > 0) {
            scratchBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            scratchCanvas = Canvas(scratchBitmap!!)
            scratchCanvas?.drawColor(Color.parseColor("#C0C0C0"))
            // Add texture
            val texturePaint = Paint().apply {
                color = Color.parseColor("#A0A0A0")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            for (i in 0 until width step 20) {
                scratchCanvas?.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), texturePaint)
                scratchCanvas?.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), texturePaint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawRevealedContent(canvas)
        scratchBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
    }

    private fun drawRevealedContent(canvas: Canvas) {
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#FFD700"), Color.parseColor("#FF8C00"),
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (isScratched || revealAnimationRunning) {
            textPaint.textSize = width / 5f
            textPaint.color = Color.WHITE
            canvas.drawText("${rewardAmount}", width / 2f, height / 2f, textPaint)
            subtitlePaint.textSize = width / 12f
            canvas.drawText("COINS WON!", width / 2f, height / 1.5f, subtitlePaint)
        } else {
            textPaint.textSize = width / 7f
            textPaint.color = Color.WHITE
            canvas.drawText("SCRATCH!", width / 2f, height / 2f - 20, textPaint)
        }

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.WHITE
        }
        canvas.drawRect(4f, 4f, width - 4f, height - 4f, borderPaint)

        if (!isScratched && !revealAnimationRunning) {
            val dotPaint = Paint().apply {
                color = Color.WHITE
                alpha = 80
                style = Paint.Style.FILL
            }
            for (i in 0 until width step 25) {
                for (j in 0 until height step 25) {
                    canvas.drawCircle(i.toFloat(), j.toFloat(), 3f, dotPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Request parent not to intercept touch events to prevent scrolling
        parent.requestDisallowInterceptTouchEvent(true)

        if (isScratched || revealAnimationRunning) {
            parent.requestDisallowInterceptTouchEvent(false)
            return true
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                scratchAt(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY))
                if (distance > 3) {
                    scratchLine(lastX, lastY, x, y)
                    lastX = x
                    lastY = y
                }
                scratchAt(x, y)
                checkScratchCompletion()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                checkScratchCompletion()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scratchAt(x: Float, y: Float) {
        scratchCanvas?.drawCircle(x, y, 48f, scratchPaint)
        invalidate()
    }

    private fun scratchLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        scratchCanvas?.drawLine(x1, y1, x2, y2, scratchPaint)
        invalidate()
    }

    private fun checkScratchCompletion() {
        if (isScratched || revealAnimationRunning) return

        var scratchedPixels = 0
        var totalChecked = 0
        val step = 15

        if (scratchBitmap != null) {
            for (i in 0 until width step step) {
                for (j in 0 until height step step) {
                    if (i < width && j < height) {
                        totalChecked++
                        val pixel = scratchBitmap?.getPixel(i, j) ?: continue
                        if (pixel == Color.TRANSPARENT) {
                            scratchedPixels++
                        }
                    }
                }
            }

            val percentageScratched = (scratchedPixels.toFloat() / totalChecked.toFloat()) * 100

            if (percentageScratched > 35 && !isScratched) {
                showRevealAnimation()
            }
        }
    }

    private fun showRevealAnimation() {
        revealAnimationRunning = true

        // Clear the scratch layer completely for a clean reveal
        scratchCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()

        val scaleUp = ScaleAnimation(1f, 1.1f, 1f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f)
        scaleUp.duration = 200

        val scaleDown = ScaleAnimation(1.1f, 1f, 1.1f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f)
        scaleDown.duration = 200
        scaleDown.startOffset = 200

        scaleUp.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                startAnimation(scaleDown)
            }
        })

        scaleDown.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                revealAnimationRunning = false
                isScratched = true
                invalidate()
                onScratchCompleteListener?.invoke(true)
            }
        })

        startAnimation(scaleUp)
    }
}