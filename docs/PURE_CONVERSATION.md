# Pure Conversation Invariant

## Status

This document defines a hard product and security invariant. It overrides any older or conflicting suggestion to add automatic instructions.

**Amended 2026-09-11:** the owner lifted the absolute ban on added text so that writing assistance can exist, on one condition: **every addition is optional, off by default, and visible.** The rules below are therefore restated as two layers — what the app may never do, and what it may do only when the user has switched it on.

## Rule

DS Writer must never inject hidden instructions or silently alter a user message.

The remote request contains real messages selected from the active conversation branch and explicit image attachments the user selected. With every optional assistance switch off — the default for a new install — that is the entire request.

Allowed message content:

- user text exactly as submitted;
- assistant text exactly as returned by DeepSeek;
- image attachments the user explicitly selected for the submitted user message;
- text the user approved through an optional, switchable assistance feature described in its own section below.

What the app must never do, on any setting:

- create or send a `system` or `developer` role;
- rewrite, prefix, suffix, or otherwise alter text the user typed;
- replay assistant reasoning as an assistant message;
- add anything at all while the relevant feature switch is off;
- send a request that the user did not cause, including retries of anything potentially billable.

Visible interface text is not conversation content. Conversation titles (including local `新对话 N` titles), model labels, draft hints, error messages, accessibility labels, generation-state labels, and reasoning presentation must never enter a request unless the user explicitly submits that exact text as a user message. Assistant reasoning is stored and displayed separately and must not be replayed as an assistant message.

App-update metadata, version values, release notes, download status, signing certificates, and update-server details are also outside conversation content and must never enter a model request.

Request parameters are also not conversation content. `max_tokens`, `temperature`, and `top_p` control sampling and length; they carry no text, message, instruction, or user wording, so they may be set per model. Because a local OpenAI-compatible server's defaults differ from the official DeepSeek defaults, they are set only for a configured local model and left unset otherwise, which keeps an official request body unchanged. Any parameter that would carry text remains forbidden.

For example, the user input `继续` remains `继续`. It must not become an instruction to continue a novel from previous context.

## Optional assistance

Assistance features live behind switches in Settings under **写作辅助**, and every one of them defaults to off. A switch states, in Chinese, exactly what it does while on and what happens while off, so the switch and the behaviour can never disagree.

Current features:

- **上下文快满时提醒我压缩** — shows a visible prompt in the chat when a conversation nears its context limit. It offers the recap action. It sends nothing by itself.

Adding a feature here means adding a switch. An addition that cannot be turned off, or that is on by default, is not permitted by this document.

## Recap

A long conversation may be condensed into a recap. This is always an explicit user action: the user triggers it, reads the result, may edit it, and decides whether it becomes the opening `user` message of a new conversation. The recap is then ordinary real conversation content.

The condensing request is the one place the app sends a message the model wrote rather than the user. It is bounded by these rules:

- its instruction is folded into a single real user message, so no `system` or `developer` role is ever introduced and the transmitted role set stays `user`/`assistant`;
- it is sent only after the user confirms, never automatically and never on a retry;
- its result is shown for review before anything is stored, and is discarded if the user does not use it;
- a recap is never attached to a later request as hidden context.

## Context limits

Context selection is allowed; context fabrication is not. The context engine may progressively remove older real messages to fit a model limit. It must not replace removed messages with a synthetic or AI-generated hidden summary. It should preserve complete exchanges when possible and must always retain the current user message.

Local summaries may later support navigation or search, but they are not request messages. Any future explicit-context feature must require an intentional user action and show the exact material before sending.

## Storage boundary

Internal metadata belongs in dedicated database columns or entities. It must never masquerade as a conversation message. The persisted role set for ordinary conversation messages is `user` and `assistant` only.

## Verifiability

Request construction must be deterministic and testable without the network. Debug builds should provide a request inspector that shows the exact roles, text, and attachment references sent, with secrets redacted. Its implementation must not be packaged into release builds. Release builds must not log request bodies.

Tests must fail if request construction adds an unselected message, changes message text, creates a system/developer role, or replaces trimmed history with a summary. A recap request is included in that rule: its tests assert that it carries exactly one real `user` message, no `system`/`developer` role, the original text unmodified inside the material, and the instruction as visible text inside that same message. A test also asserts that every assistance flag is off by default, because a fresh install must change nothing.

There must be one production request-construction path. UI code must pass stored branch messages to that path and must not concatenate prompts or construct API messages directly. Serialization tests inspect the final JSON body, not only intermediate domain values.

The context selector is part of that single snapshot factory. Its only permitted content operation is removing older whole real messages. Estimated token counts and dropped-message counts are snapshot metadata and are not API messages. The debug inspector reads the already-built snapshot; it cannot edit or augment it.
