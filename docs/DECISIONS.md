# Architecture Decision Log

Use this file only for important, non-obvious decisions. Progress is tracked in `DEVELOPMENT_PLAN.md`.

## 2026-08-31 - Pure conversation is a hard invariant

**Decision:** Remote messages contain only real selected user/assistant messages and explicit user-selected attachments. No hidden prompts or synthetic summaries are allowed.

**Reason:** DS Writer is a transparent client and must preserve user intent exactly.

**Consequences:** Request construction is isolated and heavily tested. Metadata and summaries remain separate local data unless a future explicit, visible attachment flow is designed.

UI-only strings and assistant reasoning are also excluded from request messages. Production UI code may not build API DTOs or concatenate instructions; it submits exact stored branch messages through the single snapshot factory.

## 2026-08-31 - Start with one Android application module

**Decision:** Use a pragmatic single `app` module with package-level boundaries.

**Reason:** The empty repository does not yet justify module overhead.

**Consequences:** Dependency direction is documented and packages may become modules only when measured build or ownership needs support it.

## 2026-08-31 - Application identity and Android baseline

**Decision:** Use `DS Writer`, namespace/application ID `app.dswriter`, minSdk 31, compileSdk 35, and targetSdk 35.

**Reason:** The chosen Android 12+ floor reduces legacy background and platform compatibility branches.

**Consequences:** Android 11 and older devices are unsupported. User-facing copy remains Simplified Chinese while `DS Writer` is treated as a brand name.

## 2026-08-31 - Limit concurrent remote generations to three

**Decision:** An application-scoped manager runs at most three generations and queues later submissions.

**Reason:** This balances multitasking, resource use, rate limits, and user visibility.

**Consequences:** Task state and immutable request snapshots must be durable, and queue/concurrency behavior must be tested.

## 2026-08-31 - Establish documentation and Phase 0 first

**Decision:** The first delivery creates the complete documentation system and a buildable shell, without premature business entities or API clients.

**Reason:** Future sessions need durable source-of-truth documents and a verified build foundation.

**Consequences:** Phase 1 and later remain unchecked until implemented and tested.

## 2026-09-01 - Retired: pin an intermediate build baseline around the old IDE

**Status:** Superseded by "Raise the build baseline to the upgraded Android Studio" below. Preserved as history.

**Decision:** Use AGP 8.8.0, Gradle 8.14.3, Kotlin 2.1.21, KSP 2.1.21-2.0.2, Compose BOM 2025.08.01, Build Tools 35.0.0, and JVM bytecode target 17. Pin AndroidX and OkHttp releases that remain compatible with compileSdk 35.

**Reason:** The installed Android Studio supports Android Gradle Plugin versions through 8.8.0, while Gradle 8.14.3 can run on the available JDK 24. Kotlin 2.1.21 remains within the bytecode and metadata range supported by the R8 version bundled with AGP 8.8, and KSP 2.1.21-2.0.2 matches that compiler line. Newer KSP releases call AGP APIs unavailable in 8.8, while Kotlin 2.3 produces unsupported-metadata warnings during release shrinking. The remaining versions support the installed API and Build Tools 35 environment. Compose BOM 2026.08.00 and its contemporary dependencies require compileSdk 37 and AGP 9.1, so they cannot satisfy the selected platform baseline.

**Consequences:** Android Studio can sync the project without its unsupported-AGP preflight error. The newer Gradle wrapper is retained for JDK 24 command-line use. Versions live in the catalog and are upgraded deliberately with full build verification.

**Why retired:** This baseline existed only to satisfy the obsolete Android Studio installation at `D:\Program Files (x86)\AndroidStudio`. After Android Studio was upgraded outside the project, the constraint disappeared and the pinned versions became an unnecessary downgrade. The historical rationale is kept rather than rewritten so the intermediate workaround remains auditable.

## 2026-09-01 - Test API settings without generation

**Decision:** Validate the configured base URL and API key with authenticated `GET /models` rather than a chat completion.

**Reason:** The models endpoint verifies connectivity and authorization without sending conversation content, inventing a prompt, or creating a potentially billable generation.

**Consequences:** Connection-test success proves endpoint authentication, not that every model can complete a request. Model-specific failures remain part of generation error handling.

## 2026-09-01 - Use UUID identities and bounded message windows

**Decision:** Persist entity IDs as UUID strings and load conversation messages in bounded sequence windows rather than observing an entire history.

**Reason:** UUIDs are portable across future backup/import workflows, and bounded reads protect long-conversation memory use.

**Consequences:** `(conversationId, sequence)` is unique and indexed. Version 1 exports its schema and validates it directly; real migration paths begin when version 2 is introduced.

## 2026-09-01 - Keep reasoning separate and throttle stream persistence

**Decision:** Store DeepSeek reasoning in a dedicated message column and accumulate stream chunks in memory, publishing UI snapshots at 50 ms and ordinary database snapshots at 300 ms intervals.

**Reason:** Reasoning is not assistant answer text and must not be silently inserted into later conversation messages. Throttling avoids per-token recomposition and Room writes.

**Consequences:** Database version 2 adds `reasoningContent` through a tested migration. Terminal completion, cancellation, and failure bypass the debounce and always flush partial output.

## 2026-09-01 - Use a visible non-restarting foreground service

**Decision:** Active generation uses a non-exported `dataSync` foreground service with `START_NOT_STICKY`; Android 13+ requests notification permission from the submission action.

**Reason:** User-started SSE work must remain visible and survive navigation without creating an automatic retry mechanism.

**Consequences:** The notification opens the task center, Android 15 timeouts cancel active work, leftover RUNNING tasks become INTERRUPTED, and leftover QUEUED tasks become CANCELLED. No process or service restart resubmits a request.

## 2026-09-01 - Use conservative estimated context selection

**Decision:** Use a UTF-8-based conservative estimate with an app-side 64,000-token request guardrail and an 8,000-token output reserve until verified model-specific limits are available.

**Reason:** The client needs deterministic bounded requests without pretending that an estimate is authoritative or fabricating context.

**Consequences:** The current user message is always complete, recent complete exchanges are retained, older whole messages are removed, and authoritative usage remains the API response. The debug inspector exposes the exact resulting snapshot without credentials.

## 2026-09-01 - Normalize explicit vision input into private immutable copies

**Decision:** Photo Picker input is decoded by content, EXIF-normalized, downsampled to a 2,048-pixel longest edge, encoded as a private JPEG, and bound to the exact submitted user message. Phase 8 supports one selected image per new message.

**Reason:** A durable normalized copy avoids broad storage permission, unstable external URI access, orientation mismatches, and full-resolution request memory costs while preserving explicit user control.

**Consequences:** GIF input is rejected rather than silently flattened. A non-vision model is never switched automatically; the user must choose the visible Flash Vision action. Request snapshots hold immutable private references, and only the remote edge encodes those files as base64. No filename, UI label, processing metadata, or hidden image description becomes conversation content.

## 2026-09-01 - Retired: metadata-only JSON backup

**Decision:** Backup schema version 1 is a strict, bounded JSON document. It includes relational records and sanitized request snapshots but excludes credentials, settings internals, private paths, and image binaries. Every restore receives fresh UUIDs and is committed in one transaction.

**Reason:** Metadata-only JSON remains inspectable and portable without exposing app-private files. Full remapping makes repeat imports deterministic and avoids destructive conflict resolution.

**Consequences:** This decision is superseded by the conversation-first v3 decision below. Its production entry points, DTOs, validators, and tests have been removed.

## 2026-09-01 - Enforce measured local performance guardrails

**Decision:** Enforce 50 ms stream UI publication, 300 ms ordinary stream persistence, bounded indexed message reads, a 2,048-pixel prepared-image edge, measured API 35 database/image ceilings, and one foreground-service start per idle-to-active period.

**Reason:** Long conversations and large images need repeatable regression detection, while foreground work should avoid redundant system calls. These controls change local execution only and must never alter pure request content.

**Consequences:** JVM and instrumentation tests fail when the documented ceilings are exceeded. The values are conservative emulator regression guardrails and must be re-measured before deliberate revision; they are not universal physical-device latency promises.

## 2026-09-01 - Exclude the request inspector from release artifacts

**Decision:** Compile the exact-request inspector only in the `debug` source set. The `release` source set provides a no-op composable boundary, enables code and resource shrinking, and contains neither the dialog implementation nor its strings.

**Reason:** The inspector is valuable for proving pure-conversation behavior during development, but a distribution artifact should not carry dormant UI capable of displaying complete conversation request bodies.

**Consequences:** Debug Compose tests continue to inspect exact immutable snapshots with credentials absent. Release audits check the R8 mapping and packaged resource table for inspector removal; request construction itself remains identical and contains no prompt injection.

## 2026-09-01 - Separate streaming lifetime from live reasoning presentation

**Decision:** Cancellable SSE generation has no whole-call deadline and retains a five-minute idle read timeout. Complete reasoning is persisted, while live UI state uses a bounded head/tail preview and stored long reasoning is rendered in pages.

**Reason:** A fixed 20-second request deadline incorrectly fails valid long reasoning. Repeatedly copying and laying out an ever-growing reasoning string creates avoidable memory and UI pressure.

**Consequences:** Connection tests still time out after 20 seconds. Stop cancels streaming immediately. The UI discloses when it is showing a live preview, and no reasoning text is discarded from terminal persistence. Reasoning remains display-only and never becomes a request message.

## 2026-09-01 - Use a conversation-first Room v3 and remove JSON restore

**Decision:** Remove the project layer and every project foreign key. Use one chat host with a conversation drawer, an atomic persistent conversation-number counter, and Markdown/TXT-only export. The explicit v3 migration clears legacy content, preserves settings outside Room, removes orphaned private images, and shows one Chinese notice.

**Reason:** The product is organized around independent conversations. A project hierarchy and machine-restorable JSON surface add complexity without value for this use case.

**Consequences:** Titles are local, user-controlled metadata and never model-generated or sent. Deleted or renamed numbers are never reused. JSON backup/restore code is absent. Migration validation covers `1 -> 2 -> 3` and `2 -> 3`, and destructive fallback remains forbidden. The pure-conversation invariant is unchanged.

## 2026-09-01 - Self-manage the personal APK signing key

**Decision:** Direct personal releases use one long-lived PKCS12 signing key stored in the release workstation's protected user directory, outside the repository. Gradle signs release artifacts when that external configuration exists and remains capable of producing unsigned CI release artifacts when it does not.

**Reason:** Android accepts direct APK updates only when application identity and signing lineage remain stable. Repository and public artifact boundaries must never contain private signing material.

**Consequences:** Every release increments `versionCode`, and the private key plus password require an offline backup. Losing the key prevents future compatible updates. A debug-signed installation must be removed once before adopting the release-signed lineage.

## 2026-09-11 - Remove the in-app updater from the open-source release

**Decision:** The public 1.0.0 release carries no in-app update mechanism at all. The update
domain, the manifest validator, the remote repository, the installer, the `REQUESTS_INSTALL_PACKAGES`
permission, the APK `FileProvider`, the Settings update section, and the update manifest are all
deleted rather than disabled.

**Reason:** The updater existed to serve one person's self-hosted distribution. Keeping it would
have compiled the original author's server address and signing-certificate fingerprint into every
copy, so the app would have contacted a third party's infrastructure on someone else's device, and
any fork would have failed signature validation against a certificate it does not own. Deleting it
is also the honest option for a public release: an update check is a network call to a host the
user did not choose.

**Consequences:** The app now contacts nothing until the user enters an API address, which is why
`DEFAULT_BASE_URL` is empty and Settings explains what to type. Installing a newer version is an
ordinary manual APK install. The released APK requests only `INTERNET`, `POST_NOTIFICATIONS`, and
the two foreground-service permissions; an instrumentation test asserts that
`REQUEST_INSTALL_PACKAGES` is absent and that no FileProvider is declared, so the removal cannot
silently regress. Distribution and signing still work exactly as any Android project does, so a
fork can publish its own releases without inheriting anything from the original author.

## 2026-09-01 - Retired: use a pinned self-hosted update origin

**Status:** Superseded by the removal above. Kept as history because it describes the mechanism
the private 0.3.0-0.9.0 line used.

**Decision:** App updates use an explicit Settings flow and one fixed HTTPS origin on the personal release host. The client pins the release signing-certificate digest and verifies metadata, file digest, application ID, and increasing version before invoking Android's package installer.

**Reason:** Direct personal distribution needs a simple update path without granting the server authority to install arbitrary packages or involving conversation/API data.

**Consequences:** The update host serves public immutable APKs and an atomically replaced manifest. Port 443 remains owned by Xray; Caddy uses ports 80 and 8443. Users retain unknown-source and final install confirmation. Update metadata never enters the pure-conversation path.

## 2026-09-10 - Raise the build baseline to the upgraded Android Studio

**Decision:** Build with AGP 8.13.2, Gradle Wrapper 8.14.3, Kotlin 2.3.21, KSP 2.3.11, Compose BOM 2025.08.01, Build Tools 35.0.0, and JVM bytecode target 17, using JDK 17 as the release/CI baseline while the wrapper also runs on the workstation's JDK 24.

**Reason:** The obsolete-IDE constraint that forced AGP 8.8.0 no longer applies, because Android Studio was upgraded outside the project to build `AI-261.26222.65.2613.15948027` at `C:\Program Files\Android\Android Studio`. This supersedes the retired decision above. Downgrading the project to AGP 8.8 merely to satisfy the leftover installation at `D:\Program Files (x86)\AndroidStudio` is not acceptable.

**Consequences:** `gradle/libs.versions.toml` is the single source of truth for these versions. The upgrade was verified by Gradle configuration, JVM unit tests, lint, Debug, and Release builds, and by a signed release that passes the documented release gate. This entry exists because `README.md`, the build files, and completed Phase 14 already stated the newer baseline while the earlier decision record still described the intermediate workaround.

## 2026-09-10 - Treat the archived APK, not the build output, as the release artifact

**Decision:** The published release artifact is the immutable versioned copy such as `DS-Writer-0.3.0.apk` together with the digest recorded from it. `app/build/outputs/apk/release/app-release.apk` is a working output that any later build overwrites.

**Reason:** Release builds are not byte-reproducible. Three clean `assembleRelease` runs of unchanged version 5 source produced three different SHA-256 digests at an identical byte size, and a rebuilt APK differed from the published APK in exactly two entries, `classes.dex` and `assets/dexopt/baseline.prof`, while all other 116 archive entries were identical. Zip headers, ordering, and compression are stable; the dex and baseline profile payloads from the minified build are not. Therefore a rebuild cannot reproduce or confirm a published digest, and verification must compare the archived copy with the manifest and server.

**Consequences:** A digest recorded from the build output path is invalid as soon as another build runs, so the versioned copy is archived before publishing. The security properties that the updater actually enforces remain unaffected: every observed build kept package `app.dswriter`, `versionCode 5`, and signing-certificate SHA-256 `1C624B29D9D27B59A99E13045884B4BF2360811F3415BCA9D1BC21DF4EC96035`. A future clean rebuild is not evidence that a published artifact is intact; only the archived bytes and the public manifest are.

## 2026-09-10 - Reach a private-network model server with a configured alias

**Decision:** DS Writer can address an OpenAI-compatible model server on the user's own network. Settings gains a local model alias, the model registry resolves that alias as a fourth text model, and plain HTTP is accepted only for private, loopback, and link-local addresses. Public addresses stay HTTPS-only.

**Superseded in part:** The 16,384-token local budget recorded below was replaced by a server-matched 24,576-token default in "Tune the local model from measurements" below. The reachability decision itself still stands.

**Reason:** A local llama.cpp server was the motivating case: it serves an OpenAI-compatible API over plain HTTP on a private address under a server-chosen alias. Such a server cannot be reached otherwise, because it publishes no certificate, and the previous registry contained only the three built-in provider ids, so `GenerationRequestSnapshotFactory` rejected any other value before a request could be built. The behavior is not tied to that setup: any OpenAI-compatible server on a private network works the same way, and no vendor, product, or model name is compiled in.

**Consequences:** The model alias is request routing metadata, not conversation content, and the pure-conversation invariant is unchanged: the snapshot factory still emits only real branch messages, and its tests assert that adding a local alias introduces no extra message and no system/developer role. The local alias resolves as a text-only model whose context budget is configurable. Because an Android network security config matches hostnames and cannot express a private address range, `res/xml/network_security_config.xml` permits cleartext in its base configuration while pinning `api.deepseek.com` to HTTPS, so `BaseUrlValidator` is the authoritative LAN gate. Making the server reachable is outside the app's scope: a server bound to loopback, a closed firewall port, or a phone on another subnet all remain the user's responsibility, and the app ships without any host being reachable by default. The address and the model name are configured by the user, so nothing about any particular machine is compiled into the APK, and the `获取模型列表` action reads the server's own `GET /models` so the name never has to be guessed.

## 2026-09-10 - Document that the host must be reachable for a LAN client to work

**Decision:** The reference local model server is launched with `--host 0.0.0.0` instead of `--host 127.0.0.1`, with the port allowed inbound on a private network profile. This is host configuration, not application code, and it is recorded in the README as a requirement rather than shipped as a setting.

**Reason:** Binding to loopback makes a server unreachable from any other device, so no client change could ever satisfy a phone-on-the-same-network requirement. App work alone is therefore never sufficient, and a user who only installs the app will conclude the feature is broken. Every interface still answers on `127.0.0.1`, so local-only use keeps working unchanged.

**Consequences:** Serving on every interface makes the server reachable by anything on the local network, which is the intended tradeoff and is why access is limited to private network profiles. A server bound to loopback, a closed port, or a client on another subnet are all conditions the app cannot detect or fix, so the README states them plainly instead of implying that entering an address is enough. This does not weaken the app's own policy: the client still refuses plain HTTP for public addresses.

## 2026-09-10 - Own the interface with a design system, and make the model an explicit control

**Decision:** Define the whole interface in `ui/theme/` (full color scheme, Chinese-tuned type scale, shape scale, spacing tokens, extended accent roles) and `ui/components/` (shared panels, capsules, empty states, page frame), give dark mode its own window theme, and replace the title-tap model menu with a labelled model chip that opens a grouped model picker sheet.

**Reason:** The interface previously used the stock Material baseline: only four color roles were overridden, no typography or shape was defined, `themes.xml` still inherited a platform theme, and there was no `values-night` configuration at all, so dark mode mixed light platform chrome with dark Compose surfaces. Separately, the only way to change model was to tap the conversation title, which is why a configured local model appeared to be missing — the capability existed but had no discoverable affordance.

**Consequences:** Screens share one visual language instead of per-screen styling, and the model picker states what to configure when no local model exists yet. The redesign preserves every existing behavior and the exact user-visible strings the instrumentation tests assert, because those tests encode the product contract; one genuine duplicate was found this way and removed (the API-key section title repeated the field label, and the export section title repeated a button label). The request inspector stays debug-only. Extended roles such as the queued and local accents live in a `CompositionLocal` because Material 3 has no role for them.

## 2026-09-10 - Refuse a silent rolling summary, then tune the local model from measurements

**Superseded in part:** The measured request tuning below still stands. The refusal of a rolling summary was replaced by the explicit, user-reviewed recap decided on 2026-09-11.

**Decision:** Add per-model request tuning and apply measured values to a configured local model: `max_tokens` 2560, `temperature` 0.8, `top_p` 0.95, and a context window matching the server's `-c 24576`. Official DeepSeek models set none of these, so their request body is unchanged. The measured recommendation to compress early chapters into a rolling summary is deliberately **not** implemented.

**Reason:** A measured study of the reference setup found that this model ends a chapter naturally near 1,000-1,500 Chinese characters and injects repeated filler beyond that, so a 4,096-token cap wasted roughly 1,500 tokens and about 30 seconds per generation; that the server's looser sampling defaults are less stable than 0.8 / 0.95; and that declaring a 16,384-token client window against a 24,576-token server silently discarded about 10,000 characters of usable manuscript. The summary recommendation is refused because a summary is model-written text entering the request, which `PURE_CONVERSATION.md` forbids replacing trimmed history with, and because the product's standing requirement is that no prompt of any kind is added.

**Consequences:** Request parameters are documented as not being conversation content: they carry no text, so they cannot affect message fidelity, and the serialization tests assert both that the local body contains them and that an official body does not. The context window becomes a setting rather than a constant, because changing the server's `-c` is a normal thing for the user to do and a mismatched client window fails silently by trimming. The estimator counts one token per three UTF-8 bytes, slightly more conservative than the measured 1.29 Chinese characters per token. The recap path described below is what carries long-manuscript support, and it is implemented as an explicit, reviewable action rather than an automatic summary.

## 2026-09-11 - Implement the recap as a user-reviewed real message

**Decision:** Implement the study's rolling-summary recommendation as an explicit feature: a conversation-menu action condenses the **oldest** messages into a recap, the recap is shown in an editable field, and it enters a conversation only when the user submits it as the opening `user` message of a new conversation. The condensing instruction is folded into that single real user message rather than adding a `system` role.

**Reason:** The user asked for the recommendation to be implemented, and the invariant forbids the automatic version of it. Those two are only compatible in one shape: the summary must be a real message the user has seen and approved. Condensing the oldest messages rather than the newest is required for the feature to mean anything, because a recap of the newest messages would record the part still in context and leave the beginning unrecorded.

**Consequences:** The pure-conversation invariant is unchanged in substance and the storage boundary is untouched, because the transmitted and persisted role set stays `user`/`assistant`. A recap request is now the one place DS Writer sends something other than a plain conversation turn, so its shape is covered by its own tests: exactly one real user message, no `system`/`developer` role, the original text unmodified inside the material, and the instruction present as visible text. Nothing recaps automatically, nothing attaches a recap to a later request as hidden context, and a recap is never retried on its own. A recap covers only what one request can hold, so the review step reports the condensed and remaining counts and the user recaps again for older material; that limitation is visible rather than silent.

## 2026-09-11 - Allow optional assistance, always off by default and always visible

**Decision:** The owner lifted the absolute ban on added text in a request. Writing assistance is therefore allowed, under one structural condition: every addition is a switch in Settings under **写作辅助**, every switch defaults to off, and each switch states in Chinese exactly what it does while on and what happens while off. The first feature is `上下文快满时提醒我压缩`, which shows a prominent prompt in the chat when a conversation nears its context limit and offers the recap action.

**Reason:** The original ban existed to guarantee the user's own words reached the model unaltered and that nothing was added without their knowledge. The owner's request was for the writing benefits, not for loss of control over their requests, so the guarantee is preserved by making every addition opt-in and legible rather than by forbidding addition outright. `PURE_CONVERSATION.md` was amended in the same change and now states both layers: what may never happen on any setting, and what may happen only when switched on.

**Consequences:** A fresh install still sends exactly the messages the user typed, and a test asserts that every assistance flag starts disabled. The rule that no `system` or `developer` role may ever be introduced, that user text is never rewritten, and that no request is sent without a user action all remain absolute and are still covered by tests. The switch is persisted immediately rather than on save, so the displayed state and the effective behaviour cannot drift apart. Adding a future assistance feature means adding a switch in that section; a feature that cannot be turned off, or that is on by default, is not permitted. The toggle is placed in a distinctly coloured card with an 已开启/已关闭 badge, and the in-chat prompt is a primary-container card with an explicit button, because the owner asked for these controls to be noticeable rather than buried.

## 2026-09-11 - Discover the model list instead of requiring a known alias

**Decision:** Add a `获取模型列表` action in Settings that reads `GET /models` from the configured server and lets the user pick from what the server advertises. Keep the manual name field as a fallback for servers whose payload cannot be read.

**Reason:** The request `model` value is chosen by the server (`llama.cpp --alias novel-a`), so a user cannot be expected to know it, and a blank name field left no way to reach a local model at all. Asking the server is the only reliable way to learn the value, and it is also what makes the local-model feature generic rather than specific to one deployment: nothing about any vendor, product, or model name is compiled in, and any OpenAI-compatible server works.

**Consequences:** The parser accepts only a `data` array of objects with a usable id or name and reports anything else as unsupported rather than guessing, so an unfamiliar server produces a clear Chinese message instead of a wrong request. A published filesystem path is shortened for display while the raw value is kept as the identifier to send. Discovery attaches the Authorization header only when a key exists, because a local server commonly accepts any key and a missing key must not block the model list. The server only has to publish its models; no restart or configuration change on the host is involved.

## 2026-09-11 - Open-source readiness

**Decision:** Publish the project under the MIT license and keep personal infrastructure details out of the tree entirely.

**Reason:** A pre-publication audit found no credentials, private keys, or signing material in the tree, and confirmed that `local.properties`, `.idea`, `.gradle`, `.kotlin`, and `build` are all ignored. What remained were personal host addresses, the workstation's absolute user path in the README signing section, and a missing license — none of which are secrets, but all of which would confuse a reader who is not the original author.

**Consequences:** The README now documents the signed-release setup generically, so a fork can build a release APK without any signing key. No personal host address, key path, certificate digest, or backup location remains anywhere in the tree; the audit that verified this is recorded here so a future change can be checked against it. The reference model host needs no plugin, patch, or addon: serving an OpenAI-compatible API, binding a reachable address, and allowing the port are sufficient.

## 2026-09-11 - Make the client vendor-neutral with a provider list

**Decision:** Replace the single global endpoint plus a built-in model table with a list of user-defined providers. A provider holds its own address, API key, model id, context window, optional output cap and temperature, and a vision flag. One provider is active application-wide, chosen from the chat header. The built-in model registry, the per-conversation model choice, and the only vendor hostname in the network security configuration are all removed.

**Reason:** A built-in table of one vendor's model ids was wrong for a general client: it offered models that do not exist on the user's server and refused the ones that do, and it silently sent that vendor's model id to whatever endpoint the user configured. The previous design also stored a model choice per conversation, which cannot express "use my local server for everything today".

**Consequences:** Nothing about any vendor, product, or model name is compiled in, and a test asserts that the request carries exactly the configured model id. The network security configuration no longer names a host: public endpoints must use HTTPS, enforced in code by `BaseUrlValidator` plus a base configuration that does not permit cleartext, and a private-network endpoint may use plain HTTP because the address-range check is the only thing that can express that rule. Blank output cap and temperature mean "send nothing", so a server's own defaults apply rather than this app imposing values the user never chose. An earlier version of the single-provider design did impose a 2,560-token cap automatically, and a test caught it; that behaviour is gone. Conversation rows keep their legacy model column only because the schema requires it, and nothing reads it.

## 2026-09-11 - Keep the form's actions outside the scrolling area

**Decision:** Move the save, test, and cancel actions of the service form into a footer pinned below the scrolling content, and collapse the context window, output cap, temperature, and vision flag behind an advanced section that starts closed. The address field keeps accepting plain `http://` on private addresses; the failure that prompted this change was never the validator.

**Reason:** The reported symptom was that an internal `http://` address could not be saved and its model list could not be fetched, which reads like an address-rule rejection. Reproducing it on an API 35 emulator showed the opposite: the address was accepted, and the save, test, and discovery buttons were simply absent from the view hierarchy. The form had grown taller than the screen, the buttons sat past the bottom edge, and the scroll region did not move when swiped, so no tap could reach them. Forcing HTTPS would have broken the local-server case that is the main reason this client exists while leaving the real defect in place.

**Consequences:** Every action a screen needs is pinned, so a form can no longer grow its own submit button out of reach; a form field added later cannot reintroduce the bug, and an instrumentation test clicks save with no scrolling at all. The default form is now four short fields — name, address, model name, and key — with the tunables one tap away, which matches the request to keep settings terse. Testing a connection stays available only for a saved service, because a service that has never been saved has no stored key to test with. The advanced section's open state is remembered across configuration changes but never persisted, so a fresh visit is always the short form.

## 2026-09-11 - A connection test may run without a key

**Decision:** Let the connection test send its request with no `Authorization` header when no key is configured, exactly as model discovery already does, and let the server's answer decide. A saved service with no stored key is therefore tested rather than refused.

**Reason:** Verifying the fix above on the emulator exposed a second, independent fault on the same screen: model discovery against `http://192.168.1.10:8555/v1` succeeded and listed `novel-a`, while the test button next to it reported an invalid key, because the view model returned `UNAUTHORIZED` locally whenever no key existed. The local server that motivated the whole provider list needs no key, so the test refused precisely the configuration it was meant to confirm. The check was also wrong in principle: it asserted a fact about the user's server that only the server can state.

**Consequences:** Both requests on that screen now follow one rule — attach the header only when there is a key — so discovery and testing can no longer disagree about the same service. A server that truly requires a key still answers with a 401 and is still reported as unauthorized, which a test asserts; a keyless server now reports success. The view model no longer short-circuits on a missing key, and three unit tests plus the emulator run cover the keyless path. No credential is ever sent to a server it was not configured for, because the header is absent rather than empty.

