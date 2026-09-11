package app.dswriter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.dswriter.MainActivity
import app.dswriter.R
import app.dswriter.domain.generation.GenerationTaskManager
import app.dswriter.domain.generation.GenerationTaskStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GenerationForegroundService : Service() {
    @Inject lateinit var manager: GenerationTaskManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startInForeground(running = 1, queued = 0)
        scope.launch {
            manager.states.collectLatest { states ->
                val running = states.values.count { it.status == GenerationTaskStatus.RUNNING }
                val queued = states.values.count { it.status == GenerationTaskStatus.QUEUED }
                if (running + queued == 0) {
                    ServiceCompat.stopForeground(this@GenerationForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(running, queued))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.cancelAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(running: Int, queued: Int) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(running, queued),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun buildNotification(running: Int, queued: Int): Notification {
        val openTasks = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_TASKS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openTasks,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.generation_notification_title))
            .setContentText(GenerationNotificationText.format(this, running, queued))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.generation_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.generation_channel_description) },
        )
    }

    companion object {
        internal const val CHANNEL_ID = "generation"
        private const val NOTIFICATION_ID = 1001
    }
}
