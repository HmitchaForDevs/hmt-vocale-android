package com.hmtautosolutions.hmttvocale.nlu

enum class IntentCategory { LOCAL_APP, MEDIA, VOLUME, GENERAL, VEHICLE, UNSAFE, UNKNOWN }

enum class IntentType(val category: IntentCategory) {
    OPEN_APP(IntentCategory.LOCAL_APP),
    NAVIGATE(IntentCategory.LOCAL_APP),
    MEDIA_PLAY(IntentCategory.MEDIA),
    MEDIA_PAUSE(IntentCategory.MEDIA),
    MEDIA_NEXT(IntentCategory.MEDIA),
    MEDIA_PREVIOUS(IntentCategory.MEDIA),
    VOLUME_UP(IntentCategory.VOLUME),
    VOLUME_DOWN(IntentCategory.VOLUME),
    VOLUME_MUTE(IntentCategory.VOLUME),
    VOLUME_SET(IntentCategory.VOLUME),
    CLIMATE_ON(IntentCategory.VEHICLE),
    CLIMATE_OFF(IntentCategory.VEHICLE),
    TEMP_SET(IntentCategory.VEHICLE),
    TEMP_UP(IntentCategory.VEHICLE),
    TEMP_DOWN(IntentCategory.VEHICLE),
    FAN_UP(IntentCategory.VEHICLE),
    FAN_DOWN(IntentCategory.VEHICLE),
    WINDOWS_OPEN(IntentCategory.VEHICLE),
    WINDOWS_CLOSE(IntentCategory.VEHICLE),
    IDENTITY(IntentCategory.GENERAL),
    TIME(IntentCategory.GENERAL),
    HELP(IntentCategory.GENERAL),
    CHANGE_LANGUAGE(IntentCategory.GENERAL),
    UNSAFE(IntentCategory.UNSAFE),
    UNKNOWN(IntentCategory.UNKNOWN)
}

/** Structured result of understanding one utterance. */
data class CommandIntent(
    val type: IntentType,
    val confidence: Float,
    val params: Map<String, String> = emptyMap(),
    val normalizedText: String = ""
)
