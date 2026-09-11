package app.dswriter.data.remote

import okio.BufferedSource

internal object SseDataParser {
    suspend fun parse(source: BufferedSource, onData: suspend (String) -> Unit) {
        val dataLines = mutableListOf<String>()

        suspend fun flush() {
            if (dataLines.isNotEmpty()) {
                onData(dataLines.joinToString("\n"))
                dataLines.clear()
            }
        }

        while (true) {
            val line = source.readUtf8Line()
            if (line == null) {
                flush()
                return
            }
            if (line.isEmpty()) {
                flush()
            } else if (line.startsWith("data:")) {
                dataLines += line.removePrefix("data:").removePrefix(" ")
            }
        }
    }
}
