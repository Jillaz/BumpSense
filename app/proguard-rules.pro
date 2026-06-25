# === Room (База данных) ===
# Сохраняем только классы с аннотацией @Entity и интерфейсы с @Dao
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }

# Сохраняем методы, помеченные аннотациями Room
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keepattributes *Annotation*

# === MapLibre (Карты) ===
# MapLibre уже содержит свои правила защиты, нам нужно только подавить возможные предупреждения
-dontwarn org.maplibre.android.**

# === Наши классы ===
# Сохраняем маппер GeoJSON (на всякий случай, хотя он не использует рефлексию)
-keep class ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper { *; }