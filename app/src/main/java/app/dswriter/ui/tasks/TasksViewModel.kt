package app.dswriter.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dswriter.domain.generation.GenerationTaskManager
import app.dswriter.domain.generation.GenerationTaskRepository
import app.dswriter.domain.generation.GenerationTaskSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TasksViewModel @Inject constructor(
    repository: GenerationTaskRepository,
    private val manager: GenerationTaskManager,
) : ViewModel() {
    val tasks: StateFlow<List<GenerationTaskSummary>> = repository.recentTasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun cancel(taskId: String) = manager.cancelTask(taskId)
}
