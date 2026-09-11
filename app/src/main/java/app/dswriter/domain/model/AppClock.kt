package app.dswriter.domain.model

fun interface AppClock {
    fun currentTimeMillis(): Long
}
