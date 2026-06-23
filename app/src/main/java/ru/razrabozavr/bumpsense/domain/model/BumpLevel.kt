package ru.razrabozavr.bumpsense.domain.model

import androidx.compose.ui.graphics.Color

enum class BumpLevel(val color: Color, val minValue: Int, val maxValue: Int) {
    SMOOTH(Color(0xFF4CAF50), 0, 19),
    SLIGHT(Color(0xFFFFEB3B), 20, 39),
    MODERATE(Color(0xFFFF9800), 40, 59),
    STRONG(Color(0xFFF44336), 60, 79),
    EXTREME(Color(0xFFB71C1C), 80, 100);

    companion object {
        fun fromIndex(index: Int): BumpLevel {
            return entries.find { index in it.minValue..it.maxValue } ?: SMOOTH
        }
    }
}