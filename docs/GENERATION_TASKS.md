# Generation Tasks

## User experience

Generation is application-scoped and survives navigation. At most three remote generations are RUNNING. Additional submissions remain QUEUED and visible in submission order. One failure never cancels another task.

## State model

Persist these terminal and non-terminal states: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, and `INTERRUPTED`. Legal transitions are QUEUED -> RUNNING/CANCELLED; RUNNING -> COMPLETED/FAILED/CANCELLED/INTERRUPTED. Terminal tasks do not restart automatically.

Each submission creates an immutable, non-secret request snapshot and durable task row before network work begins. A controlled queue or semaphore enforces the limit, while a `SupervisorJob` isolates failures. Cancellation targets the specific coroutine and underlying HTTP call.

The implemented `GenerationTaskManager` owns a FIFO in-memory execution queue and persists `QUEUED` before dispatch. It starts no more than three jobs, starts foreground execution once per idle-to-active period, retains API credentials only in memory, and stores the exact already-constructed pure-conversation snapshot without credentials. The task center observes durable rows and exposes Chinese state and cancellation actions.

## Completion and unread state

If output completes while the conversation is not actively visible, mark it unread. Chinese UI shows a blue dot in the conversation drawer and task center. Showing the completed response clears the unread flag transactionally; merely starting the app does not.

Partial assistant output is retained on failure, cancellation, or interruption and clearly labeled. Retrying is an explicit new, potentially billable request with its own snapshot and task.

## Recovery

On startup, persisted QUEUED tasks may be re-evaluated only if no paid request began. Persisted RUNNING tasks become INTERRUPTED because an SSE call cannot be safely resumed. Never silently resubmit them.

Manager initialization transactionally changes leftover RUNNING rows and their streaming assistant messages to INTERRUPTED before accepting a new submission. Leftover QUEUED rows and placeholders become CANCELLED. Neither state is automatically submitted again; retrying always requires a new explicit user action.
