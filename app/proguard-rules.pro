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

# === RuStore Updates SDK ===
# Сохраняем все классы SDK от обфускации и удаления
-keep class ru.rustore.sdk.appupdate.model.AppUpdateInfo { *; }
-keep class ru.rustore.sdk.appupdate.model.AppUpdateOptions { *; }
-keep class ru.rustore.sdk.appupdate.model.AppUpdateOptions$Builder { *; }
-keep class ru.rustore.sdk.appupdate.model.AppUpdateType { *; }
-keep class ru.rustore.sdk.appupdate.model.InstallState { *; }
-keep class ru.rustore.sdk.appupdate.model.InstallStatus { *; }
-keep class ru.rustore.sdk.appupdate.model.UpdateAvailability { *; }
-keepclassmembers class ru.rustore.sdk.** { *; }

# Подавляем предупреждения, если SDK использует опциональные зависимости
-dontwarn ru.rustore.sdk.**

# Сохраняем Parcelable-модели SDK (используются при передаче между процессами)
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Сохраняем Serializable-модели SDK (если SDK использует сериализацию)
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}