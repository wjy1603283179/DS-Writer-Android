package app.dswriter.data.local.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentResolverDocumentTransferTest {
    @Test
    fun unavailableDocumentProviderMapsToIoFailure() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transfer = ContentResolverDocumentTransfer(context)

        val failure = runCatching {
            transfer.writeUtf8("content://app.dswriter.missing/document", "内容")
        }.exceptionOrNull()

        assertTrue(failure != null)
    }
}
