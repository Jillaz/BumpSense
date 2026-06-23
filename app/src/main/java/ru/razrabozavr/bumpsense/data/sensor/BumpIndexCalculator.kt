package ru.razrabozavr.bumpsense.data.sensor

import kotlin.math.sqrt

/**
 * Калькулятор индекса неровности (BumpIndex) на основе данных акселерометра.
 * Использует скользящее окно для вычисления RMS (Root Mean Square) ускорения.
 */
class BumpIndexCalculator(
    private val windowSize: Int = 50,
    private val maxAcceleration: Float = 15f
) {

    private val accelerationBuffer = mutableListOf<Float>()

    /**
     * Добавляет новое значение ускорения и возвращает текущий индекс неровности (0-100)
     */
    fun addSample(acceleration: Float): Int {
        accelerationBuffer.add(acceleration)

        if (accelerationBuffer.size > windowSize) {
            accelerationBuffer.removeAt(0)
        }

        return calculateBumpIndex()
    }

    private fun calculateBumpIndex(): Int {
        if (accelerationBuffer.isEmpty()) return 0

        // Вычисляем RMS (Root Mean Square)
        val sumOfSquares = accelerationBuffer.sumOf { (it * it).toDouble() }
        val rms = sqrt(sumOfSquares / accelerationBuffer.size).toFloat()

        // Нормализуем в диапазон 0-100
        val normalizedIndex = (rms / maxAcceleration * 100).toInt()

        return normalizedIndex.coerceIn(0, 100)
    }

    fun reset() {
        accelerationBuffer.clear()
    }
}