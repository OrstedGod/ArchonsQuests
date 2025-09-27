package com.example.archonsquests

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class SakuraPetalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val petals = mutableListOf<Petal>()
    private val random = Random
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setWillNotDraw(false)
        postDelayed(::spawnPetal, 400) // чуть реже появляются
    }

    private fun spawnPetal() {
        if (petals.size < 40) { // немного уменьшил лимит для плавности
            petals.add(Petal(width, height))
        }
        postDelayed(::spawnPetal, 250 + random.nextInt(300).toLong()) // чуть медленнее генерация
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        petals.removeAll { !it.isAlive }
        petals.forEach { it.draw(canvas, paint) }
        invalidate()
    }

    inner class Petal(screenWidth: Int, screenHeight: Int) {
        private val startX = random.nextFloat() * screenWidth
        var x = startX
        var y = -60f // чуть выше старт

        // ✅ Увеличены размеры: 24–36 пикселей (было 18–30)
        val size = 24f + random.nextFloat() * 12f

        var rotation = random.nextFloat() * 360f
        val rotationSpeed = (random.nextFloat() - 0.5f) * 2f // медленнее вращение

        // ✅ Замедленное падение: 12–20 секунд (было 8–15)
        val duration = 15_000L + random.nextLong(10_000)

        val windOffset = (random.nextFloat() - 0.5f) * 70f
        val windWaveAmplitude = 15f
        var startTime = System.currentTimeMillis()

        // Нежно-розовый цвет
        val color = Color.rgb(
            255,
            215 + random.nextInt(30), // 215–245
            215 + random.nextInt(30)
        )
        val alpha = 190 + random.nextInt(50) // чуть прозрачнее для воздушности

        var isAlive = true
            private set

        fun draw(canvas: Canvas, paint: Paint) {
            val now = System.currentTimeMillis()
            val progress = ((now - startTime) / duration.toFloat()).coerceAtMost(1f)

            if (progress >= 1f) {
                isAlive = false
                return
            }

            // Плавное ускорение вниз (но медленнее)
            val eased = 1 - (1 - progress).pow(1.8f) // чуть мягче, чем pow(2)
            y = -60f + eased * (canvas.height + 120f)

            // Ветер и колебания
            val windWave = sin(progress * Math.PI * 2.3).toFloat() * windWaveAmplitude
            x = startX + windOffset * progress + windWave

            rotation += rotationSpeed

            paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation, 0f, 0f)
            canvas.scale(size / 24f, size / 24f)

            // Форма лепестка — чуть вытянута для крупных размеров
            val path = Path().apply {
                moveTo(12f, 1f)
                cubicTo(19f, 5f, 21f, 15f, 16f, 24f)
                cubicTo(11f, 29f, 7f, 25f, 9f, 16f)
                cubicTo(10f, 10f, 11f, 5f, 12f, 1f)
                close()
            }

            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}