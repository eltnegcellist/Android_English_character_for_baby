package com.eltnegcellist.emma

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

internal data class HistoryEntry(val id: String, val sessionId: String, val createdAt: String,
    val japaneseText: String, val englishText: String, val topic: String, val engine: String) {
    companion object {
        fun create(session: String, japanese: String, english: String, topic: String, engine: String) =
            HistoryEntry(UUID.randomUUID().toString(), session, Instant.now().toString(), japanese, english, topic, engine)
    }
}
/** Text only; no microphone audio, generated audio, or cloud backup. */
internal class ConversationHistory(context: Context) : SQLiteOpenHelper(context, "conversation-history.db", null, 1) {
    private val worker = Executors.newSingleThreadExecutor()
    val errorState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private fun failed(error: Throwable) { android.os.Handler(android.os.Looper.getMainLooper()).post { errorState.value="履歴を保存・読込できませんでした: ${error.message}" } }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY, sessionId TEXT NOT NULL, createdAt TEXT NOT NULL, japaneseText TEXT NOT NULL, englishText TEXT NOT NULL, topic TEXT NOT NULL, engine TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'conversation', schemaVersion INTEGER NOT NULL DEFAULT 1)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun append(entry: HistoryEntry) { worker.execute {
        runCatching {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("INSERT OR IGNORE INTO history (id,sessionId,createdAt,japaneseText,englishText,topic,engine) VALUES (?,?,?,?,?,?,?)", arrayOf(entry.id, entry.sessionId, entry.createdAt, entry.japaneseText, entry.englishText, entry.topic, entry.engine))
                db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY createdAt DESC, rowid DESC LIMIT 1000)")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }.onFailure(::failed)
    } }
    fun list(callback: (List<HistoryEntry>) -> Unit) { worker.execute {
        val entries = runCatching {
            readableDatabase.rawQuery("SELECT id,sessionId,createdAt,japaneseText,englishText,topic,engine FROM history ORDER BY createdAt DESC, rowid DESC", null).use { c ->
                buildList { while (c.moveToNext()) add(HistoryEntry(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6))) }
            }
        }.onFailure(::failed).getOrDefault(emptyList())
        android.os.Handler(android.os.Looper.getMainLooper()).post { callback(entries) }
    } }
    fun delete(id: String?, callback: () -> Unit) { worker.execute {
        runCatching { writableDatabase.delete("history", if (id == null) null else "id=?", if (id == null) null else arrayOf(id)) }.onFailure(::failed)
        android.os.Handler(android.os.Looper.getMainLooper()).post(callback)
    } }
}
