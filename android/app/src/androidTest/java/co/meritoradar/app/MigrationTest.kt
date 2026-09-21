package co.meritoradar.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LegacyRadarDatabase::class.java
    )

    private fun v1ProcessInsert(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO processes (id, name, officialUrl, category, status, confidence, firstDetectedAt, lastCheckedAt, updatedAt) " +
                "VALUES ('test-id', 'Test Process', 'https://www.cnsc.gov.co/test', 'IN_DEVELOPMENT', 'UNKNOWN', 'UNCONFIRMED', '', '2024-01-01', '')"
        )
        db.execSQL("INSERT INTO following (processId) VALUES ('test-id')")
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2PreservesData() {
        helper.createDatabase(TEST_DB, 1).apply {
            v1ProcessInsert(this)
            close()
        }
        // content_cache (v2) is created by MIGRATION_1_2; data must survive.
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        val cursor = migrated.query("PRAGMA table_info(content_cache)")
        assertTrue(cursor.moveToFirst())
        assertEquals("cacheKey", cursor.getString(1))
        cursor.close()
        val following = migrated.query("SELECT * FROM following WHERE processId = 'test-id'")
        assertTrue(following.moveToFirst())
        following.close()
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3PreservesDataAndAddsSlug() {
        helper.createDatabase(TEST_DB, 2).apply {
            v1ProcessInsert(this)
            execSQL("INSERT INTO content_cache (cacheKey, payload, savedAt) VALUES ('test-key', 'test-payload', 123456)")
            close()
        }
        // slug is added by MIGRATION_2_3; existing rows get ''.
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        val process = migrated.query("SELECT * FROM processes WHERE id = 'test-id'")
        assertTrue(process.moveToFirst())
        assertEquals("", process.getString(process.getColumnIndexOrThrow("slug")))
        assertEquals("Test Process", process.getString(process.getColumnIndexOrThrow("name")))
        process.close()
        val cache = migrated.query("SELECT * FROM content_cache WHERE cacheKey = 'test-key'")
        assertTrue(cache.moveToFirst())
        assertEquals("test-payload", cache.getString(cache.getColumnIndexOrThrow("payload")))
        cache.close()
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrateAllPreservesData() {
        helper.createDatabase(TEST_DB, 1).apply {
            v1ProcessInsert(this)
            close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        val process = migrated.query("SELECT * FROM processes WHERE id = 'test-id'")
        assertTrue(process.moveToFirst())
        assertEquals("", process.getString(process.getColumnIndexOrThrow("slug")))
        assertEquals("Test Process", process.getString(process.getColumnIndexOrThrow("name")))
        process.close()
        val following = migrated.query("SELECT * FROM following WHERE processId = 'test-id'")
        assertTrue(following.moveToFirst())
        following.close()
        migrated.close()
    }
}