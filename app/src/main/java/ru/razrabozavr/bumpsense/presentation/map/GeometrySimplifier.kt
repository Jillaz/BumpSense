package ru.razrabozavr.bumpsense.presentation.map

import ru.razrabozavr.bumpsense.domain.model.TrackPoint

/**
 * Алгоритм упрощения геометрии трека (Ramer-Douglas-Peucker).
 *
 * ✅ ОПТИМИЗАЦИЯ:
 * - Уменьшает количество точек в треке в 5-20 раз
 * - Сохраняет ключевые точки с высоким bumpIndex (неровности)
 * - Применяется только к истории треков (текущий трек не упрощается)
 */
object GeometrySimplifier {

    // Порог bumpIndex: точки с bumpIndex > этого значения НЕ упрощаются
    private const val CRITICAL_BUMP_INDEX = 50

    // Максимальное расстояние для упрощения (в градусах)
    // 0.0001° ≈ 11 метров на экваторе
    private const val DEFAULT_EPSILON = 0.0001

    /**
     * Упрощает список точек трека, сохраняя ключевые точки (неровности).
     *
     * @param points Исходные точки трека
     * @param epsilon Максимальное расстояние для упрощения (в градусах)
     * @param minPoints Минимальное количество точек (трек не упрощается ниже этого)
     * @return Упрощённый список точек
     */
    fun simplify(
        points: List<TrackPoint>,
        epsilon: Double = DEFAULT_EPSILON,
        minPoints: Int = 100
    ): List<TrackPoint> {
        if (points.size <= minPoints) return points
        if (points.size < 3) return points

        // Находим индексы критических точек (с высоким bumpIndex)
        val criticalIndices = points.mapIndexedNotNull { index, point ->
            if (point.bumpIndex > CRITICAL_BUMP_INDEX) index else null
        }.toSet()

        // Применяем RDP с учётом критических точек
        val simplifiedIndices = rdpSimplify(points, 0, points.size - 1, epsilon, criticalIndices)

        // Сортируем индексы и возвращаем точки
        return simplifiedIndices.sorted().map { points[it] }
    }

    /**
     * Рекурсивный алгоритм Ramer-Douglas-Peucker.
     * Возвращает список индексов точек, которые нужно сохранить.
     */
    private fun rdpSimplify(
        points: List<TrackPoint>,
        startIndex: Int,
        endIndex: Int,
        epsilon: Double,
        criticalIndices: Set<Int>
    ): Set<Int> {
        if (endIndex - startIndex < 2) {
            // Базовый случай: 2 точки или меньше — сохраняем обе
            return setOf(startIndex, endIndex)
        }

        val start = points[startIndex]
        val end = points[endIndex]

        // Находим точку с максимальным перпендикулярным расстоянием
        var maxDistance = 0.0
        var maxIndex = startIndex

        for (i in startIndex + 1 until endIndex) {
            val point = points[i]
            val distance = perpendicularDistance(point, start, end)

            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }

        return if (maxDistance > epsilon) {
            // Рекурсивно упрощаем обе части
            val left = rdpSimplify(points, startIndex, maxIndex, epsilon, criticalIndices)
            val right = rdpSimplify(points, maxIndex, endIndex, epsilon, criticalIndices)
            left + right
        } else {
            // Все промежуточные точки можно удалить, КРОМЕ критических
            val result = mutableSetOf(startIndex, endIndex)
            for (i in startIndex + 1 until endIndex) {
                if (i in criticalIndices) {
                    result.add(i)
                }
            }
            result
        }
    }

    /**
     * Вычисляет перпендикулярное расстояние от точки до линии (в градусах).
     */
    private fun perpendicularDistance(
        point: TrackPoint,
        lineStart: TrackPoint,
        lineEnd: TrackPoint
    ): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude

        // Если линия вырождена в точку
        if (dx == 0.0 && dy == 0.0) {
            val pdx = point.longitude - lineStart.longitude
            val pdy = point.latitude - lineStart.latitude
            return kotlin.math.sqrt(pdx * pdx + pdy * pdy)
        }

        // Формула расстояния от точки до линии
        val t = ((point.longitude - lineStart.longitude) * dx +
                (point.latitude - lineStart.latitude) * dy) / (dx * dx + dy * dy)

        val clampedT = t.coerceIn(0.0, 1.0)

        val projLon = lineStart.longitude + clampedT * dx
        val projLat = lineStart.latitude + clampedT * dy

        val pdx = point.longitude - projLon
        val pdy = point.latitude - projLat

        return kotlin.math.sqrt(pdx * pdx + pdy * pdy)
    }
}