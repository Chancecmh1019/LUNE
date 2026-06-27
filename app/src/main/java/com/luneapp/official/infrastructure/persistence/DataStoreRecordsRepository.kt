package com.luneapp.official.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.luneapp.official.domain.menstrual.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

private val RECORDS_KEY = stringPreferencesKey("menstrual_records")

@Inject
class DataStoreRecordsRepository(
    private val dataStore: DataStore<Preferences>
) : RecordsRepository {

    override suspend fun insertRecord(record: MenstrualRecord): AddRecordResult {
        val existing = loadRecords()
        saveRecords(existing + record)
        return AddRecordResult.Success(record)
    }

    override suspend fun getAllRecords(): List<MenstrualRecord> =
        loadRecords().filter { !it.isDeleted }

    override suspend fun updateRecord(record: MenstrualRecord): Boolean {
        val existing = loadRecords()
        val index = existing.indexOfFirst { it.id == record.id }
        if (index == -1) return false
        val updated = existing.toMutableList()
        updated[index] = record
        saveRecords(updated)
        return true
    }

    override suspend fun deleteRecord(id: String): Boolean {
        val existing = loadRecords()
        val index = existing.indexOfFirst { it.id == id }
        if (index == -1) return false
        val updated = existing.toMutableList()
        updated[index] = updated[index].copy(
            isDeleted = true,
            updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
        )
        saveRecords(updated)
        return true
    }

    override suspend fun clearAll() {
        dataStore.edit { prefs -> prefs.remove(RECORDS_KEY) }
    }

    private suspend fun loadRecords(): List<MenstrualRecord> {
        val json = dataStore.data.first()[RECORDS_KEY] ?: return emptyList()
        return json.toRecordList()
    }

    private suspend fun saveRecords(records: List<MenstrualRecord>) {
        dataStore.edit { prefs ->
            prefs[RECORDS_KEY] = records.toJson()
        }
    }
}

// ---------- JSON serialization ----------

private fun List<MenstrualRecord>.toJson(): String {
    return "[" + joinToString(",") { r ->
        val dailyJson = r.dailyRecords.dailyToJson()
        """{"id":"${r.id}","startDate":"${r.startDate}","endDate":"${r.endDate ?: ""}","endConfirmed":${r.endConfirmed},"dailyRecords":$dailyJson,"createdAt":${r.createdAtEpochMillis},"updatedAt":${r.updatedAtEpochMillis},"source":"${r.source.name}","isDeleted":${r.isDeleted}}"""
    } + "]"
}

private fun List<DailyRecord>.dailyToJson(): String {
    return "[" + joinToString(",") { dr ->
        buildString {
            append("{")
            append("\"date\":\"${dr.date}\",")
            append("\"intensity\":\"${dr.intensity?.name ?: ""}\",")
            append("\"mood\":\"${dr.mood?.name ?: ""}\",")
            append("\"symptoms\":\"${dr.symptoms.joinToString("|")}\",")
            append("\"bleedingType\":\"${dr.bleedingType.name}\",")
            append("\"notes\":${dr.notes.toJsonString()},")
            append("\"medications\":${dr.medications.toJsonString()},")
            append("\"clinicalNotes\":${dr.clinicalNotes.toJsonString()}")
            append("}")
        }
    } + "]"
}

/** Serialize a nullable String as a JSON string literal with proper escaping, or JSON null. */
private fun String?.toJsonString(): String {
    if (this == null) return "null"
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

private fun String.toRecordList(): List<MenstrualRecord> {
    if (this == "[]" || this.isBlank()) return emptyList()
    try {
        val content = removeSurrounding("[", "]")
        if (content.isBlank()) return emptyList()

        val records = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in content.indices) {
            when (content[i]) {
                '{', '[' -> depth++
                '}', ']' -> depth--
            }
            if (depth == 0 && i > start) {
                records.add(content.substring(start, i + 1))
                start = i + 2
            }
        }

        return records.map { it.parseRecord() }
    } catch (_: Exception) {
        return emptyList()
    }
}

private fun String.parseRecord(): MenstrualRecord {
    val s = removeSurrounding("{", "}")

    // Extract and remove the dailyRecords array first (it contains nested objects
    // that would confuse a flat field parser).
    val dailyKey = "\"dailyRecords\":["
    val dailyStart = s.indexOf(dailyKey) + dailyKey.length
    val dailyEnd = findMatchingBracket(s, dailyStart - 1)
    val dailyJson = s.substring(dailyStart, dailyEnd)
    val dailyRecords = dailyJson.parseDailyRecords()

    // Strip out the dailyRecords key+array from the string, then parse the
    // remaining flat scalar fields using the same robust parser used for daily records.
    val withoutDaily = "{" + s.removeRange(s.indexOf("\"dailyRecords\":"), dailyEnd + 1)
        .replace(",,", ",").trim(',') + "}"
    val map = parseJsonStringFields(withoutDaily)

    return MenstrualRecord(
        id = map["id"] ?: "",
        startDate = LocalDate.parse(map["startDate"] ?: ""),
        endDate = map["endDate"]?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
        endConfirmed = map["endConfirmed"] == "true",
        dailyRecords = dailyRecords,
        createdAtEpochMillis = map["createdAt"]?.toLongOrNull() ?: 0L,
        updatedAtEpochMillis = map["updatedAt"]?.toLongOrNull() ?: 0L,
        source = map["source"]?.takeIf { it.isNotBlank() }
            ?.let { runCatching { RecordSource.valueOf(it) }.getOrNull() }
            ?: RecordSource.MANUAL,
        isDeleted = map["isDeleted"] == "true"
    )
}

private fun findMatchingBracket(s: String, openIndex: Int): Int {
    var depth = 0
    for (i in openIndex until s.length) {
        when (s[i]) {
            '[' -> depth++
            ']' -> { depth--; if (depth == 0) return i }
        }
    }
    return s.length - 1
}

private fun String.parseDailyRecords(): List<DailyRecord> {
    if (this.isBlank()) return emptyList()
    try {
        // Split objects at top level only (depth-aware), so field values containing
        // "},{" don't cause premature splits.
        val objects = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in indices) {
            when (this[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        objects.add(substring(start, i + 1))
                        start = i + 1
                        while (start < length && (this[start] == ',' || this[start] == ' ')) start++
                    }
                }
            }
        }
        return objects.map { obj ->
            val map = parseJsonStringFields(obj)
            DailyRecord(
                date = LocalDate.parse(map["date"] ?: ""),
                intensity = map["intensity"]?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Intensity.valueOf(it) }.getOrNull() },
                mood = map["mood"]?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Mood.valueOf(it) }.getOrNull() },
                symptoms = map["symptoms"]?.takeIf { it.isNotBlank() }?.split("|") ?: emptyList(),
                bleedingType = map["bleedingType"]?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { BleedingType.valueOf(it) }.getOrNull() }
                    ?: BleedingType.NORMAL,
                notes = map["notes"]?.takeIf { it.isNotBlank() },
                medications = map["medications"]?.takeIf { it.isNotBlank() },
                clinicalNotes = map["clinicalNotes"]?.takeIf { it.isNotBlank() },
            )
        }
    } catch (_: Exception) {
        return emptyList()
    }
}

/**
 * Minimal JSON object parser that extracts string and null-valued fields.
 * Handles escaped characters (\\, \", \n, \r, \t) within string values.
 * Does not support nested objects or arrays — sufficient for [DailyRecord].
 */
private fun parseJsonStringFields(obj: String): Map<String, String?> {
    val result = mutableMapOf<String, String?>()
    val s = obj.trim().removePrefix("{").removeSuffix("}")
    var i = 0
    while (i < s.length) {
        // Skip whitespace and commas between fields
        while (i < s.length && (s[i] == ',' || s[i] == ' ' || s[i] == '\n' || s[i] == '\r')) i++
        if (i >= s.length) break

        // Expect opening quote of key
        if (s[i] != '"') { i++; continue }
        i++ // skip opening quote
        val keyStart = i
        while (i < s.length && s[i] != '"') i++
        val key = s.substring(keyStart, i)
        i++ // skip closing quote of key

        // Expect colon
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length || s[i] != ':') continue
        i++ // skip colon
        while (i < s.length && s[i] == ' ') i++

        if (i >= s.length) break

        if (s[i] == '"') {
            // String value — parse with escape handling
            i++ // skip opening quote
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        '"'  -> { sb.append('"');  i += 2 }
                        '\\' -> { sb.append('\\'); i += 2 }
                        'n'  -> { sb.append('\n'); i += 2 }
                        'r'  -> { sb.append('\r'); i += 2 }
                        't'  -> { sb.append('\t'); i += 2 }
                        else -> { sb.append(s[i + 1]); i += 2 }
                    }
                } else if (c == '"') {
                    i++ // skip closing quote
                    break
                } else {
                    sb.append(c)
                    i++
                }
            }
            result[key] = sb.toString()
        } else if (s.startsWith("null", i)) {
            result[key] = null
            i += 4
        } else {
            // Non-string scalar (bool / number) — read until comma or end
            val valStart = i
            while (i < s.length && s[i] != ',' && s[i] != '}') i++
            result[key] = s.substring(valStart, i).trim()
        }
    }
    return result
}
