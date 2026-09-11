# Architecture

The repository uses one Android `app` module. Production code is organized under `app.dswriter` by responsibility: `data/local`, `data/remote`, `data/repository`, `data/update`, `domain/model`, `domain/context`, `domain/generation`, `domain/update`, `ui/chat`, `ui/tasks`, `ui/settings`, `ui/data`, `security`, and `service`.

Dependencies flow from Compose UI to domain/repository contracts and then to local or remote implementations. UI code does not construct API messages. One domain request-snapshot path enforces [PURE_CONVERSATION.md](PURE_CONVERSATION.md).

Room is the durable source for conversations, messages, attachments, usage, generation tasks, and the app-state counter. DataStore owns ordinary settings. Android Keystore protects the API key. The application-scoped `GenerationTaskManager` owns concurrency and cannot be tied to a chat ViewModel lifetime.

One chat destination hosts all conversation switching. Drawer selection changes the selected conversation inside that host instead of adding a destination per conversation. Settings, tasks, and data screens are separate destinations and return to the existing chat host.

Readable export DTOs contain only conversations and real visible message bodies. The Room export service reads one transactionally consistent snapshot, and the Android document edge only writes user-selected UTF-8 documents. No JSON import or restore boundary exists.

Conversation data, model settings, and the optional recap all go through one request-construction path, so there is no second boundary that could construct an API request. The release build has no update client, no service origin, and no package-install path; it contacts only the API address the user configures.
