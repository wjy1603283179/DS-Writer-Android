package app.dswriter.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.dswriter.domain.generation.GenerationForegroundController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidGenerationForegroundController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GenerationForegroundController {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, GenerationForegroundService::class.java),
        )
    }
}
