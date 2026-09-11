# Android Background Execution

## Lifecycle boundary

A user-started SSE generation must not belong to an Activity, Composable, navigation entry, or Chat ViewModel. The application-scoped manager owns in-process jobs, while persisted task state supports recreation and process-death diagnosis.

## Foreground execution

Use a platform-compliant foreground service when active, user-visible generation must continue beyond ordinary foreground lifetime. Start it only from an allowed user action, declare the correct service type and permissions for supported Android versions, and stop it when no task requires it.

The ongoing notification is Simplified Chinese, for example `DS Writer · 2 个任务正在生成`, and opens the task center. Notification channels, permission prompts, cancellation behavior, and failure text are also Chinese.

WorkManager is suitable for deferrable maintenance, not real-time SSE transport. The current product schedules no automatic export or remote request work.

The implementation uses a non-exported `dataSync` foreground service with `START_NOT_STICKY`. It starts only when an explicit send or regenerate action changes the manager from no active tasks to at least one QUEUED or RUNNING task; later queued submissions do not repeat the start call. It remains active while the application-scoped manager has QUEUED or RUNNING tasks and stops when that count reaches zero. Android 15 foreground-service timeout cancels active jobs and preserves their partial output.

The manifest declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and `POST_NOTIFICATIONS`. Android 12/12L does not request a runtime notification permission; Android 13 through 15 requests it from the user action before submission. Refusing notification permission does not remove the in-app task center or permit hidden retries. The Chinese ongoing notification reports running and queued counts and opens the task center.

## Process death

Debounced writes preserve partial output. At next startup, any task left RUNNING becomes INTERRUPTED and the Chinese UI explains that the system stopped it. The app must not silently restart a request that may already have incurred cost.

Validate foreground-service restrictions, notification permission behavior, battery restrictions, and process recreation on each supported Android API family.

Compatibility tests cover the API 31/32 and API 33-35 notification-permission branches, declared service type and permissions, non-exported service configuration, Chinese notification formatting, Android 15 timeout handling, and API 35 foreground-service startup. The service requests no battery-optimization exemption and uses no background-start path.
