# Testing

## Test layers

Use JVM unit tests for pure request mapping, context selection, model capabilities, SSE parsing, state machines, repositories, and error mapping. Use Room/instrumentation tests for migrations and transactional behavior. Use Compose tests for critical Chinese UI flows and accessibility. Use integration fakes rather than paid live API calls in routine CI.

## Required invariant test

Given stored messages:

1. user: `你好`
2. assistant: `你好，请问有什么可以帮你？`
3. user: `继续`

the remote request contains only the real messages selected by the context window, in order and byte-for-byte equivalent at the string level. It contains no system/developer role, hidden prefix or suffix, summary, conversation title, legacy project metadata, or rewriting.

Also test trimming, branches, Unicode, explicit attachments, SSE fragmentation and `[DONE]`, usage/reasoning chunks, cancellation of the HTTP call, maximum three running tasks, fourth-task queuing, failure isolation, unread completion, encryption boundaries, and every Room migration.

Long-reasoning regression coverage uses a deliberately tiny whole-call deadline to prove that SSE overrides it, verifies exact terminal persistence for hundreds of thousands of reasoning characters in both the executor and Room, bounds every live reasoning snapshot, and exercises collapsed/expanded 6,000-character UI paging on API 35. These tests never send reasoning back in a request.

Vision tests inspect the final serialized content array, exact user text, explicit attachment ownership, and pre-network rejection for non-vision models. Android tests verify decoded-content validation, EXIF rotation, bounded output dimensions, app-private persistence, Room attachment association, thumbnail UI, and the explicit Flash Vision switch. They must not use a paid API call.

Export tests verify exact Unicode user/assistant bodies, conversation-only structure, and exclusion of keys, reasoning, private paths, and runtime metadata. Production and tests must contain no JSON backup/restore entry point. Trash tests cover conversation recovery and confirmed permanent deletion.

Room instrumentation validates a fresh v3 schema plus explicit `1 -> 2 -> 3` and `2 -> 3` migrations. Both paths clear legacy content, create the singleton counter and one-time notice, remove the legacy project table, and never use destructive fallback. Repository tests cover concurrent numbering, no reuse, title-only search, restore, and deletion.

Performance regression tests include a 3,000-chunk JVM stream test and API 35 instrumentation workloads for a 10,000-message indexed conversation and a 3,200 x 1,800 image. The executable ceilings and the measured debug-emulator baseline are owned by [PERFORMANCE.md](PERFORMANCE.md). Background tests also verify that the manifest requests no battery-optimization exemption. These tests use local fixtures and never make a remote model request.

This build has no update client to test. Release verification builds a signed APK, inspects its package identity, version, SDK levels, permissions, signer digest, and shrunk resources, and performs a cold-start smoke test. Routine automated tests never contact a paid model endpoint.

## Phase 0 commands

On Windows:

```powershell
.\gradlew.bat --version
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

CI runs the equivalent Gradle tasks on JDK 17 and Android SDK 35. Instrumentation tests require an emulator/device and are mandatory once Android-dependent behavior exists.

The repository-level Gradle daemon uses the Windows native GBK charset so worker argument files can represent the current Chinese workspace path. Kotlin sources and XML resources remain UTF-8. Keep this compatibility setting unless the repository moves to an ASCII-only path or the Gradle/JDK worker behavior is revalidated.

Do not mark a development-plan task complete because code compiles alone; run the checks appropriate to its behavior and inspect failures before updating the checklist.

## Release gate

Run `testDebugUnitTest`, the complete `connectedDebugAndroidTest` suite on API 35 and broader API 31-35 coverage as available, `lintDebug`, `assembleDebug`, and `assembleRelease`. Then inspect the packaged release manifest, R8 mapping, shrunk resources, application ID/SDK/version values, permissions, logging surface, credentials boundary, and pure-conversation serialization. The root [README](../README.md) owns operator, signing, installation, and final smoke-test instructions.
