package com.avrora.telecom.cameraxlibrary;

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CameraButtonView : View {
    private var paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var drawRect: RectF = RectF()
    private var cornerRadius = 0f
    private var startWidth = 0f
    private var startHeight = 0f
    private var reduceAnimator: ValueAnimator = ValueAnimator()
    private var restoreAnimator: ValueAnimator = ValueAnimator()
    private val sizeAnimator: ValueAnimator = ValueAnimator()
    private val cornerAnimator: ValueAnimator = ValueAnimator()

    enum class State { DEFAULT, RECORDING }

    var currentState = State.DEFAULT
        set(value) {
            field = value
            updatePaintColor()
            if (value == State.RECORDING) {
                animatePressUp()
            }

            if (value == State.DEFAULT) {
                animateStopVideo()
            }
            invalidate()
        }

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        setupAnimators()
    }

    // Обновление цвета в зависимости от состояния
    private fun updatePaintColor() {
        paint.color = if (currentState == State.DEFAULT) Color.WHITE else Color.RED
    }

    private fun setupAnimators() {
        // Анимация для уменьшения размера на 10%
        reduceAnimator.setFloatValues(0f, 1f)
        reduceAnimator.duration = 100
        reduceAnimator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            updateSizeForAnimation(fraction)
        }

        // Анимация для восстановления размера
        restoreAnimator.setFloatValues(1f, 0f)
        restoreAnimator.duration = 100
        restoreAnimator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            updateSizeForAnimation(fraction)
        }

        // Настройка sizeAnimator
        sizeAnimator.duration = 300
        sizeAnimator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            val delta = (width.toFloat() - value) / 2f
            drawRect.set(delta, delta, width.toFloat() - delta, height.toFloat() - delta)
            invalidate()
        }

        // Настройка cornerAnimator
        cornerAnimator.duration = 300
        cornerAnimator.addUpdateListener { animation ->
            cornerRadius = animation.animatedValue as Float
            invalidate()
        }
    }

    private fun updateSizeForAnimation(fraction: Float, reduceScale: Float = 0.9f) {
        val endWidth = startWidth * reduceScale
        val endHeight = startHeight * reduceScale
        val newWidth = startWidth + (endWidth - startWidth) * fraction
        val newHeight = startHeight + (endHeight - startHeight) * fraction
        val deltaWidth = (startWidth - newWidth) / 2
        val deltaHeight = (startHeight - newHeight) / 2
        drawRect.set(deltaWidth, deltaHeight, startWidth - deltaWidth, startHeight - deltaHeight)
        invalidate()
    }

    fun animatePressDown() {
        reduceAnimator.start()
    }

    fun animatePressUp() {
        reduceAnimator.cancel()
        restoreAnimator.start()
    }

    fun animateStartVideo() {
        val startSize = width.toFloat()
        val reductionScale = 0.5f // Квадрат будет меньше по каждому измерению
        val endSize = startSize * reductionScale

        sizeAnimator.setFloatValues(startSize, endSize)
        cornerAnimator.setFloatValues(cornerRadius, 25f)

        sizeAnimator.start()
        cornerAnimator.start()
    }

    fun animateStopVideo() {
        val startSize = drawRect.width()
        val endSize = width.toFloat()

        sizeAnimator.setFloatValues(startSize, endSize)
        cornerAnimator.setFloatValues(cornerRadius, width / 2f)

        sizeAnimator.start()
        cornerAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        startWidth = width.toFloat()
        startHeight = height.toFloat()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawRect.set(0f, 0f, w.toFloat(), h.toFloat())
        cornerRadius = w / 2f
    }
}