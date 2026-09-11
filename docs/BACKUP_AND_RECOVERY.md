# Export and Recovery

Markdown and TXT are the only user-directed export formats. Both are organized directly by conversation and preserve exact Unicode user/assistant message bodies. Structural headings are export formatting and are never API messages.

Exports exclude API keys, ciphertext, Authorization data, reasoning content, drafts, request snapshots, usage, task metadata, attachment internals, and private paths. JSON backup, JSON restore, import DTOs, validators, and restore entry points are intentionally absent.

Conversation deletion is soft by default. The data screen lists trashed conversations and supports explicit restore. Permanent deletion requires confirmation, is blocked while related work is queued or running, cascades database children, and then removes referenced private image files.

Android cloud backup and device-to-device transfer remain disabled. The v3 migration intentionally resets legacy local content while retaining settings stored outside Room; [DATA_MODEL.md](DATA_MODEL.md) owns that policy.
