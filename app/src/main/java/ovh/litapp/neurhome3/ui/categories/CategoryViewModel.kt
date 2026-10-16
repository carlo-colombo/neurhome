package ovh.litapp.neurhome3.ui.categories

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.models.Tag
import ovh.litapp.neurhome3.data.repositories.TagMoveDirection
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel
import ovh.litapp.neurhome3.ui.NeurhomeViewModelDependencies

private const val TAG = "CategoryViewModel"

class CategoryViewModel(
    private val tagRepository: TagRepository,
    dependencies: NeurhomeViewModelDependencies,
) : NeurhomeViewModel(dependencies) {
    val tags: StateFlow<List<Tag>> = tagRepository.tags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun createTag(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val created = runTagWrite("create tag '$name'") { tagRepository.createTag(name) }
                ?: return@launch
            onResult(created)
        }
    }

    fun deleteTag(name: String, onResult: () -> Unit = {}) {
        viewModelScope.launch {
            runTagWrite("delete tag '$name'") { tagRepository.deleteTag(name) } ?: return@launch
            onResult()
        }
    }

    fun moveTag(name: String, direction: TagMoveDirection) {
        viewModelScope.launch {
            // The reorder rewrites every tag position inside one transaction, so it must not be
            // cancelled halfway: NonCancellable lets a torn-down viewModelScope finish the
            // reorder instead of leaving a partial position order behind.
            val moved = runTagWrite("move tag '$name'") {
                withContext(NonCancellable) { tagRepository.moveTag(name, direction) }
            } ?: return@launch
            if (!moved) Log.d(TAG, "Reorder rejected for tag '$name' (direction=$direction)")
        }
    }

    fun applicationsForTag(name: String?): Flow<List<Application>> =
        neurhomeRepository.applicationsForTag(name)

    /**
     * Runs one tag write, logging and swallowing a failure so a rejected write cannot crash the
     * app or stay invisible. Cancellation is rethrown so scope teardown stays cooperative.
     * Returns null when the write failed.
     */
    private suspend fun <T> runTagWrite(operation: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tag write failed: $operation", e)
            null
        }
}
