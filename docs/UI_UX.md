# UI and UX

All visible copy, errors, notifications, states, and accessibility descriptions are Simplified Chinese. `DS Writer` and model labels are product/protocol names.

The primary screen is a Material 3 chat workspace wrapped by a modal navigation drawer. The drawer contains a prominent new-conversation action, title search, recent conversations ordered by activity, current-row highlight, unread blue dots, per-row rename/trash menus, and fixed task/data/settings entries.

The top app bar contains the drawer button, current title, model subtitle, model selection, and conversation management. An empty conversation displays only `DS Writer` and `开始一段新对话`; it never displays prompt suggestions or auto-send content.

User messages use restrained right-aligned bubbles with bounded width. Assistant messages use the available width without heavy cards and retain selection, copy, reasoning disclosure, regeneration, and generation status. The floating rounded composer places attachment, multiline text, and circular send/stop controls together. Image status, incompatibility, queue state, and errors sit immediately above it.

Reasoning is collapsed by default in a full-width, 56 dp disclosure row that shows whether reasoning is still arriving and its received character count. Expansion uses a 320 dp maximum viewport and 6,000-character previous/next pages so very long reasoning never creates an unbounded text layout. During a very long live stream, the panel explicitly says it is showing the beginning and latest portion while the complete reasoning is saved.

The composer applies IME and navigation-bar insets. Interactive icons expose Chinese descriptions and a minimum 48 dp target. Light and dark themes use neutral surfaces with a restrained blue primary accent.

Auto-follow occurs only near the bottom. Switching conversations does not add navigation history; returning from settings, tasks, or data restores the chat destination and its selected conversation.

Settings starts with an empty `API 地址` field and must teach the accepted shapes rather than assume the user knows them: the field carries an example placeholder, and a guide panel below it explains a public HTTPS root and a private-network HTTP root with a path prefix. A local entry also shows the `获取模型列表` action, which lists what the server advertises so the model name never has to be guessed. An empty address is reported as "not filled in yet", never as an invalid URL.

The `写作辅助` section holds the optional assistance switches. It is visually separated, shows an `已开启`/`已关闭` badge, and each switch describes both its on and its off behaviour so the control and the outcome cannot disagree. There is no `应用更新` section in this build.
