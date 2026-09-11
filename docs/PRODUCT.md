# Product

DS Writer is a transparent Android DeepSeek client for Chinese writers. Its primary object is a Conversation; there is no project layer.

The app launches directly into the most recently active conversation. If none exists, it atomically creates `新对话 1`. A drawer lists recent conversations by activity, supports bounded title-only search, and provides explicit rename and trash actions. Conversation titles change only when the user renames them; titles are never model-generated and never sent to the model.

Users can send exact text, explicitly attach images, select a supported model, stream and stop responses, and run up to three generations concurrently. A fourth generation remains visibly queued.

DS Writer is not a prompt-engineering, role-play, memory, summarization, or automatic writing-assistant layer. It does not offer example prompts or suggested questions. The complete invariant is owned by [PURE_CONVERSATION.md](PURE_CONVERSATION.md).

The app supports Markdown and TXT exports organized directly by conversation. It does not support JSON backup or restore. Conversation trash supports restore and confirmed permanent deletion.

Settings asks for an API address before anything can be sent. The field starts empty and the screen explains the two accepted shapes: a public HTTPS root such as `https://api.deepseek.com`, or a private-network HTTP root such as `http://192.168.1.10:8080/v1` for a model server on the user's own network. The app contacts nothing until the user supplies one. Installing a newer version is a manual APK install; there is no in-app updater.
