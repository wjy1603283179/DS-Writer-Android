package app.dswriter.domain.generation

import app.dswriter.data.local.db.MessageGenerationStatus
import app.dswriter.data.remote.ApiException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ConversationGenerationState(
    val taskId: String,
    val assistantMessageId: String,
    val status: GenerationTaskStatus,
    val progress: GenerationProgress? = null,
    val failure: Throwable? = null,
)

private data class PendingGeneration(
    val taskId: String,
    val submission: GenerationTaskSubmission,
    val apiKey: String,
)

@Singleton
class GenerationTaskManager @Inject constructor(
    private val runner: GenerationRunner,
    private val taskRepository: GenerationTaskRepository,
    private val outputStore: GenerationOutputStore,
    private val foregroundController: GenerationForegroundController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val submissionMutex = Mutex()
    private val lock = Any()
    private val queue = ArrayDeque<PendingGeneration>()
    private val jobs = mutableMapOf<String, Job>()
    private val taskConversations = mutableMapOf<String, String>()
    private val visibleConversations = mutableSetOf<String>()
    private val _states = MutableStateFlow<Map<String, ConversationGenerationState>>(emptyMap())
    val states: StateFlow<Map<String, ConversationGenerationState>> = _states.asStateFlow()
    private val _latestSnapshots = MutableStateFlow<Map<String, GenerationRequestSnapshot>>(emptyMap())
    val latestSnapshots: StateFlow<Map<String, GenerationRequestSnapshot>> = _latestSnapshots.asStateFlow()
    private val recovery = scope.async { taskRepository.recoverAfterProcessStop() }

    suspend fun submit(submission: GenerationTaskSubmission, apiKey: String): Boolean =
        submissionMutex.withLock {
            recovery.await()
            if (states.value[submission.conversationId]?.status in ACTIVE_STATUSES) return@withLock false
            val wasIdle = states.value.values.none { it.status in ACTIVE_STATUSES }
            val taskId = taskRepository.createQueued(submission)
            val pending = PendingGeneration(taskId, submission, apiKey)
            synchronized(lock) {
                queue.addLast(pending)
                taskConversations[taskId] = submission.conversationId
            }
            _states.update {
                it + (submission.conversationId to pending.state(GenerationTaskStatus.QUEUED))
            }
            _latestSnapshots.update { it + (submission.conversationId to submission.snapshot) }
            if (wasIdle) foregroundController.start()
            dispatch()
            true
        }

    fun cancelConversation(conversationId: String) {
        val state = states.value[conversationId] ?: return
        val queued = synchronized(lock) {
            val pending = queue.firstOrNull { it.taskId == state.taskId }
            if (pending != null) queue.remove(pending)
            pending
        }
        if (queued != null) {
            _states.update { it - conversationId }
            scope.launch {
                outputStore.persist(
                    queued.submission.assistantMessageId,
                    "",
                    "",
                    MessageGenerationStatus.CANCELLED,
                )
                taskRepository.markCancelled(queued.taskId)
                synchronized(lock) { taskConversations.remove(queued.taskId) }
                dispatch()
            }
        } else {
            synchronized(lock) { jobs[state.taskId] }?.cancel()
        }
    }

    fun cancelTask(taskId: String) {
        val conversationId = synchronized(lock) { taskConversations[taskId] } ?: return
        cancelConversation(conversationId)
    }

    fun cancelAll() {
        states.value.filterValues { it.status in ACTIVE_STATUSES }.keys.forEach(::cancelConversation)
    }

    fun setConversationVisible(conversationId: String, visible: Boolean) {
        synchronized(lock) {
            if (visible) visibleConversations.add(conversationId) else visibleConversations.remove(conversationId)
        }
        if (visible) scope.launch { taskRepository.markConversationRead(conversationId) }
    }

    private fun dispatch() {
        val jobsToStart = mutableListOf<Job>()
        synchronized(lock) {
            while (jobs.size < MAX_RUNNING && queue.isNotEmpty()) {
                val pending = queue.removeFirst()
                val job = scope.launch(start = CoroutineStart.LAZY) { run(pending) }
                jobs[pending.taskId] = job
                jobsToStart += job
            }
        }
        jobsToStart.forEach(Job::start)
    }

    private suspend fun run(pending: PendingGeneration) {
        val conversationId = pending.submission.conversationId
        try {
            taskRepository.markRunning(pending.taskId)
            _states.update { it + (conversationId to pending.state(GenerationTaskStatus.RUNNING)) }
            var finalProgress: GenerationProgress? = null
            runner.execute(
                pending.submission.snapshot,
                pending.apiKey,
                pending.submission.assistantMessageId,
            ).collect { progress ->
                finalProgress = progress
                _states.update {
                    it + (conversationId to pending.state(GenerationTaskStatus.RUNNING, progress))
                }
            }
            val unread = synchronized(lock) { conversationId !in visibleConversations }
            taskRepository.markCompleted(pending.taskId, finalProgress?.usage, unread)
            _states.update { it - conversationId }
        } catch (_: CancellationException) {
            withContext(NonCancellable) { taskRepository.markCancelled(pending.taskId) }
            _states.update { it - conversationId }
        } catch (failure: Exception) {
            taskRepository.markFailed(pending.taskId, failure.errorCode())
            _states.update {
                it + (
                    conversationId to pending.state(
                        status = GenerationTaskStatus.FAILED,
                        failure = failure,
                    )
                    )
            }
        } finally {
            synchronized(lock) {
                jobs.remove(pending.taskId)
                taskConversations.remove(pending.taskId)
            }
            dispatch()
        }
    }

    private fun PendingGeneration.state(
        status: GenerationTaskStatus,
        progress: GenerationProgress? = null,
        failure: Throwable? = null,
    ) = ConversationGenerationState(
        taskId = taskId,
        assistantMessageId = submission.assistantMessageId,
        status = status,
        progress = progress,
        failure = failure,
    )

    private fun Throwable.errorCode(): String =
        (this as? ApiException)?.failure?.name ?: this::class.simpleName ?: "UNKNOWN"

    companion object {
        const val MAX_RUNNING = 3
        private val ACTIVE_STATUSES = setOf(GenerationTaskStatus.QUEUED, GenerationTaskStatus.RUNNING)
    }
}
