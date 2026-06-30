package ru.razrabozavr.bumpsense.data.export

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.first
import ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

/**
 * Результат операции экспорта.
 */
sealed class ExportResult {
    data class Success(val tracksCount: Int, val pointsCount: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
    data object Empty : ExportResult()
}

/**
 * Результат операции импорта.
 */
sealed class ImportResult {
    data class Success(
        val tracksCount: Int,
        val pointsCount: Int,
        val skippedCount: Int = 0
    ) : ImportResult()
    data class Error(val message: String) : ImportResult()
    data object Empty : ImportResult()
}

/**
 * Менеджер для операций экспорта и импорта треков.
 *
 * ✅ РЕФАКТОРИНГ (Этап 3): Вынесено из MapViewModel для улучшения структуры кода.
 * - Устраняет дублирование кода между importTracks и appendTracks
 * - Инкапсулирует работу с файловой системой и парсингом JSON
 * - Возвращает типизированные результаты вместо прямого обновления UI
 */
class TrackExportImportManager(
    private val context: Context,
    private val trackRepository: TrackRepository
) {
    private val tag = "TrackExportImport"

    /**
     * Экспортирует все треки в GeoJSON файл.
     *
     * @param uri URI файла для записи
     * @param onProgress Callback для обновления сообщения прогресса
     * @return Результат экспорта
     */
    suspend fun exportTracks(
        uri: Uri,
        onProgress: (String) -> Unit
    ): ExportResult {
        return try {
            onProgress("Подготовка к экспорту...")

            val allTracks = trackRepository.getAllTracks().first()

            if (allTracks.isEmpty()) {
                return ExportResult.Empty
            }

            onProgress("Экспорт ${allTracks.size} треков...")

            val jsonString = GeoJsonMapper.tracksToGeoJson(allTracks)

            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return ExportResult.Error("Не удалось открыть файл для записи")

            outputStream.use { it.write(jsonString.toByteArray()) }

            val totalPoints = allTracks.sumOf { it.points.size }
            Log.d(tag, "✅ Экспортировано треков: ${allTracks.size}, точек: $totalPoints")

            ExportResult.Success(allTracks.size, totalPoints)
        } catch (e: Exception) {
            Log.e(tag, "❌ Ошибка экспорта", e)
            ExportResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    /**
     * Импортирует треки из GeoJSON файла, заменяя все существующие.
     *
     * @param uri URI файла для чтения
     * @param onProgress Callback для обновления сообщения прогресса
     * @return Результат импорта
     */
    suspend fun importTracks(
        uri: Uri,
        onProgress: (String) -> Unit
    ): ImportResult {
        return try {
            onProgress("Чтение файла...")

            val jsonString = readFileAsString(uri) ?: return ImportResult.Error("Не удалось открыть файл")

            if (jsonString.isEmpty()) {
                return ImportResult.Empty
            }

            Log.d(tag, "📄 Размер файла: ${jsonString.length} символов")

            onProgress("Парсинг треков...")
            val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)
            Log.d(tag, "📥 Распаршено треков: ${tracks.size}")

            if (tracks.isEmpty()) {
                return ImportResult.Error("Неверный формат файла или нет треков")
            }

            onProgress("Сохранение ${tracks.size} треков в БД...")
            Log.d(tag, "🗑️ Очистка базы перед импортом")
            trackRepository.clearDatabase()

            var totalPoints = 0
            tracks.forEach { track ->
                trackRepository.insertTrack(track)
                totalPoints += track.points.size
            }

            Log.d(tag, "✅ Импортировано треков: ${tracks.size}, точек: $totalPoints")
            ImportResult.Success(tracks.size, totalPoints)
        } catch (e: Exception) {
            Log.e(tag, "❌ Ошибка импорта", e)
            ImportResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    /**
     * Добавляет треки из GeoJSON файла к существующим (без удаления).
     * Дубликаты (по startTime) пропускаются.
     *
     * @param uri URI файла для чтения
     * @param onProgress Callback для обновления сообщения прогресса
     * @return Результат добавления
     */
    suspend fun appendTracks(
        uri: Uri,
        onProgress: (String) -> Unit
    ): ImportResult {
        return try {
            onProgress("Чтение файла...")

            val jsonString = readFileAsString(uri) ?: return ImportResult.Error("Не удалось открыть файл")

            if (jsonString.isEmpty()) {
                return ImportResult.Empty
            }

            onProgress("Парсинг треков...")
            val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)

            if (tracks.isEmpty()) {
                return ImportResult.Error("Неверный формат файла или нет треков")
            }

            val existingTracks = trackRepository.getAllTracks().first()
            val existingStartTimes = existingTracks.map { it.startTime }.toSet()

            val newTracks = tracks.filter { it.startTime !in existingStartTimes }
            val skippedCount = tracks.size - newTracks.size

            if (newTracks.isEmpty()) {
                return ImportResult.Success(0, 0, skippedCount)
            }

            onProgress("Сохранение ${newTracks.size} треков...")

            var totalPoints = 0
            newTracks.forEach { track ->
                trackRepository.insertTrack(track)
                totalPoints += track.points.size
            }

            Log.d(tag, "✅ Добавлено треков: ${newTracks.size}, точек: $totalPoints, пропущено: $skippedCount")
            ImportResult.Success(newTracks.size, totalPoints, skippedCount)
        } catch (e: Exception) {
            Log.e(tag, "❌ Ошибка добавления треков", e)
            ImportResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    /**
     * Читает файл по URI в строку.
     *
     * @return Содержимое файла или null при ошибке
     */
    private fun readFileAsString(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Ошибка чтения файла", e)
            null
        }
    }
}