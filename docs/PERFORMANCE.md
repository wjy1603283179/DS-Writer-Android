# Performance

## Invariants

Never write Room once per token, reparse all Markdown once per token, rebuild complete context during Compose recomposition, decode large images at full resolution without need, load all messages from all conversations, create an OkHttpClient per request, or build large responses through repeated immutable string concatenation.

## Streaming pipeline

Use: network chunks -> mutable accumulator -> throttled UI snapshots -> debounced database writes -> mandatory final write. Cancellation, failure, and service shutdown also flush the latest safe partial content.

The streaming executor uses mutable `StringBuilder` accumulators, limits UI snapshots to at most once per 100 ms, and limits ordinary partial Room writes to at most once per 300 ms. After reasoning exceeds 32,000 UTF-16 characters, intermediate full-state persistence relaxes to once per two seconds to avoid quadratic copying; completion, cancellation, and failure still perform an immediate full write.

Live reasoning state is bounded to a 4,000-character head and 8,000-character tail after it grows beyond that window. This is a UI-only preview with a visible disclosure; the complete reasoning remains in the accumulator and Room. Expanded stored reasoning is rendered in surrogate-safe 6,000-character pages instead of one unbounded Compose `Text` layout.

A 3,000 one-character-chunk JVM pressure test enforces no more than 61 UI progress publications and 11 ordinary/terminal database writes when chunks arrive one millisecond apart. A separate long-reasoning test verifies that hundreds of thousands of characters persist exactly while every live reasoning snapshot remains bounded. Message rendering is plain Compose `Text`; there is no Markdown parser to re-run per chunk.

Use one shared, configured OkHttpClient. Keep parsing and persistence off the main thread. Bound buffers and avoid copying the whole response more frequently than the UI update cadence requires.

## Lists, database, and images

Use LazyColumn stable keys and paged/indexed queries. Observe only the active conversation window and incremental changes. Downsample images to model/display needs, normalize orientation once, and release large bitmaps promptly.

Image preparation first decodes bounds, selects a power-of-two sample, applies EXIF orientation once, scales the longest edge to at most 2,048 pixels, and writes an app-private JPEG at quality 88. Temporary source and transformed bitmaps are recycled after encoding. Conversation previews perform a separate sampled decode targeting 256 pixels instead of decoding the prepared image at full size. Prepared images are capped at 24 MiB; request-context estimation includes both tiled dimensions and compressed bytes.

Conversation, task, and safety lists use stable entity IDs as lazy-list keys. The drawer observes a bounded recent list and title search returns at most 100 rows. The active chat observes 50 recent messages and explicit older-message loading is capped at 200. Message page reads use the `(conversationId, sequence)` index; selected-branch traversal is capped before results reach Compose.

## Enforced budgets and baseline

Budgets are regression guardrails, not claims about every physical device. The instrumented baseline was measured on the Android 15 / API 35 `Medium_Phone_API_35` x86_64 emulator with a debug build on 2026-09-01.

| Workload | Observed baseline | Enforced ceiling |
| --- | ---: | ---: |
| 20 indexed reads of 50 messages from a 10,000-message conversation | 37 ms | 1,000 ms |
| 3 selected-branch reads capped at 200 messages | 27 ms | 2,000 ms |
| Selected-branch count across 10,000 messages | 5 ms | 1,000 ms |
| Prepare a 3,200 x 1,800 JPEG | 65 ms | 5,000 ms |
| Retained PSS delta after that image preparation | 596 KiB | 96 MiB |

The image test also requires a longest output edge of at most 2,048 pixels; the baseline output was 2,048 x 1,152. Generation runs at most three remote calls concurrently. The foreground service starts once when task state changes from idle to active, remains visible while work is queued or running, and stops after the last active task. The app requests no battery-optimization exemption and has no automatic background retry.

## Validation

Profile startup, scrolling, streaming, memory, database queries, image preparation, and background battery use with production-like long conversations. Record device, message count, text size, image sizes, build type, and observed regressions with any accepted performance budget. Re-measure and deliberately revise both the test and this document when a ceiling changes.
