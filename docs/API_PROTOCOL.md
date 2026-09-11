# API Protocol

## Endpoint and authentication

The default base URL is `https://api.deepseek.com`; users may override it for new requests. Chat uses `POST /chat/completions` with JSON, `Authorization: Bearer <key>`, and `stream: true`. The key must follow `SECURITY.md` and must never enter logs or persisted request snapshots.

The remote layer follows the official DeepSeek Chat Completions contract: <https://api-docs.deepseek.com/api/create-chat-completion/>.

Settings connection tests use authenticated `GET /models`, as documented at <https://api-docs.deepseek.com/api/list-models>. They send no conversation messages, do not generate model output, and do not alter conversation history.

## Model registry

Model capabilities are centralized, not spread across conditional statements:

| Model ID | UI label | Text | Vision |
| --- | --- | --- | --- |
| `deepseek-v4-flash` | Flash | yes | no |
| `deepseek-v4-pro` | Pro | yes | no |
| `deepseek-v4-flash-vision-exp` | Flash Vision | yes | yes |

The registry also owns supported thinking/reasoning options and future context limits after those values are verified against the active API contract.

Until model-specific limits are verified, the registry owns a conservative app-side estimated request budget rather than presenting it as a provider limit. Context selection reserves output space and sends only the resulting real-message suffix.

The implemented registry is `DeepSeekModelRegistry`; unsupported IDs fail before network submission. Request snapshots are immutable values containing base URL, model ID, and the exact selected user/assistant messages. Credentials are supplied only at execution time and never enter the snapshot.

## Transparent message mapping

Each selected stored user message maps to exactly one API `user` message with identical text. Each selected stored assistant message maps to exactly one API `assistant` message with identical returned text. A user-selected image maps to an image content part on its owning user message. No system/developer message, preamble, metadata, summary, or rewritten content may be added. See `PURE_CONVERSATION.md`.

## Vision content

Vision requests use `deepseek-v4-flash-vision-exp`. A user message with an explicit image is serialized as an ordered content array containing its exact text block followed by an `image_url` block. The URL is a private-copy JPEG encoded as a `data:image/jpeg;base64,...` URL and uses `detail: auto`. Assistant messages cannot own image blocks. Text-only messages retain the ordinary string form, so enabling vision does not add content to them.

The official vision contract accepts JPEG, PNG, GIF, and WebP image content, with a 32 MiB inline-image limit and a 48 MiB overall request limit: <https://api-docs.deepseek.com/guides/vision/>. This client deliberately accepts static JPEG, PNG, and WebP input, normalizes it to JPEG, caps the private result at 24 MiB, and does not silently flatten animated GIFs. Context estimation also accounts for image dimensions and encoded byte size so retained history remains below a conservative request-size guardrail.

## Streaming

Parse data-only SSE events incrementally. A JSON event can carry `choices[].delta.content`, supported `reasoning_content`, finish reason, and usage; `data: [DONE]` terminates the stream. Cancellation must cancel the underlying OkHttp call. A malformed event fails that task without cancelling siblings and retains already persisted partial output.

Usage returned by the service is authoritative. Parse structured 401, 429, timeout, connectivity, server, content-filter, length, and insufficient-resource outcomes into internal error categories; UI translations belong in Android resources.

The SSE transport reads `data:` fields until a blank line, accepts multiline data events, and terminates on `[DONE]`. It accumulates content and reasoning separately, maps finish reasons, and retains the final usage object. Cancelling collection cancels the OkHttp call and marks the partial assistant message cancelled. Invalid JSON fails the generation without logging the payload.

Streaming calls have no whole-call deadline because valid reasoning and answer generation can run for minutes. They retain a five-minute idle read timeout, and explicit stop still cancels the underlying call immediately. The short connection test remains protected by the shared client's 20-second whole-call deadline.

## Logging and snapshots

Release builds do not log request bodies, response content, Authorization headers, or full novel text. A generation snapshot stores exactly the non-secret request payload submitted at send time and the chosen base URL/model so later settings changes cannot mutate a running request.
