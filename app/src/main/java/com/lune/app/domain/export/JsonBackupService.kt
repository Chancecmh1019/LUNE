package com.lune.app.domain.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.lune.app.domain.menstrual.BleedingType
import com.lune.app.domain.menstrual.DailyRecord
import com.lune.app.domain.menstrual.Intensity
import com.lune.app.domain.menstrual.MenstrualRecord
import com.lune.app.domain.menstrual.Mood
import com.lune.app.domain.menstrual.RecordSource
import com.lune.app.domain.menstrual.RecordsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.time.Clock

/**
 * Serialises and deserialises the full menstrual record history to/from
 * a structured JSON backup file.
 *
 * The backup is written to the device's public Downloads directory so
 * the user can transfer it to a new device, archive it, or share it
 * with a clinician.
 *
 * Schema version is embedded in every backup so future migrations can
 * detect and transform older files gracefully.
 */
class JsonBackupService(
    private val context: Context,
    private val repository: RecordsRepository,
) {

    // ---------- Export ----------

    /**
     * Exports all non-deleted records to a JSON file in the public
     * Downloads folder. Returns the display path of the saved file
     * or throws on error.
     */
    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val records = repository.getAllRecords().filter { !it.isDeleted }
        val json = buildJson(records)
        val fileName = buildFileName()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(fileName, json)
        } else {
            saveLegacy(fileName, json)
        }
    }

    // ---------- Import ----------

    /**
     * Imports records from a JSON [InputStream] (obtained via SAF file
     * picker). Existing records whose IDs collide with imported ones are
     * skipped to avoid duplication.
     *
     * Returns the number of records successfully inserted.
     */
    suspend fun importBackup(stream: InputStream): Int = withContext(Dispatchers.IO) {
        val text = stream.bufferedReader().readText()
        val root = JSONObject(text)

        // Validate schema version
        val schemaVersion = root.optInt("schema_version", 1)
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            error("Backup schema version $schemaVersion is newer than this app supports. Please update the app.")
        }

        val recordsJson = root.getJSONArray("records")
        val existing = repository.getAllRecords().map { it.id }.toSet()
        var inserted = 0

        for (i in 0 until recordsJson.length()) {
            val obj = recordsJson.getJSONObject(i)
            val record = parseRecord(obj)
            if (record.id !in existing) {
                repository.insertRecord(record)
                inserted++
            }
        }
        inserted
    }

    // ---------- JSON build ----------

    private fun buildJson(records: List<MenstrualRecord>): String {
        val root = JSONObject().apply {
            put("schema_version", CURRENT_SCHEMA_VERSION)
            put("exported_at", Clock.System.now().toEpochMilliseconds())
            put("app_id", "com.lune.app")
            put("records", JSONArray().apply {
                records.forEach { put(recordToJson(it)) }
            })
        }
        return root.toString(2)
    }

    private fun recordToJson(r: MenstrualRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("start_date", r.startDate.toString())
        putOpt("end_date", r.endDate?.toString())
        put("end_confirmed", r.endConfirmed)
        put("source", r.source.name)
        put("is_deleted", r.isDeleted)
        put("created_at", r.createdAtEpochMillis)
        put("updated_at", r.updatedAtEpochMillis)
        put("daily_records", JSONArray().apply {
            r.dailyRecords.forEach { put(dailyToJson(it)) }
        })
    }

    private fun dailyToJson(d: DailyRecord): JSONObject = JSONObject().apply {
        put("date", d.date.toString())
        putOpt("intensity", d.intensity?.name)
        putOpt("mood", d.mood?.name)
        put("symptoms", JSONArray(d.symptoms))
        putOpt("notes", d.notes)
        putOpt("medications", d.medications)
        putOpt("clinical_notes", d.clinicalNotes)
        put("bleeding_type", d.bleedingType.name)
    }

    // ---------- JSON parse ----------

    private fun parseRecord(obj: JSONObject): MenstrualRecord = MenstrualRecord(
        id = obj.getString("id"),
        startDate = LocalDate.parse(obj.getString("start_date")),
        endDate = obj.optString("end_date").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
        endConfirmed = obj.optBoolean("end_confirmed", false),
        source = runCatching { RecordSource.valueOf(obj.optString("source", "MANUAL")) }
            .getOrDefault(RecordSource.MANUAL),
        isDeleted = obj.optBoolean("is_deleted", false),
        createdAtEpochMillis = obj.optLong("created_at", 0L),
        updatedAtEpochMillis = obj.optLong("updated_at", 0L),
        dailyRecords = parseDailyArray(obj.optJSONArray("daily_records")),
    )

    private fun parseDailyArray(arr: JSONArray?): List<DailyRecord> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { parseDaily(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun parseDaily(obj: JSONObject): DailyRecord {
        val symptomsArr = obj.optJSONArray("symptoms")
        val symptoms = if (symptomsArr != null) {
            (0 until symptomsArr.length()).map { symptomsArr.getString(it) }
        } else emptyList()

        return DailyRecord(
            date = LocalDate.parse(obj.getString("date")),
            intensity = obj.optString("intensity").takeIf { it.isNotBlank() }
                ?.let { runCatching { Intensity.valueOf(it) }.getOrNull() },
            mood = obj.optString("mood").takeIf { it.isNotBlank() }
                ?.let { runCatching { Mood.valueOf(it) }.getOrNull() },
            symptoms = symptoms,
            notes = obj.optString("notes").takeIf { it.isNotBlank() },
            medications = obj.optString("medications").takeIf { it.isNotBlank() },
            clinicalNotes = obj.optString("clinical_notes").takeIf { it.isNotBlank() },
            bleedingType = obj.optString("bleeding_type").takeIf { it.isNotBlank() }
                ?.let { runCatching { BleedingType.valueOf(it) }.getOrNull() }
                ?: BleedingType.NORMAL,
        )
    }

    // ---------- File I/O ----------

    private fun saveViaMediaStore(fileName: String, content: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
        } else {
            error("MediaStore Downloads not available below API 29")
        }
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        values.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Downloads.IS_PENDING, 0)
        }
        resolver.update(uri, values, null, null)
        return "Downloads/$fileName"
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(fileName: String, content: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(content.toByteArray()) }
        return file.absolutePath
    }

    private fun buildFileName(): String {
        val now = java.time.LocalDate.now()
        return "LUNE_backup_${now.year}${now.monthValue.toString().padStart(2, '0')}${now.dayOfMonth.toString().padStart(2, '0')}.json"
    }

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1
    }
}
