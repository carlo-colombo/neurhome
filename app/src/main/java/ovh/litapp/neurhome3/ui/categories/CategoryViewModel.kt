package ovh.litapp.neurhome3.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.models.Tag
import ovh.litapp.neurhome3.data.repositories.TagRepository

class CategoryViewModel(
    private val tagRepository: TagRepository,
    private val neurhomeRepository: NeurhomeRepository,
) : ViewModel() {
    val tags: StateFlow<List<Tag>> = tagRepository.tags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun createTag(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(tagRepository.createTag(name))
        }
    }

    fun applicationsForTag(name: String?): Flow<List<Application>> =
        neurhomeRepository.applicationsForTag(name)
}
