package co.meritoradar.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import co.meritoradar.app.MIGRATION_1_2
import co.meritoradar.app.MIGRATION_2_3

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MigrationTestHelper.TEST_DATABASE
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        val database = helper.createDatabase(TEST_DB, 1).apply {
            // Create initial schema (version 1)
            execSQL("CREATE TABLE IF NOT EXISTS processes (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, status TEXT NOT NULL, officialUrl TEXT NOT NULL, category TEXT NOT NULL, confidence TEXT NOT NULL, lastCheckedAt TEXT NOT NULL)")
            execSQL("CREATE TABLE IF NOT EXISTS following (processId TEXT NOT NULL PRIMARY KEY)")
        }

        // Run migration
        database.close()
        database = helper.runMigrationsAndValidate(TEST_DB, true, MIGRATION_1_2)

        // Verify migration succeeded
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        val database = helper.createDatabase(TEST_DB, 2).apply {
            // Create schema (version 2)
            execSQL("CREATE TABLE IF NOT EXISTS processes (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, status TEXT NOT NULL, officialUrl TEXT NOT NULL, category TEXT NOT NULL, confidence TEXT NOT NULL, lastCheckedAt TEXT NOT NULL)")
            execSQL("CREATE TABLE IF NOT EXISTS following (processId TEXT NOT NULL PRIMARY KEY)")
            execSQL("CREATE TABLE IF NOT EXISTS content_cache (cacheKey TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, savedAt INTEGER NOT NULL)")

            // Insert test data
            execSQL("INSERT INTO processes (id, name, status, officialUrl, category, confidence, lastCheckedAt) VALUES ('test-id', 'Test Process', 'UNKNOWN', 'https://www.cnsc.gov.co/test', 'IN_DEVELOPMENT', 'UNCONFIRMED', '2024-01-01')")
            execSQL("INSERT INTO following (processId) VALUES ('test-id')")
            execSQL("INSERT INTO content_cache (cacheKey, payload, savedAt) VALUES ('test-key', 'test-payload', 123456)")
        }

        // Run migration
        database.close()
        database = helper.runMigrationsAndValidate(TEST_DB, true, MIGRATION_2_3)

        // Verify migration succeeded and data preserved
        val cursor = database.query("SELECT * FROM processes WHERE id = 'test-id'")
        Assert.assertTrue(cursor.moveToFirst())
        Assert.assertEquals("Test Process", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        cursor.close()

        // Verify slug column exists
        val slugIndex = cursor.getColumnIndex("slug")
        Assert.assertTrue(slugIndex >= 0)

        // Verify following data preserved
        val followingCursor = database.query("SELECT * FROM following WHERE processId = 'test-id'")
        Assert.assertTrue(followingCursor.moveToFirst())
        followingCursor.close()

        // Verify content_cache data preserved
        val cacheCursor = database.query("SELECT * FROM content_cache WHERE cacheKey = 'test-key'")
        Assert.assertTrue(cacheCursor.moveToFirst())
        cacheCursor.close()

        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        var database = helper.createDatabase(TEST_DB, 1).apply {
            // Create initial schema (version 1)
            execSQL("CREATE TABLE IF NOT EXISTS processes (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, status TEXT NOT NULL, officialUrl TEXT NOT NULL, category TEXT NOT NULL, confidence TEXT NOT NULL, lastCheckedAt TEXT NOT NULL)")
            execSQL("CREATE TABLE IF NOT EXISTS following (processId TEXT NOT NULL PRIMARY KEY)")

            // Insert test data
            execSQL("INSERT INTO processes (id, name, status, officialUrl, category, confidence, lastCheckedAt) VALUES ('test-id', 'Test Process', 'UNKNOWN', 'https://www.cnsc.gov.co/test', 'IN_DEVELOPMENT', 'UNCONFIRMED', '2024-01-01')")
            execSQL("INSERT INTO following (processId) VALUES ('test-id')")
        }

        // Run all migrations
        database.close()
        database = helper.runMigrationsAndValidate(TEST_DB, true, MIGRATION_1_2, MIGRATION_2_3)

        // Verify final state
        val cursor = database.query("SELECT * FROM processes WHERE id = 'test-id'")
        Assert.assertTrue(cursor.moveToFirst())
        Assert.assertEquals("Test Process", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        Assert.assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("slug"))) // Default value
        cursor.close()

        val followingCursor = database.query("SELECT * FROM following WHERE processId = 'test-id'")
        Assert.assertTrue(followingCursor.moveToFirst())
        followingCursor.close()

        database.close()
    }
}
