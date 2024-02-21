package com.avrora.telecom.cameraxlibrary;

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BorderView : View {
    private var paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE // Цвет рамки
        style = Paint.Style.STROKE // Рисуем только контур
        strokeWidth = 6f // Толщина линии рамки
    }
    private var drawRect: RectF = RectF()

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        init()
    }

    private fun init() {
        // Инициализация, если необходимо
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Рисуем круглую рамку
        val radius = width / 2f - paint.strokeWidth / 2 // Учитываем толщину линии
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
    }
}
