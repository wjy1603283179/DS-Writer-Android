# Context Management

## Purpose

The context engine selects real messages from the active branch that fit the model request, retains recent conversation, reduces unnecessary token use, and preserves branch correctness. It never creates context messages.

## Selection policy

Always retain the current user message. Walk backward through the selected branch, prioritizing recent complete user/assistant exchanges. Add older exchanges while the estimated input budget permits. When necessary, progressively remove the oldest selected exchanges. Removing an unmatched old message is a documented last-resort fallback; never split or corrupt UTF-8 text.

Do not shorten message content, rewrite it, merge messages, or insert summaries. Conversation titles, legacy project metadata, character notes, other conversations, local search results, and deleted branches are excluded unless a future explicit UI lets the user select visible context before sending.

## Token accounting

Preflight counts may be estimates and must be labeled as such internally. Reserve a configurable output allowance and registry-specific protocol overhead. The completed API usage response is authoritative and is stored separately.

The implemented estimator conservatively converts UTF-8 byte length to estimated tokens and adds per-message/request protocol overhead. The registry currently applies an app-side 64,000-token safety budget with 8,000 reserved for output. This is a local guardrail, not a claim about the provider's contractual context limit, and must be revisited when the active model contract is verified.

For explicit images, the estimate adds the larger of a 512-pixel tile heuristic and one estimated unit per 512 compressed bytes. This is a conservative local request-size guardrail, not provider-reported token usage.

The selector always retains the current user message, even when that message alone exceeds the estimate. It then walks backward in complete user/assistant exchanges. It never truncates a string. The database supplies at most 200 recent branch messages plus the full branch count; an assistant orphaned by that bounded page boundary is excluded rather than sent without its owning user exchange.

## Request snapshot

Selection occurs once at submission. The ordered selected messages, exact content, attachment references, model, and non-secret settings form an immutable request snapshot. Subsequent edits or settings changes do not alter the running request.

Debug builds retain the latest immutable snapshot in the application-scoped manager and expose a Chinese request inspector. It displays the exact selected roles and text, model, base URL, estimated input, and dropped-message count. The snapshot type cannot contain credentials, and release builds do not expose the inspector.

For images, the inspector shows the stable attachment ID, display name, MIME type, and dimensions. It does not render or log the base64 request body.

## Tests

Cover empty history, long history, a current message larger than the budget, exchange boundaries, branch forks, Unicode and emoji, attachment ownership, stable ordering, and the absence of synthetic roles or content.
