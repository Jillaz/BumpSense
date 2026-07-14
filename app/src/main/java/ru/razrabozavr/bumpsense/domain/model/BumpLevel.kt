package ru.razrabozavr.bumpsense.domain.model

import androidx.compose.ui.graphics.Color

enum class BumpLevel(val color: Color, val minValue: Int, val maxValue: Int) {
    GOOD(Color(0xFF4CAF50), 0, 15),           // 🟢 Хорошая дорога
    SLIGHT(Color(0xFFFFEB3B), 15, 25),        // 🟡 Небольшие неровности
    MODERATE(Color(0xFFFF9800), 25, 50),      //  Заметные кочки
    STRONG(Color(0xFFF44336), 50, Int.MAX_VALUE); // 🔴 Сильные удары

    companion object {
        fun fromIndex(index: Int): BumpLevel {
            return when {
                index >= STRONG.minValue -> STRONG
                index >= MODERATE.minValue -> MODERATE
                index >= SLIGHT.minValue -> SLIGHT
                else -> GOOD
            }
        }
    }
}