# Development Plan

This checklist is authoritative. Complete tasks in dependency order. Mark `[x]` only after the implementation exists and its required checks pass. Add newly discovered work to the appropriate phase instead of hiding it in unrelated changes.

## Documentation foundation

- [x] Create the scoped documentation set and documentation router
- [x] Establish the short repository session protocol
- [x] Make the pure-conversation invariant explicit and testable
- [x] Record initial architecture decisions
- [x] Verify document links, ownership, and language

## Phase 0 - Repository foundation

- [x] Initialize the single-module Android application
- [x] Configure the Gradle wrapper and version catalog
- [x] Configure Kotlin, Compose, Material 3, and Navigation Compose
- [x] Configure Hilt, Room, DataStore, OkHttp, coroutines, and serialization dependencies
- [x] Establish package conventions and a minimal Chinese UI shell
- [x] Add basic CI and local build commands
- [x] Pass unit tests, lint, and `assembleDebug`

## Phase 1 - Persistent settings and API security

- [x] Define the settings repository contract
- [x] Implement Android Keystore-backed API key encryption
- [x] Persist the configurable base URL separately from the key
- [x] Implement the Chinese API settings screen
- [x] Implement a non-streaming API connection test
- [x] Handle 401, 429, timeout, and network errors in Chinese
- [x] Audit logs and exports for credential disclosure
- [x] Add settings and security tests

## Phase 2 - Local conversation model

- [x] Implement Conversation, Message, Attachment, Usage, and GenerationTask schemas
- [x] Add required foreign keys and indexes
- [x] Implement conversation creation, rename, soft deletion, and trash behavior
- [x] Implement bounded conversation lists
- [x] Persist message history and input drafts
- [x] Export and validate the initial Room schema; add real migration tests from version 2 onward

## Phase 3 - DeepSeek streaming

- [x] Implement the centralized model registry for Flash, Pro, and Flash Vision
- [x] Implement immutable API request and response DTOs
- [x] Implement cancellable SSE transport and parser
- [x] Persist partial assistant output without per-token writes
- [x] Parse finish reasons, usage, errors, and supported reasoning output
- [x] Implement stop generation without deleting partial output
- [x] Prove the pure-conversation mapping with unit tests

## Phase 4 - Chat UI

- [x] Implement the Chinese conversation screen and paged lazy message list
- [x] Render streaming output incrementally with long-text safeguards
- [x] Persist drafts and expose stop, regenerate, copy, and text selection
- [x] Implement near-bottom auto-follow and the `回到底部` control
- [x] Add model selection and supported thinking presentation
- [x] Validate accessibility and long-text performance

## Phase 5 - Concurrent generation

- [x] Implement the application-scoped GenerationTaskManager
- [x] Limit running jobs to three and visibly queue the fourth
- [x] Isolate jobs with SupervisorJob and support per-task cancellation
- [x] Persist QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, and INTERRUPTED states
- [x] Implement unread completion state, blue indicators, and task center
- [x] Test concurrency limits, queue ordering, cancellation, and failure isolation

## Phase 6 - Background execution

- [x] Keep generation alive across navigation and Activity recreation
- [x] Implement a platform-compliant foreground service for active user work
- [x] Add Chinese running notifications and Android-version-specific permissions
- [x] Persist partial output and mark unfinished work INTERRUPTED after process death
- [x] Never silently retry or restart a potentially billable request
- [x] Validate behavior across supported Android versions

## Phase 7 - Context management

- [x] Implement branch-aware message ancestry
- [x] Build requests only from real messages on the selected branch
- [x] Implement estimated token accounting and recent-history retention
- [x] Implement safe trimming without synthetic messages or unsafe text truncation
- [x] Add a redacted debug request inspector
- [x] Test trimming, branch correctness, message fidelity, and pure conversation

## Phase 8 - Vision

- [x] Integrate Android Photo Picker and thumbnail preview
- [x] Normalize EXIF orientation and downsample without unnecessary full-size decoding
- [x] Validate attachment type and model capability
- [x] Offer an explicit one-tap switch to Flash Vision
- [x] Persist upload state and user-selected attachment metadata
- [x] Test image memory behavior and request mapping

## Phase 9 - Export and data safety

- [x] Implement readable Markdown and text export
- [x] Exclude API keys and internal secrets from every export
- [x] Implement soft deletion, trash, and recovery flows
- [x] Test exact Unicode readable export and document-provider failures

## Phase 10 - Performance hardening

- [x] Batch stream UI updates and debounce database persistence
- [x] Avoid quadratic string building and per-token Markdown reparsing
- [x] Use stable lazy-list keys, paging, and indexed queries
- [x] Profile large conversations and database access
- [x] Profile memory, images, and background battery use
- [x] Record and enforce agreed performance budgets

## Phase 11 - Release readiness

- [x] Pass unit, instrumentation, migration, lint, and release builds
- [x] Audit release logging, API-key security, and pure conversation
- [x] Audit Simplified Chinese text and accessibility
- [x] Verify export and recovery with production-like data
- [x] Complete release README and operator instructions

## Phase 12 - Conversation-first UI and Room v3

- [x] Remove the project layer from production code and documentation
- [x] Add explicit validated `1 -> 2 -> 3` and `2 -> 3` clearing migrations
- [x] Add atomic monotonic conversation numbering and bounded title search
- [x] Implement the drawer-based single chat host and refined Material 3 chat layout
- [x] Remove JSON backup/restore production code, UI, DTOs, and tests
- [x] Preserve readable Markdown/TXT export and conversation trash
- [x] Show one migration notice and clean orphaned private images
- [x] Verify pure conversation, JVM tests, API 35 instrumentation, lint, Debug, and Release builds

## Phase 13 - Long reasoning resilience

- [x] Remove the whole-call deadline from cancellable SSE generation while retaining bounded idle reads
- [x] Bound live reasoning snapshots without truncating persisted reasoning
- [x] Add collapsed status, long-reasoning pagination, selection, and Chinese accessibility text
- [x] Test deadline behavior, exact long-reasoning persistence, UI paging, and pure-conversation regression
- [x] Pass JVM, API 35 instrumentation, lint, Debug, and Release builds

## Phase 14 - Android Studio compatibility

- [x] Select an AGP release supported by the development Android Studio installation
- [x] Pin a KSP release compatible with AGP 8.13 and the current Kotlin compiler
- [x] Retain a Gradle wrapper that supports the available JDK 24 command-line environment
- [x] Verify Gradle configuration, JVM tests, lint, Debug, and Release builds

## Phase 15 - Personal signed APK distribution

- [x] Keep release signing material outside the repository
- [x] Configure optional workstation signing without breaking unsigned CI release builds
- [x] Build and verify the version 4 signed release APK
- [x] Record the APK digest and signing certificate fingerprint
- [ ] Back up the release keystore and protected signing properties offline

## Phase 16 - Self-hosted in-app updates (removed for the public release)

This phase built the private line's updater. The open-source 1.0.0 release deletes it entirely, so
the items below are historical and no longer describe shipping behaviour. See
`DECISIONS.md` for why. The delivered work was:

- [x] Deploy an isolated HTTPS update origin without changing the existing port 443 service
- [x] Publish a bounded version manifest and versioned signed APK
- [x] Add explicit Chinese update checking, download progress, and install guidance
- [x] Verify APK digest, package identity, increasing version, and pinned release certificate
- [x] Keep update metadata and traffic independent from API credentials and conversations
- [x] Pass JVM, API 35 instrumentation, lint, Debug, Release, public-download, and launch checks

## Phase 17 - Private-network local model support

- [x] Allow plain HTTP for private, loopback, and link-local addresses and reject it for public hosts
- [x] Resolve a configured local model alias without weakening the built-in DeepSeek models
- [x] Add the Chinese settings field, LAN warning, and local model picker entry
- [x] Build endpoint URLs from a tested joiner so a `/v1` prefix is preserved
- [x] Pin the public endpoint to HTTPS in the packaged network security config
- [x] Verify an end-to-end local generation against an OpenAI-compatible server
- [x] Pass JVM, API 35 instrumentation, lint, Debug, Release, and cold-start checks
- [x] Confirm the local model works from a real phone on the home WiFi network

## Phase 18 - Interface redesign

- [x] Define a complete color scheme, Chinese type scale, shape scale, and spacing tokens
- [x] Add a dark window theme so dark mode no longer mixes platform chrome with Compose
- [x] Add shared components for panels, status capsules, empty states, and page frames
- [x] Redesign the chat screen: header model chip, message layout, composer, back-to-bottom control
- [x] Replace the title-tap model menu with a grouped model picker sheet
- [x] Redesign the drawer, settings, task center, and data screens onto the shared system
- [x] Remove the duplicated API-key label and duplicate export title found during verification
- [x] Pass JVM tests, API 35 instrumentation, lint, Debug, Release, and end-to-end generation checks

## Phase 19 - Measured local model tuning

- [x] Add per-model request tuning that official DeepSeek models leave entirely unset
- [x] Cap local single-pass output at the measured repetition threshold instead of 4096 tokens
- [x] Apply the measured sampling pair for stable long-form Chinese text
- [x] Raise the local context window to match the server's `-c` and expose it as a setting
- [x] Validate the context-window setting and report a Chinese error for an unusable value
- [x] Assert the serialized request body for a local model and the untouched official body
- [x] Record why the measured rolling-summary recommendation is refused under the invariant
- [x] Pass JVM tests, API 35 instrumentation, lint, Debug, and Release checks
- [x] Provide an explicit, user-editable recap path for manuscripts beyond the context window
- [x] Condense the oldest messages, and report what did not fit so a second pass is possible
- [x] Keep the recap request to one real user message with no system or developer role

## Phase 20 - Optional writing assistance

- [x] Amend the invariant so added text is allowed only through off-by-default, visible switches
- [x] Add a 写作辅助 settings section that states each switch's on and off behaviour
- [x] Persist each switch immediately so the label and the behaviour cannot disagree
- [x] Show a prominent in-chat prompt when a conversation nears its context limit
- [x] Make the prompt offer the recap action rather than sending anything by itself
- [x] Assert that every assistance flag is off by default on a fresh install
- [x] Pass JVM tests, API 35 instrumentation, lint, Debug, and Release checks

## Phase 21 - Open-source readiness

- [x] Audit the tree for credentials, private keys, signing material, and personal identifiers
- [x] Confirm local-only files stay ignored and extend `.gitignore` for signing and build output
- [x] Add an MIT license
- [x] Replace personal host and path details in the README with a generic signing setup
- [x] Add server-side model discovery so no alias has to be known in advance
- [x] Document that the model host needs no plugin, patch, or addon
- [x] Pass JVM tests, API 35 instrumentation, lint, Debug, and Release checks

## Phase 22 - Public 1.0.0 release

- [x] Release from a separate source tree so the private line is untouched
- [x] Delete the in-app updater, its permission, its FileProvider, and its update strings
- [x] Delete the update manifest and the deployment files that pointed at a personal host
- [x] Assert in an instrumentation test that the removed permission and provider are absent
- [x] Start with an empty API address and teach the accepted address shapes in the settings screen
- [x] Report an empty address as "not filled in yet" rather than as an invalid URL
- [x] Re-audit the tree for any remaining personal identifier or contact point
- [x] Pass JVM tests, API 35 instrumentation, lint, Debug, and Release checks

## Phase 23 - Vendor-neutral provider list

- [x] Replace the built-in model table with user-defined providers
- [x] Store several providers with their own address, key, model, and parameters
- [x] Switch the active provider from the chat header in one tap
- [x] Move model choice from the conversation to the application
- [x] Remove the vendor hostname from the network security configuration
- [x] Remove the vendor name from export labels, role labels, and notifications
- [x] Never sign a public build with anyone else's private release key
- [x] Trim settings copy to one short line per field and one line per switch
- [x] Pass JVM tests, API 35 instrumentation, lint, Debug, and Release checks

## Phase 24 - Reachable form actions and a short default form

- [x] Report that an internal `http://` address could not be saved and its model list could not be fetched
- [x] Reproduce on an API 35 emulator: the save, test, and discovery buttons were absent from the hierarchy
- [x] Root-cause it to the form outgrowing the screen instead of to the address validator
- [x] Pin the save, test, and cancel actions in a scaffold footer outside the scrolling area
- [x] Move the context window, output cap, temperature, and vision flag into a collapsed advanced section
- [x] Keep plain-HTTP private addresses accepted and keep public addresses HTTPS-only
- [x] Assert that save is clickable without scrolling and that the advanced fields start hidden
- [x] Let the connection test run without a key, matching model discovery
- [x] Pass JVM tests, API 35 instrumentation, lint, Debug, and Release checks
