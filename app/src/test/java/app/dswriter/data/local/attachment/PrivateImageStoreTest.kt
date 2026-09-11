package app.dswriter.data.local.attachment

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateImageStoreTest {
    @Test
    fun `sample size is power of two and avoids full size decode`() {
        assertEquals(1, PrivateImageStore.calculateInSampleSize(2048, 1024, 2048))
        assertEquals(2, PrivateImageStore.calculateInSampleSize(4096, 3000, 2048))
        assertEquals(4, PrivateImageStore.calculateInSampleSize(9000, 6000, 2048))
    }
}
