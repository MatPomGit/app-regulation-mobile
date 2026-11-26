package pl.edu.prz.arcadhd.platform.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import pl.edu.prz.arcadhd.storage.JournalSql
import pl.edu.prz.arcadhd.storage.SessionJournalSchema

class JournalStorageException : IllegalStateException("local_storage_failure")

/** App-private, engineering-only database. Android backup is disabled in the manifest. */
class AndroidJournalDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "engineering-session-journal.db", null, SessionJournalSchema.VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.execSQL("PRAGMA synchronous=FULL")
    }
    override fun onCreate(db: SQLiteDatabase) {
        SessionJournalSchema.create(AndroidJournalSql(db))
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (!SessionJournalSchema.upgrade(AndroidJournalSql(db), oldVersion, newVersion)) {
            throw JournalStorageException()
        }
    }
}

class AndroidJournalSql(private val db: SQLiteDatabase) : JournalSql {
    override fun execute(sql: String, args: List<Any?>) {
        try {
            db.execSQL(sql, args.toTypedArray())
        } catch (_: SQLiteException) { throw JournalStorageException() }
    }

    override fun query(sql: String, args: List<Any?>): List<Map<String, String?>> {
        try {
            db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { cursor ->
                val rows = mutableListOf<Map<String, String?>>()
                while (cursor.moveToNext()) {
                    rows += cursor.columnNames.indices.associate { index ->
                        cursor.getColumnName(index) to
                            if (cursor.isNull(index)) null else cursor.getString(index)
                    }
                }
                return rows
            }
        } catch (_: SQLiteException) { throw JournalStorageException() }
    }

    override fun <T> transaction(block: () -> T): T {
        try {
            db.beginTransaction()
            try {
                val value = block()
                db.setTransactionSuccessful()
                return value
            } finally { db.endTransaction() }
        } catch (_: SQLiteException) { throw JournalStorageException() }
    }
}
