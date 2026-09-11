package app.dswriter.data.local.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun messageRole(value: MessageRole): String = value.name
    @TypeConverter fun messageRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter fun messageStatus(value: MessageGenerationStatus): String = value.name
    @TypeConverter fun messageStatus(value: String): MessageGenerationStatus =
        MessageGenerationStatus.valueOf(value)

    @TypeConverter fun taskState(value: GenerationTaskState): String = value.name
    @TypeConverter fun taskState(value: String): GenerationTaskState = GenerationTaskState.valueOf(value)

    @TypeConverter fun attachmentState(value: AttachmentPreparationState): String = value.name
    @TypeConverter fun attachmentState(value: String): AttachmentPreparationState =
        AttachmentPreparationState.valueOf(value)
}
