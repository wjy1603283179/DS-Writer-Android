# DS Writer

DS Writer is an Android 12+ OpenAI-compatible conversation client with a conversation-first Chinese interface. It stores conversations locally, streams up to three generations concurrently, supports explicit image attachments, and never adds prompts or hidden context unless you switch that on. The current app version is `1.0.0` (`versionCode 1`).

It ships with **no endpoint and no model configured**, and no vendor name is compiled in. Add your own service on first run; the settings screen explains both address shapes.

## Models

Add as many services as you like and switch between them from the chat header.

- **添加服务** opens a form: a name, the interface address, the model name, your API key, and optional parameters.
- **接口地址** takes either a public HTTPS root such as `https://api.deepseek.com`, or a private-network HTTP root such as `http://192.168.1.10:8080/v1`.
- **获取模型列表** reads `GET /models` from that address and lets you tap a model instead of typing its name. This exists because a server picks its own alias (`llama.cpp --alias novel-a`), so guessing it is impossible.
- **模型名** accepts anything the server answers to. Nothing validates it against a built-in list, because there is none.
- **单次输出上限** and **temperature** are blank by default, and blank means "send nothing", so the server's own defaults apply. Fill them only if you want to override.
- **上下文长度** should match the server's context size. If the server was started with a smaller window than you enter, requests can exceed it, so keep this honest.
- **支持图片** tells the app the model accepts images. Enable it only if the server really does.

Tap the model chip in the chat header to see every service; tapping one makes it active for the whole app, so switching models is one tap.

### Local model servers

DS Writer is a plain HTTP client, so the server side needs three things and **no plugin, patch, or addon of any kind**:

1. **Serve the OpenAI-compatible API.** For llama.cpp that means running `llama-server`, which already exposes `/v1/models` and `/v1/chat/completions`. Nothing about the model or the runtime is modified.
2. **Listen on an address other devices can reach.** `--host 127.0.0.1` is loopback-only and is unreachable from a phone no matter what the client does. Use `--host 0.0.0.0`. Local use on the same machine keeps working either way.
3. **Let the port through.** Allow the port inbound on the private network profile; on Windows the profile must also be Private, because Public blocks inbound by default.

Plain HTTP is accepted **only** for private, loopback, and link-local addresses. A public address must use HTTPS.

## Product boundary

- The app opens the most recent conversation, or atomically creates `新对话 1` on first launch.
- New conversation numbers increase permanently and are never reused after rename or deletion.
- The navigation drawer provides recent conversations, title-only search, rename, trash, task center, data, and settings.
- Requests contain only real selected user/assistant messages and explicitly selected images. See [PURE_CONVERSATION.md](docs/PURE_CONVERSATION.md).
- Markdown and TXT are the only user exports. JSON backup and restore are not supported.

### Long manuscripts

A manuscript eventually outgrows any context window. DS Writer handles that with a recap the user controls: the conversation menu offers `压缩为前情提要`, the app condenses the **oldest** messages, and the result appears in an editable field. Nothing is stored yet. Only `用它开始新对话` turns the recap into the first real `user` message of a new conversation.

The recap is therefore ordinary conversation content that the user has read and approved, never a hidden summary. The condensing instruction is folded into that same real user message, so no `system` role is ever introduced, and nothing recaps automatically. If older messages remain, the dialog says how many, and the user recaps again to bring the next-oldest material into range.

## Writing assistance

Settings has a **写作辅助** section listing optional conveniences. Every switch is **off by default**, and with them all off a request contains only the messages you typed — a test asserts that default so a fresh install cannot change anything silently.

Each switch states what it does while on and what happens while off, and the state is saved immediately rather than on save, so the label and the behaviour can never disagree.

| Switch | Off (default) | On |
| --- | --- | --- |
| 上下文快满时提醒我压缩 | No prompts, no condensing. The manual recap action is still available in the conversation menu. | When a conversation nears its context limit, a prominent card appears in the chat offering the recap action. It sends nothing by itself. |

The rule the app keeps on every setting: it never creates a `system`/`developer` role, never rewrites your text, and never sends a request you did not cause. See [PURE_CONVERSATION.md](docs/PURE_CONVERSATION.md) for the full statement.

## Build

Use AGP 8.13.2 with Gradle 8.14.3, Kotlin 2.3.21, KSP 2.3.11, JDK 17 or a Gradle-compatible newer JDK, Android SDK 35, and Build Tools 35.0.0. This baseline is compatible with Android Studio Quail 3 and retains command-line support for the available JDK 24.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

When an API 35 emulator is available, also run `./gradlew connectedDebugAndroidTest`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Signed releases

Release signing material is never stored in the repository, and no release is signed with anyone else's key. Gradle uses local signing **only** when you have provided your own `~/.gradle/dswriter-release/signing.properties` **and** have not disabled it. Build an unsigned release on any machine with:

```powershell
.\gradlew.bat assembleRelease -PdsWriterSigning=off
```

That writes `app/build/outputs/apk/release/app-release-unsigned.apk`. An unsigned APK cannot be installed until you sign it with `apksigner` and your own key.

To sign your builds automatically, create that properties file with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, and `storeType`, and keep it outside the repository; `.gitignore` already excludes it.

Keep the same `applicationId` and signing key across your own releases and increase `versionCode`, because Android accepts an in-place upgrade only when identity and signing lineage match. Back the key up offline: losing it means users must uninstall before installing your next build. A previously installed debug-signed build must be uninstalled once before installing the first release-signed build, and that is a normal manual install.

Clean release rebuilds are not byte-reproducible, so archive the versioned APK and record the digest from that copy rather than from the build output, which any later build overwrites.

## What this build does not contain

There is deliberately **no in-app update mechanism**. No update client, no update origin, no
`REQUEST_INSTALL_PACKAGES` permission, and no APK `FileProvider`. Installing a newer version is a
manual install. That was removed on purpose so the app cannot contact anyone's server except the
API address you configure, and so a fork inherits nothing from another project's infrastructure.
An instrumentation test asserts the permission and the provider are absent.

## License

MIT. See [LICENSE](LICENSE).

## Security

API keys are encrypted with Android Keystore, excluded from exports, and never stored in Room, resources, request snapshots, or logs. Android platform backup and device transfer are disabled. Readable exports include only conversation titles and exact visible user/assistant message bodies; they exclude reasoning, credentials, attachment paths, and runtime metadata.
