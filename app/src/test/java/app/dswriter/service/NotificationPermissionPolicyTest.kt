package app.dswriter.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun `notification permission starts at Android 13`() {
        assertFalse(NotificationPermissionPolicy.requiresRuntimePermission(31))
        assertFalse(NotificationPermissionPolicy.requiresRuntimePermission(32))
        assertTrue(NotificationPermissionPolicy.requiresRuntimePermission(33))
        assertTrue(NotificationPermissionPolicy.requiresRuntimePermission(35))
    }
}
