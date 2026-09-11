# Local Model Tuning

This document records how DS Writer configures a local OpenAI-compatible model server, and why.
The parameters come from a measured study of the reference setup (llama.cpp serving
`Qwen3.6-35B-A3B-Uncensored-HauhauCS-Aggressive Q4_K_M` on a 12 GB GPU) rather than from
guesswork, so the numbers are treated as findings and not as preferences.

## What the app sends

DS Writer builds one request shape for every provider. For a local model it adds three
parameters; for an official DeepSeek model it adds none, so those requests are unchanged.

| Parameter | Local model | Official DeepSeek |
| --- | --- | --- |
| `model` | the configured alias, e.g. `novel-a` | the built-in model id |
| `max_tokens` | 2560 | omitted |
| `temperature` | 0.8 | omitted |
| `top_p` | 0.95 | omitted |
| `stream` | `true` | `true` |
| `stream_options.include_usage` | `true` | `true` |
| `messages` | real selected user/assistant messages only | same |

This mirrors the reference harness, which sets `stream_options.include_usage` and passes the
per-model `maxTokens` and `temperature` through to the chat-completions body.

## Why these values

**Output cap 2560 tokens.** The model ends a chapter naturally around 1,000-1,500 Chinese
characters (roughly 780-1,170 tokens). Forcing it far past that point injects repeated filler:
measured 12-gram repetition was 0.000 in the first 1,200 characters and rose steadily beyond it.
A 4,096-token cap therefore spends roughly 1,500 tokens producing text that has to be discarded,
costing around 30 seconds of generation. 2,560 tokens is about 2,000 Chinese characters, which
leaves headroom for a longer scene without inviting repetition.

**temperature 0.8 / top_p 0.95.** The server defaults (`temperature 1.0`, `top_k 20`,
`min_p 0.05`) are looser; 0.8 / 0.95 measured as the most stable pair for this model.

**Context window 24576.** The server is started with `-c 24576`, and the study confirmed a
22.4K-token context still behaved normally. Declaring 16,384 on the client discards roughly
8K tokens - about 10,000 Chinese characters - that the server could still hold. Because the
context selector trims whole messages, a too-small window silently drops usable novel text
rather than failing loudly, so the setting must match the server. Settings exposes it as
`本地上下文长度（token）` and documents the `-c` requirement, because a different launch
argument is a normal thing for the user to change.

The token estimator counts one token per three UTF-8 bytes, which is about one token per Chinese
character. The study measured 1.29 Chinese characters per token, so the estimator is slightly
conservative - it trims a little earlier than strictly necessary, which is the safe direction.

## Long manuscripts: an explicit, reviewable recap

The study recommends compressing early chapters once a manuscript passes roughly 20,000
characters. DS Writer implements this only in a form that keeps
[PURE_CONVERSATION.md](PURE_CONVERSATION.md) true, because a summary is model-written text and
must never enter a request behind the user's back.

The flow is:

1. The user opens the conversation menu and chooses `压缩为前情提要`, then confirms.
2. The app sends **one** request that condenses the **oldest** messages, because those are the
   ones a long conversation is about to lose.
3. The recap is shown in an editable field. Nothing is stored and nothing is submitted yet.
4. If the user taps `用它开始新对话`, the recap becomes the first real `user` message of a new
   conversation.

Two properties make this compliant rather than a loophole:

- **The recap is a real user message.** Once created it is ordinary conversation content, exactly
  what the user reviewed and possibly edited. It is never a hidden summary attached to a request.
- **No new role appears.** The condensing instruction is folded into a single real user message
  instead of adding a `system` message, so the transmitted and persisted role set stays
  `user`/`assistant`. That keeps the storage boundary in the invariant intact.

The instruction sent for condensing is `RecapPrompts.SUMMARY_INSTRUCTION`, and it is visible to
the user because it is part of the message they approve. It asks only for a factual recap and
explicitly forbids continuing the story, inventing content, or commenting, so the result stays a
condensation of what the user actually wrote.

Because a recap covers only what one request can hold, the review step reports how many messages
were condensed and warns when older ones remain. In that case the user recaps again, which
removes the text just recorded and brings the next-oldest material into range. This is how a
manuscript far beyond the window is handled without inventing anything: repeated, user-driven
passes, each one visible.

### What is still not automatic

Nothing recaps itself. The app never condenses history on its own, never attaches a recap to a
request, and never retries a recap. If a conversation grows past its window, the context selector
still trims whole older messages as before, and it remains the user's decision whether to record
them first.

## Verification

- Unit tests assert the serialized request body with the production JSON configuration, so
  `max_tokens`, `temperature`, and `top_p` are checked as they appear on the wire.
- Unit tests assert that an official model's body contains none of those keys.
- Unit tests assert the model's output reservation stays strictly inside its context window,
  including for a user-set window at the minimum.
- Instrumentation tests assert the Chinese settings controls and the context-window validation
  message render as specified.
