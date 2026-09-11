package app.dswriter.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.dswriter.data.local.db.ConversationDao
import app.dswriter.data.local.db.DSWriterDatabase
import app.dswriter.data.local.db.MessageDao
import app.dswriter.data.local.db.GenerationTaskDao
import app.dswriter.data.local.db.UsageDao
import app.dswriter.data.local.db.MIGRATION_1_2
import app.dswriter.data.local.db.MIGRATION_2_3
import app.dswriter.data.local.db.AttachmentDao
import app.dswriter.data.local.db.AppStateDao
import app.dswriter.data.local.db.ExportDao
import app.dswriter.data.local.attachment.PrivateImageStore
import app.dswriter.data.local.attachment.ImageDataUrlEncoder
import app.dswriter.data.local.settings.DataStoreSettingsLocalDataSource
import app.dswriter.data.local.settings.SettingsLocalDataSource
import app.dswriter.data.remote.ApiConnectionTester
import app.dswriter.data.remote.ChatStreamingClient
import app.dswriter.data.remote.OkHttpApiConnectionTester
import app.dswriter.data.remote.OkHttpChatStreamingClient
import app.dswriter.data.repository.DefaultProvidersRepository
import app.dswriter.data.repository.RoomConversationRepository
import app.dswriter.data.repository.RoomGenerationOutputStore
import app.dswriter.data.repository.RoomGenerationTaskRepository
import app.dswriter.data.repository.RoomReadableExportService
import app.dswriter.domain.export.ReadableExportService
import app.dswriter.domain.export.DocumentTransfer
import app.dswriter.data.local.export.ContentResolverDocumentTransfer
import app.dswriter.domain.model.AppClock
import app.dswriter.domain.generation.GenerationOutputStore
import app.dswriter.domain.generation.GenerationExecutor
import app.dswriter.domain.generation.GenerationRunner
import app.dswriter.domain.generation.GenerationTaskRepository
import app.dswriter.domain.generation.GenerationForegroundController
import app.dswriter.domain.generation.MonotonicTimeSource
import app.dswriter.domain.model.ConversationRepository
import app.dswriter.domain.model.ImageAttachmentPreparer
import app.dswriter.domain.settings.ProvidersRepository
import app.dswriter.security.AndroidKeystoreApiKeyCipher
import app.dswriter.security.ApiKeyCipher
import app.dswriter.service.AndroidGenerationForegroundController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindDocumentTransfer(implementation: ContentResolverDocumentTransfer): DocumentTransfer

    @Binds
    @Singleton
    abstract fun bindReadableExportService(implementation: RoomReadableExportService): ReadableExportService

    @Binds
    @Singleton
    abstract fun bindImageAttachmentPreparer(implementation: PrivateImageStore): ImageAttachmentPreparer

    @Binds
    @Singleton
    abstract fun bindImageDataUrlEncoder(implementation: PrivateImageStore): ImageDataUrlEncoder

    @Binds
    @Singleton
    abstract fun bindApiKeyCipher(implementation: AndroidKeystoreApiKeyCipher): ApiKeyCipher

    @Binds
    @Singleton
    abstract fun bindSettingsLocalDataSource(
        implementation: DataStoreSettingsLocalDataSource,
    ): SettingsLocalDataSource

    @Binds
    @Singleton
abstract fun bindProvidersRepository(
        implementation: DefaultProvidersRepository,
    ): ProvidersRepository

    @Binds
    @Singleton
    abstract fun bindApiConnectionTester(
        implementation: OkHttpApiConnectionTester,
    ): ApiConnectionTester

    @Binds
    @Singleton
    abstract fun bindModelDiscovery(
        implementation: app.dswriter.data.remote.OkHttpModelDiscovery,
    ): app.dswriter.domain.settings.ModelDiscovery

    @Binds
    @Singleton
    abstract fun bindStreamingClient(
        implementation: OkHttpChatStreamingClient,
    ): ChatStreamingClient

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        implementation: RoomConversationRepository,
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindGenerationOutputStore(
        implementation: RoomGenerationOutputStore,
    ): GenerationOutputStore

    @Binds
    abstract fun bindGenerationRunner(implementation: GenerationExecutor): GenerationRunner

    @Binds
    @Singleton
    abstract fun bindGenerationTaskRepository(
        implementation: RoomGenerationTaskRepository,
    ): GenerationTaskRepository

    @Binds
    @Singleton
    abstract fun bindGenerationForegroundController(
        implementation: AndroidGenerationForegroundController,
    ): GenerationForegroundController
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvidersModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DSWriterDatabase =
        Room.databaseBuilder(context, DSWriterDatabase::class.java, "ds-writer.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides fun provideAppStateDao(database: DSWriterDatabase): AppStateDao = database.appStateDao()
    @Provides fun provideConversationDao(database: DSWriterDatabase): ConversationDao =
        database.conversationDao()
    @Provides fun provideMessageDao(database: DSWriterDatabase): MessageDao = database.messageDao()
    @Provides fun provideAttachmentDao(database: DSWriterDatabase): AttachmentDao = database.attachmentDao()
    @Provides fun provideGenerationTaskDao(database: DSWriterDatabase): GenerationTaskDao =
        database.generationTaskDao()
    @Provides fun provideUsageDao(database: DSWriterDatabase): UsageDao = database.usageDao()
    @Provides fun provideExportDao(database: DSWriterDatabase): ExportDao = database.exportDao()

    @Provides
    fun provideClock(): AppClock = AppClock(System::currentTimeMillis)

    @Provides
    fun provideMonotonicTimeSource(): MonotonicTimeSource = MonotonicTimeSource(System::nanoTime)
}
