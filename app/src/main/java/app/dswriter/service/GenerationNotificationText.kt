package app.dswriter.service

import android.content.Context
import android.os.Build
import app.dswriter.R

object NotificationPermissionPolicy {
    fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU
}

object GenerationNotificationText {
    fun format(context: Context, running: Int, queued: Int): String = when {
        queued > 0 -> context.getString(R.string.generation_notification_running_queued, running, queued)
        else -> context.getString(R.string.generation_notification_running, running)
    }
}
