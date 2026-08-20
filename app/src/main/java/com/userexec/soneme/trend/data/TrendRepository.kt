package com.userexec.soneme.trend.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.userexec.soneme.trend.model.*
import com.userexec.soneme.trend.time.TimeMath
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TrendRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("trend_storage", Context.MODE_PRIVATE)
    private val resolver: ContentResolver get() = context.contentResolver

    data class Storage(val treeUri: Uri, val folderDocId: String)
    enum class Mutation { SET, INCREMENT, DECREMENT }

    fun configuredStorage(): Storage? {
        val tree = prefs.getString("treeUri", null) ?: return null
        val docId = prefs.getString("folderDocId", null) ?: return null
        val storage = Storage(Uri.parse(tree), docId)
        return if (documentExists(documentUri(storage))) storage else null
    }

    fun clearStorage() = prefs.edit().remove("treeUri").remove("folderDocId").apply()

    fun configureSelectedTree(treeUri: Uri): Storage {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { resolver.takePersistableUriPermission(treeUri, flags) } catch (_: Exception) { }
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val rootName = queryName(rootUri)
        val folderId = if (rootName.equals("SonemeTrend", true)) rootId else {
            findChild(treeUri, rootId, "SonemeTrend")?.first ?: run {
                val created = DocumentsContract.createDocument(resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, "SonemeTrend")
                    ?: throw IllegalStateException("Could not create SonemeTrend folder")
                DocumentsContract.getDocumentId(created)
            }
        }
        prefs.edit().putString("treeUri", treeUri.toString()).putString("folderDocId", folderId).apply()
        return Storage(treeUri, folderId)
    }

    fun listFilenames(): List<String> {
        val storage = configuredStorage() ?: return emptyList()
        return listChildren(storage.treeUri, storage.folderDocId).filter { it.third != DocumentsContract.Document.MIME_TYPE_DIR }.map { it.second }
    }

    fun exists(filename: String): Boolean = fileUri(filename) != null

    fun read(filename: String): TrendFile {
        val uri = fileUri(filename) ?: throw IllegalArgumentException("CSV missing")
        val text = resolver.openInputStream(uri)?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }
            ?: throw IllegalArgumentException("CSV unreadable")
        return parseCsv(text)
    }

    fun create(filename: String, unit: String, basis: TimeBasis, goal: BigDecimal?) {
        require(safeFilename(filename)) { "Invalid filename" }
        if (fileUri(filename) != null) throw IllegalArgumentException("File already exists")
        val storage = configuredStorage() ?: throw IllegalStateException("Storage unavailable")
        val parent = documentUri(storage)
        val created = DocumentsContract.createDocument(resolver, parent, "text/csv", filename)
            ?: throw IllegalStateException("Could not create CSV")
        writeUri(created, TrendFile(unit.trim(), basis, goal, emptyList()))
    }

    fun delete(filename: String) {
        fileUri(filename)?.let { DocumentsContract.deleteDocument(resolver, it) }
    }

    fun updateGoal(filename: String, goal: BigDecimal?) {
        val file = read(filename)
        write(filename, file.copy(goal = goal))
    }

    /**
     * Re-read immediately, collapse every raw row currently resolving to the target bucket,
     * then write one replacement row using the UTC instant of this operation.
     */
    fun mutateBucket(filename: String, selectedInstant: Instant, operand: BigDecimal, mutation: Mutation): BigDecimal {
        val file = read(filename)
        val target = TimeMath.bucketFor(selectedInstant, file.timeBasis)
        val currentMembers = file.records.filter { TimeMath.bucketFor(it.instant, file.timeBasis) == target }
        val current = currentMembers.maxByOrNull { it.instant }?.value ?: BigDecimal.ZERO
        val updated = when (mutation) {
            Mutation.SET -> operand
            Mutation.INCREMENT -> current.add(operand)
            Mutation.DECREMENT -> current.subtract(operand)
        }
        val records = file.records.filterNot { TimeMath.bucketFor(it.instant, file.timeBasis) == target } + RawRecord(selectedInstant, updated)
        write(filename, file.copy(records = records.sortedBy { it.instant }))
        return updated
    }

    /** Removing a displayed logical point removes every raw row currently resolving to its bucket. */
    fun removeBucket(filename: String, bucket: BucketKey) {
        val file = read(filename)
        val records = file.records.filterNot { TimeMath.bucketFor(it.instant, file.timeBasis) == bucket }
        write(filename, file.copy(records = records))
    }

    fun fileIsValid(filename: String): Boolean = try { read(filename); true } catch (_: Exception) { false }

    fun safeFilename(value: String): Boolean {
        if (!value.endsWith(".csv", true) || value.length <= 4) return false
        if (value.lastOrNull() == ' ' || value.lastOrNull() == '.') return false
        if (value.any { it.code < 32 || it in "<>:\"/\\|?*" }) return false
        return true
    }

    private fun write(filename: String, file: TrendFile) {
        val uri = fileUri(filename) ?: throw IllegalArgumentException("CSV missing")
        writeUri(uri, file)
    }

    private fun writeUri(uri: Uri, file: TrendFile) {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Timestamp", "Value", "Unit", "Time Basis", "Goal")
        rows += listOf("", "", file.unit, file.timeBasis.csvName, file.goal?.stripTrailingZeros()?.toPlainString() ?: "")
        file.records.sortedBy { it.instant }.forEach { r ->
            rows += listOf(r.instant.toString(), r.value.stripTrailingZeros().toPlainString(), "", "", "")
        }
        val text = rows.joinToString("\n", postfix = "\n") { row -> row.joinToString(",") { csvEscape(it) } }
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("CSV unwritable")
    }

    private fun parseCsv(text: String): TrendFile {
        val rows = parseRows(text).filterNot { it.size == 1 && it[0].isEmpty() }
        if (rows.size < 2) throw IllegalArgumentException("Missing settings")
        val header = normalizeFive(rows[0])
        if (header != listOf("Timestamp", "Value", "Unit", "Time Basis", "Goal")) throw IllegalArgumentException("Invalid headings")
        val settings = normalizeFive(rows[1])
        if (settings[0].isNotEmpty() || settings[1].isNotEmpty()) throw IllegalArgumentException("Invalid settings row")
        val unit = settings[2].trim()
        if (unit.isEmpty()) throw IllegalArgumentException("Missing unit")
        val basis = TimeBasis.fromCsv(settings[3]) ?: throw IllegalArgumentException("Invalid time basis")
        val goal = settings[4].trim().takeIf { it.isNotEmpty() }?.let { BigDecimal(it) }
        val records = rows.drop(2).map { raw ->
            val row = normalizeFive(raw)
            if (row[0].isBlank() || row[1].isBlank() || row.drop(2).any { it.isNotEmpty() }) throw IllegalArgumentException("Invalid data row")
            val timestamp = row[0].trim()
            if (!timestamp.endsWith("Z")) throw IllegalArgumentException("Timestamp must be UTC")
            RawRecord(Instant.parse(timestamp), BigDecimal(row[1].trim()))
        }
        if (records.zipWithNext().any { (a, b) -> a.instant >= b.instant }) throw IllegalArgumentException("Rows not strictly chronological")
        return TrendFile(unit, basis, goal, records)
    }

    private fun normalizeFive(row: List<String>): List<String> {
        if (row.size > 5 && row.drop(5).any { it.isNotEmpty() }) throw IllegalArgumentException("Too many columns")
        return List(5) { row.getOrElse(it) { "" } }
    }

    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else {
                when (c) {
                    '"' -> if (cell.isEmpty()) quoted = true else throw IllegalArgumentException("Malformed quote")
                    ',' -> { row.add(cell.toString()); cell.setLength(0) }
                    '\n' -> { row.add(cell.toString().removeSuffix("\r")); cell.setLength(0); rows.add(row); row = mutableListOf() }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (quoted) throw IllegalArgumentException("Unclosed quote")
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString().removeSuffix("\r")); rows.add(row) }
        return rows
    }

    private fun csvEscape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"${value.replace("\"", "\"\"")}\"" else value

    private fun documentUri(storage: Storage): Uri = DocumentsContract.buildDocumentUriUsingTree(storage.treeUri, storage.folderDocId)

    private fun fileUri(filename: String): Uri? {
        val s = configuredStorage() ?: return null
        val child = findChild(s.treeUri, s.folderDocId, filename) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(s.treeUri, child.first)
    }

    private fun documentExists(uri: Uri): Boolean = try {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    } catch (_: Exception) { false }

    private fun queryName(uri: Uri): String = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else ""
    } ?: ""

    private fun findChild(tree: Uri, parentId: String, name: String): Triple<String, String, String>? =
        listChildren(tree, parentId).firstOrNull { it.second.equals(name, true) }

    private fun listChildren(tree: Uri, parentId: String): List<Triple<String, String, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val result = mutableListOf<Triple<String, String, String>>()
        resolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) result += Triple(c.getString(0), c.getString(1), c.getString(2))
        }
        return result
    }
}
