package ovh.litapp.neurhome3.ui.categories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.dao.ApplicationTagDao
import ovh.litapp.neurhome3.data.dao.TagDao
import ovh.litapp.neurhome3.data.models.ApplicationTag
import ovh.litapp.neurhome3.data.models.Tag
import ovh.litapp.neurhome3.data.models.WifiContext
import ovh.litapp.neurhome3.data.repositories.TagMoveDirection
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModelDependencies

/**
 * Covers [CategoryViewModel]'s write wiring: every tag write runs on `viewModelScope` through
 * `runTagWrite`, so a rejected write must neither crash the app nor report success to the caller
 * that asked for it. The reorder additionally runs under `NonCancellable` inside a transaction, so
 * a torn-down scope has to finish it instead of leaving every position half-rewritten.
 *
 * The tags come from a fake [TagDao], because the ordering arithmetic is [TagRepository]'s contract
 * and is pinned by `TagRepositoryTest`; what is pinned here is that the ViewModel calls it, passes
 * the direction through, and only notifies the caller when the write actually landed.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryViewModelTest {
    /**
     * `NeurhomeViewModelDependencies` is built from the concrete application graph (a
     * `NeurhomeRepository` and a `FavouritesRepository`, both final classes with no test seam), so
     * the Robolectric application is the only way to obtain one. The ViewModel under test never
     * reads through it: `applicationsForTag` is the single repository call and none of these tests
     * touch it.
     */
    private val application =
        RuntimeEnvironment.getApplication() as NeurhomeApplication

    /**
     * Captured while `Dispatchers.Main` is still Robolectric's paused main looper: the repository
     * starts its eagerly shared launcher-app flow on that scope, and it must not be picked up by
     * the test dispatcher that drives `viewModelScope` below.
     */
    private val neurhomeRepository = application.repository

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope resolves Dispatchers.Main at first use, so the test dispatcher has to be
        // installed before the ViewModel is constructed.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createTagReportsTheStoredNameAndRejectsADuplicate() = runTest(dispatcher) {
        val dao = FakeTagDao()
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())
        val results = mutableListOf<Boolean>()

        viewModel.createTag(" Work ") { results += it }
        advanceUntilIdle()
        assertEquals(listOf(true), results)
        assertEquals(listOf("Work" to 0), dao.stored())

        // The repository rejects the duplicate, so the caller must be told it was not created.
        viewModel.createTag("Work") { results += it }
        advanceUntilIdle()
        assertEquals(listOf(true, false), results)
        assertEquals(listOf("Work" to 0), dao.stored())
    }

    @Test
    fun aFailingCreateNeverReportsSuccess() = runTest(dispatcher) {
        val dao = FailingTagDao()
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())
        val results = mutableListOf<Boolean>()

        viewModel.createTag("Work") { results += it }
        advanceUntilIdle()

        assertEquals(emptyList<Boolean>(), results)
        // The insert really was attempted and really failed there, so the empty result list above
        // is the swallowed failure and not an unimplemented write path.
        assertEquals(listOf(Tag("Work", 0)), dao.insertAttempts)
    }

    @Test
    fun deleteTagNotifiesOnlyAfterTheWriteLanded() = runTest(dispatcher) {
        val dao = FakeTagDao(listOf(Tag("Work", 0), Tag("Play", 1)))
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())
        var deleted = 0

        viewModel.deleteTag("Work") { deleted++ }
        advanceUntilIdle()

        assertEquals(1, deleted)
        assertEquals(listOf("Play" to 0), dao.stored())
    }

    @Test
    fun aFailingDeleteNeverNotifiesAndDoesNotCrash() = runTest(dispatcher) {
        val dao = FailingTagDao(listOf(Tag("Work", 0)))
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())
        var deleted = 0

        viewModel.deleteTag("Work") { deleted++ }
        advanceUntilIdle()

        assertEquals(0, deleted)
        // The delete reached the DAO and failed there; the swallowed failure is what the missing
        // callback above pins.
        assertNotNull(dao.lastError)
    }

    @Test
    fun moveTagRewritesTheNeighbourOrder() = runTest(dispatcher) {
        val dao = FakeTagDao(listOf(Tag("Work", 0), Tag("Play", 1), Tag("Read", 2)))
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())

        viewModel.moveTag("Play", TagMoveDirection.PREVIOUS)
        advanceUntilIdle()

        assertEquals(listOf("Play", "Work", "Read"), dao.stored().map { it.first })
    }

    @Test
    fun aRejectedMoveWritesNothingAndDoesNotCrash() = runTest(dispatcher) {
        val dao = FakeTagDao(listOf(Tag("Work", 0), Tag("Play", 1)))
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())

        // "Work" is already first: the repository rejects the move before it opens its transaction.
        viewModel.moveTag("Work", TagMoveDirection.PREVIOUS)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Int>>(), dao.positionWrites)
        assertEquals(listOf("Work", "Play"), dao.stored().map { it.first })
    }

    @Test
    fun aFailingMoveIsSwallowed() = runTest(dispatcher) {
        val dao = FailingTagDao(listOf(Tag("Work", 0), Tag("Play", 1)))
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())

        viewModel.moveTag("Play", TagMoveDirection.PREVIOUS)
        advanceUntilIdle()

        // The reorder threw on its first position write; nothing may escape into the ViewModel's
        // scope, and the failure must not be reported as a move that happened.
        assertEquals(listOf("Play"), dao.failedPositionWrites.map { it.first })
    }

    @Test
    fun anUnknownTagNeverReachesTheDao() = runTest(dispatcher) {
        val dao = FailingTagDao(listOf(Tag("Work", 0)))
        val viewModel = CategoryViewModel(tagRepository = repository(dao), dependencies = dependencies())

        viewModel.moveTag("Missing", TagMoveDirection.NEXT)
        advanceUntilIdle()

        assertTrue(dao.failedPositionWrites.isEmpty())
        assertNull(dao.lastError)
    }

    private fun repository(dao: TagDao): TagRepository = TagRepository(
        tagDao = dao,
        applicationTagDao = NoApplicationTagDao,
        transactionRunner = { block -> block() },
    )

    private fun dependencies() = NeurhomeViewModelDependencies(
        neurhomeRepository = neurhomeRepository,
        favouritesRepository = application.favouritesRepository,
        startActivity = {},
        getWifiContext = { WifiContext.UNKNOWN },
        getPositionForLogging = { null },
        launcherApps = application.launcherApps,
        checkPermission = { false },
    )

    /** In-memory [TagDao] preserving insertion order, which is the only order the tests need. */
    private class FakeTagDao(initial: List<Tag> = emptyList()) : TagDao {
        private val rows = MutableStateFlow(initial)
        val positionWrites = mutableListOf<Pair<String, Int>>()

        override suspend fun insert(tag: Tag): Long {
            if (rows.value.any { it.name == tag.name }) return -1L
            rows.value = rows.value + tag
            return rows.value.size.toLong() - 1
        }

        override fun list(): Flow<List<Tag>> = rows

        override suspend fun updatePosition(name: String, position: Int) {
            positionWrites += name to position
            rows.value = rows.value.map { if (it.name == name) it.copy(position = position) else it }
        }

        override suspend fun delete(name: String) {
            rows.value = rows.value.filterNot { it.name == name }
        }

        fun stored(): List<Pair<String, Int>> = rows.value
            .sortedWith(compareBy({ it.position }, { it.name.lowercase() }))
            .map { it.name to it.position }
    }

    /** Every write throws once it has recorded the attempt. */
    private class FailingTagDao(initial: List<Tag> = emptyList()) : TagDao {
        private val rows = MutableStateFlow(initial)
        val insertAttempts = mutableListOf<Tag>()
        val failedPositionWrites = mutableListOf<Pair<String, Int>>()
        var lastError: Exception? = null

        private fun fail(): Nothing {
            val error = IllegalStateException("dao unavailable")
            lastError = error
            throw error
        }

        override suspend fun insert(tag: Tag): Long {
            insertAttempts += tag
            return fail()
        }

        override fun list(): Flow<List<Tag>> = rows

        override suspend fun updatePosition(name: String, position: Int) {
            failedPositionWrites += name to position
            fail()
        }

        override suspend fun delete(name: String) = fail()
    }

    /** The assignment table; the writes under test never insert an assignment. */
    private object NoApplicationTagDao : ApplicationTagDao {
        override suspend fun insert(assignment: ApplicationTag): Long = -1L
        override suspend fun delete(packageName: String, profile: Int, tagName: String) = Unit
        override suspend fun deleteForApplication(packageName: String, profile: Int) = Unit
        override suspend fun deleteForTag(tagName: String) = Unit
        override fun list(): Flow<List<ApplicationTag>> = flowOf(emptyList())
        override fun listForApplication(packageName: String, profile: Int): Flow<List<String>> =
            flowOf(emptyList())
    }
}
