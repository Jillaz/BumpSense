package ru.razrabozavr.bumpsense.data.sensor

import kotlin.math.sqrt

/**
 * Калькулятор индекса неровности (BumpIndex) на основе данных акселерометра.
 * Использует скользящее окно для вычисления RMS (Root Mean Square) ускорения.
 *
 * ✅ ОПТИМИЗАЦИЯ:
 * - ArrayDeque вместо MutableList (O(1) для add/remove вместо O(n))
 * - Инкрементальный расчёт суммы квадратов (O(1) вместо O(n))
 *
 * ✅ ЧУВСТВИТЕЛЬНОСТЬ:
 * - maxAcceleration уменьшен с 15f до 25f для большей чувствительности
 * - Теперь при RMS=12.5 м/с² индекс=50 ("Заметные кочки")
 * - При RMS=25 м/с² индекс=100 ("Сильные удары")
 */
class BumpIndexCalculator(
    private val windowSize: Int = 50,
    private val maxAcceleration: Float = 25f  // ✅ УМЕНЬШЕНО с 15f до 25f
) {
    // ✅ ArrayDeque вместо MutableList
    // addLast() и removeFirst() — O(1) операции
    private val accelerationBuffer = ArrayDeque<Float>(windowSize)

    // ✅ Инкрементальная сумма квадратов для O(1) расчёта RMS
    private var sumOfSquares: Double = 0.0

    /**
     * Добавляет новое значение ускорения и возвращает текущий индекс неровности (0-100)
     */
    fun addSample(acceleration: Float): Int {
        val squared = (acceleration * acceleration).toDouble()

        // ✅ O(1) операции вместо O(n)
        if (accelerationBuffer.size >= windowSize) {
            val removed = accelerationBuffer.removeFirst()
            sumOfSquares -= (removed * removed).toDouble()
        }

        accelerationBuffer.addLast(acceleration)
        sumOfSquares += squared

        return calculateBumpIndex()
    }

    private fun calculateBumpIndex(): Int {
        if (accelerationBuffer.isEmpty()) return 0

        // ✅ O(1) расчёт вместо O(n) пересчёта суммы
        val rms = sqrt(sumOfSquares / accelerationBuffer.size).toFloat()

        // Нормализуем в диапазон 0-100
        val normalizedIndex = (rms / maxAcceleration * 100).toInt()

        return normalizedIndex.coerceIn(0, 100)
    }

    fun reset() {
        accelerationBuffer.clear()
        sumOfSquares = 0.0
    }
}