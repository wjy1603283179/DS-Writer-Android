package app.dswriter.domain.export

object ReadableExportFormatter {
    fun markdown(document: ExportDocument): String = buildString {
        appendLine("# DS Writer 数据导出")
        document.conversations.sortedBy(ExportConversation::createdAt).forEach { conversation ->
            appendLine()
            appendLine("## 对话：${heading(conversation.title)}")
            document.messages.filter { it.conversationId == conversation.id }.sortedBy(ExportMessage::sequence)
                .forEach { message ->
                    appendLine()
                    appendLine(if (message.role == "USER") "### 你" else "### 助手")
                    appendLine()
                    appendLine(message.content)
                }
        }
    }

    fun text(document: ExportDocument): String = buildString {
        appendLine("DS Writer 数据导出")
        document.conversations.sortedBy(ExportConversation::createdAt).forEach { conversation ->
            appendLine()
            appendLine("对话：${singleLine(conversation.title)}")
            document.messages.filter { it.conversationId == conversation.id }.sortedBy(ExportMessage::sequence)
                .forEach { message ->
                    appendLine(if (message.role == "USER") "  你：" else "  助手：")
                    appendLine(message.content)
                }
        }
    }

    private fun heading(value: String): String = singleLine(value).replace("#", "\\#")
    private fun singleLine(value: String): String = value.replace('\r', ' ').replace('\n', ' ')
}
