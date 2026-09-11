# Data Model

## Room version 3

- `AppStateEntity`: singleton row containing `nextConversationNumber` and the one-time migration notice flag.
- `ConversationEntity`: ID, user-controlled title, selected model, branch head, draft, timestamps, and optional soft-deletion time.
- `MessageEntity`: exact user or assistant content, optional parent, sequence, reasoning stored separately, and generation status.
- `AttachmentEntity`: a user-selected prepared image owned by a message.
- `GenerationTaskEntity`: conversation ownership, immutable non-secret request snapshot, queue/state timestamps, error class, and unread result state.
- `UsageEntity`: authoritative token usage owned by one completed task.

There is no project entity and no project foreign key.

Conversations use `(deletedAt, updatedAt)` and branch-head indexes. Title search matches only active conversation titles and returns at most 100 rows. Recent conversations are ordered by `updatedAt DESC` and bounded. Messages use unique `(conversationId, sequence)` and parent indexes. Tasks use queue/state and conversation/unread indexes.

Creation runs in one Room transaction: ensure the singleton state row, read the next number, increment it, and insert the conversation. Rename, soft deletion, restoration, and permanent deletion never decrement the counter or reuse a number.

`MIGRATION_2_3` explicitly drops all legacy project, conversation, message, attachment, task, and usage data and creates the v3 schema with the counter reset to 1. It sets a one-time migration notice. Settings and encrypted API credentials live outside Room and are preserved. The notice consumption path removes orphaned files from the app-private attachment directory before clearing the flag. Both `1 -> 2 -> 3` and `2 -> 3` are tested; destructive fallback is forbidden.

Soft-deleted conversations remain recoverable until explicit permanent deletion. Permanent deletion is blocked while the conversation has queued or running work and removes referenced private images after the durable database deletion succeeds.
