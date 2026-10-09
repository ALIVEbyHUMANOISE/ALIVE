package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestEvidenceRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: QuestEvidenceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("project_ego_quest_evidence", Context.MODE_PRIVATE).edit().clear().apply()
        repository = QuestEvidenceRepository(context)
    }

    @Test
    fun `start and declare snapshots round-trip`() {
        repository.recordStepsAtStart("attempt-1", 42)
        repository.recordStepsAtDeclare("attempt-1", 700)

        assertEquals(PhysicalStepSnapshot(42, 700), repository.snapshotFor("attempt-1"))
    }

    @Test
    fun `a recorded unavailable marker survives and differs from never recorded`() {
        repository.recordStepsAtStart("attempt-2", null)

        val snapshot = repository.snapshotFor("attempt-2")
        assertNotNull(snapshot)
        assertNull(snapshot!!.startSteps)
        assertNull(snapshot.declareSteps)

        assertNull(repository.snapshotFor("attempt-never-recorded"))
    }

    @Test
    fun `declare does not overwrite start and start does not overwrite declare`() {
        repository.recordStepsAtStart("attempt-3", 10)
        repository.recordStepsAtDeclare("attempt-3", 99)

        repository.recordStepsAtStart("attempt-4", 5)
        repository.recordStepsAtDeclare("attempt-4", null)

        assertEquals(PhysicalStepSnapshot(10, 99), repository.snapshotFor("attempt-3"))
        assertEquals(PhysicalStepSnapshot(5, null), repository.snapshotFor("attempt-4"))
    }

    @Test
    fun `clear removes one attempt and clearAll removes every attempt`() {
        repository.recordStepsAtStart("attempt-a", 1)
        repository.recordStepsAtStart("attempt-b", 2)

        repository.clear("attempt-a")
        assertNull(repository.snapshotFor("attempt-a"))
        assertNotNull(repository.snapshotFor("attempt-b"))

        repository.recordStepsAtStart("attempt-c", 3)
        repository.clearAll()
        assertNull(repository.snapshotFor("attempt-b"))
        assertNull(repository.snapshotFor("attempt-c"))
    }

    @Test
    fun `evidence lives in its own preferences file, not the quest file`() {
        repository.recordStepsAtStart("attempt-iso", 1)

        val questPrefs = context.getSharedPreferences("project_ego_quests", Context.MODE_PRIVATE)
        assertNull(questPrefs.getString("attempt-iso", null))
        assertNotNull(repository.snapshotFor("attempt-iso"))
    }
}
