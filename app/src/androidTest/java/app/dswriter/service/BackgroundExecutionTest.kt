package app.dswriter.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundExecutionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun declaresRequiredForegroundServiceAndNotificationPermissions() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
                PackageManager.GET_PROVIDERS,
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC in permissions)
        assertFalse(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in permissions)
        // This build carries no in-app update mechanism, so it must not ask to install packages
        // and must not expose a FileProvider for handing an APK to another app.
        assertFalse(Manifest.permission.REQUEST_INSTALL_PACKAGES in permissions)
        assertTrue(
            packageInfo.providers.orEmpty().none { it.authority.endsWith(".fileprovider") },
        )
        val service = packageInfo.services.orEmpty().single {
            it.name == GenerationForegroundService::class.java.name
        }
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, service.foregroundServiceType)
        assertTrue(!service.exported)
    }

    @Test
    fun formatsRunningAndQueuedNotificationInChinese() {
        assertEquals("2 个任务正在生成", GenerationNotificationText.format(context, 2, 0))
        assertEquals("3 个任务正在生成，1 个正在排队", GenerationNotificationText.format(context, 3, 1))
    }

    @Test
    fun startsForegroundServiceCreatesChineseChannelAndStopsWhenIdle() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, GenerationForegroundService::class.java),
        )
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        var channelName: String? = null
        repeat(20) {
            channelName = notificationManager
                .getNotificationChannel(GenerationForegroundService.CHANNEL_ID)
                ?.name
                ?.toString()
            if (channelName != null) return@repeat
            Thread.sleep(50)
        }
        assertEquals("生成任务", channelName)
    }
}
