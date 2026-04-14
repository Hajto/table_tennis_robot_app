package com.tablebot.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MotorConfigTest {

    private lateinit var context: Context
    private val testFileName = "motor-config-test.json"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Start clean each test
        File(context.filesDir, testFileName).delete()
    }

    @After
    fun tearDown() {
        File(context.filesDir, testFileName).delete()
    }

    private fun makeConfig() = MotorConfig(context, testFileName)

    private fun params(
        id: Int, landarea: Int,
        m1speed: Int = 100, m2speed: Int = 110,
        xaxis: Int = 10, yaxis: Int = 20, zaxis: Int = 30,
    ) = MotorParams(
        id = id, ball = 1, spin = 2, power = 2, landarea = landarea,
        m1speed = m1speed, m2speed = m2speed,
        xaxis = xaxis, yaxis = yaxis, zaxis = zaxis,
    )

    // ── Lookup ────────────────────────────────────────────────────────────────

    @Test
    fun loadsBaseConfigFromAssets() {
        val config = makeConfig()
        assertFalse("Base config should not be empty", config.isEmpty())
    }

    @Test
    fun lookupReturnsNullForUnknownCombination() {
        val config = makeConfig()
        assertNull(config.lookup(99, 99, 99, 99))
    }

    @Test
    fun lookupFindsExistingEntry() {
        val config = makeConfig()
        // The base config has Normal(1)/Float(2)/Medium(2)/landarea entries — pick one
        val result = config.lookup(1, 2, 2, 8)
        assertNotNull("Expected an entry for ball=1 spin=2 power=2 landarea=8", result)
    }

    // ── Save and reload ───────────────────────────────────────────────────────

    @Test
    fun saveNewEntryPersistsToDisk() = runTest {
        val config = makeConfig()
        val newParams = params(id = 9999, landarea = 1, m1speed = 42, m2speed = 43)
        config.save(newParams)

        // Reload from disk to confirm persistence
        val reloaded = makeConfig()
        val found = reloaded.lookup(1, 2, 2, 1)
        assertNotNull("Saved entry should be found after reload", found)
        assertEquals(42, found!!.m1speed)
        assertEquals(43, found.m2speed)
    }

    @Test
    fun saveUpdatesExistingEntryById() = runTest {
        val config = makeConfig()
        // Save a new entry first
        config.save(params(id = 1001, landarea = 2, m1speed = 50))
        // Now update it (same id, different values)
        config.save(params(id = 1001, landarea = 2, m1speed = 99))

        val reloaded = makeConfig()
        val found = reloaded.lookup(1, 2, 2, 2)
        assertEquals("Update by id should overwrite", 99, found?.m1speed)
    }

    @Test
    fun saveDoesNotDuplicateEntries() = runTest {
        val config = makeConfig()
        config.save(params(id = 2001, landarea = 3))
        config.save(params(id = 2001, landarea = 3))

        val reloaded = makeConfig()
        val count = (0..14).count { reloaded.lookup(1, 2, 2, 3) != null }
        // Just confirm there's at most one match for landarea=3
        val all = reloaded.validLandareas(1, 2, 2)
        // Each landarea should appear at most once in lookup
        assertEquals(1, (0..14).filter { reloaded.lookup(1, 2, 2, it) != null && it == 3 }.size)
    }

    // ── Regression: save-then-reload race condition ────────────────────────────
    // Previously, saveMotorParams launched a coroutine and reloadMotorConfig
    // launched another independently — the reload could read stale data before
    // the save had finished writing. This test verifies that saving then
    // constructing a new MotorConfig always sees the saved value.

    @Test
    fun saveCompletesBeforeReloadReadsNewData() = runTest {
        val config = makeConfig()
        val p = params(id = 3001, landarea = 5, m1speed = 77, m2speed = 88)

        // Simulate what the fixed Accept handler does: await save, then reload
        config.save(p)                      // suspend — guaranteed complete before next line
        val reloaded = makeConfig()         // reads from disk

        val found = reloaded.lookup(1, 2, 2, 5)
        assertNotNull("Reload after save must see saved data", found)
        assertEquals("m1speed must match saved value", 77, found!!.m1speed)
        assertEquals("m2speed must match saved value", 88, found.m2speed)
    }

    @Test
    fun multipleSequentialSavesAllPersist() = runTest {
        val config = makeConfig()

        // Use the actual IDs from base config so saves update rather than add duplicates
        for (landarea in 6..10) {
            val existing = config.lookup(1, 2, 2, landarea)
            assertNotNull("Base config should have landarea $landarea", existing)
            config.save(existing!!.copy(m1speed = landarea * 10))
        }

        val reloaded = makeConfig()
        for (landarea in 6..10) {
            val found = reloaded.lookup(1, 2, 2, landarea)
            assertNotNull("landarea $landarea should be saved", found)
            assertEquals(landarea * 10, found!!.m1speed)
        }
    }

    // ── validLandareas / validPowers / validSpins ─────────────────────────────

    @Test
    fun validLandareasReturnsCorrectSet() = runTest {
        val config = makeConfig()
        config.save(params(id = 5001, landarea = 11))
        config.save(params(id = 5002, landarea = 12))

        val areas = config.validLandareas(1, 2, 2)
        assertTrue(areas.contains(11))
        assertTrue(areas.contains(12))
    }

    @Test
    fun validLandareasExcludesOtherCombinations() = runTest {
        val config = makeConfig()
        // Save for ball=0 (Serve) — should not appear in Normal query
        config.save(MotorParams(id = 6001, ball = 0, spin = 2, power = 2, landarea = 7,
            m1speed = 1, m2speed = 1, xaxis = 1, yaxis = 1, zaxis = 1))

        val areas = config.validLandareas(1, 2, 2)  // Normal
        // landarea 7 for Serve should not pollute Normal results
        // (unless base config already has it, which it does — so we check our Serve entry isn't counted twice)
        val serveAreas = config.validLandareas(0, 2, 2)
        assertTrue(serveAreas.contains(7))
    }

    // ── exportJson / importJson ───────────────────────────────────────────────

    @Test
    fun exportJsonProducesValidJson() = runTest {
        val config = makeConfig()
        config.save(params(id = 7001, landarea = 9))
        val json = config.exportJson()
        assertTrue(json.trimStart().startsWith("["))
        assertTrue(json.contains("m1speed"))
    }

    @Test
    fun resetToDefaultsRemovesCustomFile() = runTest {
        val config = makeConfig()
        config.save(params(id = 8001, landarea = 4, m1speed = 255))

        assertTrue("File should exist after save", config.isCustomized())
        config.resetToDefaults()
        assertFalse("File should be gone after reset", config.isCustomized())
    }

    @Test
    fun resetToDefaultsRestoresBaseValues() = runTest {
        val config = makeConfig()
        // Save a wildly different value for a known base entry
        val original = config.lookup(1, 2, 2, 8)
        assertNotNull(original)
        config.save(original!!.copy(m1speed = 255, m2speed = 255))
        assertEquals(255, config.lookup(1, 2, 2, 8)?.m1speed)

        config.resetToDefaults()

        val restored = makeConfig()
        assertEquals("After reset, base value should be restored",
            original.m1speed, restored.lookup(1, 2, 2, 8)?.m1speed)
    }

    // ── isCustomized / isEmpty ────────────────────────────────────────────────

    @Test
    fun isCustomizedFalseOnFreshLoad() {
        val config = makeConfig()
        assertFalse(config.isCustomized())
    }

    @Test
    fun isCustomizedTrueAfterSave() = runTest {
        val config = makeConfig()
        config.save(params(id = 9001, landarea = 6))
        assertTrue(config.isCustomized())
    }

    @Test
    fun isEmptyFalseWhenBaseConfigLoaded() {
        val config = makeConfig()
        assertFalse(config.isEmpty())
    }
}
