package pl.edu.prz.arcadhd.storage

import java.sql.Connection
import java.sql.DriverManager

/** Exercises the production SQL and reducer against a real, file-backed SQLite database. */
class JdbcJournalSql(path: String) : JournalSql, AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path")
    var failOnSql: String? = null

    init {
        execute("PRAGMA foreign_keys=ON")
        execute("PRAGMA synchronous=FULL")
    }

    override fun execute(sql: String, args: List<Any?>) {
        if (failOnSql?.let { sql.startsWith(it) } == true) error("injected_storage_failure")
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.execute()
        }
    }

    override fun query(sql: String, args: List<Any?>): List<Map<String, String?>> =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                val rows = mutableListOf<Map<String, String?>>()
                while (result.next()) {
                    rows += (1..result.metaData.columnCount).associate {
                        result.metaData.getColumnName(it) to result.getString(it)
                    }
                }
                rows
            }
        }

    override fun <T> transaction(block: () -> T): T {
        check(connection.autoCommit) { "nested_test_transaction" }
        connection.autoCommit = false
        try {
            val value = block()
            connection.commit()
            return value
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally { connection.autoCommit = true }
    }

    override fun close() { connection.close() }
}
