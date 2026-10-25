package com.szabolcshorvath.memorymap.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Builds database files exactly as an older app version created them, from the schemas Room exported
 * to `app/schemas`, and fills each table with one sample row.
 */
internal object SchemaDatabases {
    private val SCHEMA_DIR = File("schemas/com.szabolcshorvath.memorymap.data.MemoryMapDatabase")

    const val SAMPLE_DATE = "2026-01-04T10:00:00+01:00[Europe/Budapest]"
    const val SAMPLE_INT = 3L
    const val SAMPLE_REAL = 0.5

    /** The first schema version a backup can have: backups were introduced at version 6. */
    const val OLDEST_BACKUP_VERSION = 6

    fun schema(version: Int): JSONObject = JSONObject(File(SCHEMA_DIR, "$version.json").readText()).getJSONObject("database")

    /** Table name to column names, for the given schema version. */
    fun columns(version: Int): Map<String, Set<String>> {
        val entities = schema(version).getJSONArray("entities")
        return (0 until entities.length()).associate { i ->
            val entity = entities.getJSONObject(i)
            val fields = entity.getJSONArray("fields")
            entity.getString("tableName") to (0 until fields.length()).mapTo(HashSet()) { fields.getJSONObject(it).getString("columnName") }
        }
    }

    /** Creates database [name] in the app's databases directory at [version], with one sample row per table. */
    fun create(context: Context, name: String, version: Int): File {
        context.deleteDatabase(name)
        val schema = schema(version)
        val entities = schema.getJSONArray("entities").objects()
            .sortedBy { if (it.getString("tableName") == "memory_groups") 0 else 1 } // parents first
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            entities.forEach { createTable(db, it) }
            schema.getJSONArray("setupQueries").strings().forEach { db.execSQL(it) }
            entities.forEach { insertSampleRow(db, it) }
            db.version = version
        }
        return context.getDatabasePath(name)
    }

    private fun createTable(db: SQLiteDatabase, entity: JSONObject) {
        val table = entity.getString("tableName")
        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
        entity.optJSONArray("indices")?.objects()?.forEach { index ->
            db.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", table))
        }
    }

    private fun insertSampleRow(db: SQLiteDatabase, entity: JSONObject) {
        val table = entity.getString("tableName")
        val fields = entity.getJSONArray("fields").objects()
        val columns = fields.joinToString { "`${it.getString("columnName")}`" }
        val placeholders = fields.joinToString { "?" }
        val values = fields.map { sampleValue(table, it.getString("columnName"), it.getString("affinity")) }.toTypedArray()
        db.execSQL("INSERT INTO `$table` ($columns) VALUES ($placeholders)", values)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    fun sampleText(table: String, column: String) = "$table.$column"

    private fun sampleValue(table: String, column: String, affinity: String): Any = when (column) {
        "id", "groupId" -> 1L
        "startDate", "endDate" -> SAMPLE_DATE
        "type" -> "VIDEO"
        else -> when (affinity) {
            "TEXT" -> sampleText(table, column)
            "INTEGER" -> SAMPLE_INT
            "REAL" -> SAMPLE_REAL
            else -> error("Unexpected affinity $affinity for $table.$column")
        }
    }
}
