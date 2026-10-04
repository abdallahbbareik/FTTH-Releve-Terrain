package com.example.data.local

import androidx.room.TypeConverter

class FtthTypeConverters {
    @TypeConverter
    fun fromNodeType(type: FtthNodeType): String = type.name

    @TypeConverter
    fun toNodeType(value: String): FtthNodeType = try {
        FtthNodeType.valueOf(value)
    } catch (e: Exception) {
        FtthNodeType.PBO
    }

    @TypeConverter
    fun fromSurveyStatus(status: SurveyStatus): String = status.name

    @TypeConverter
    fun toSurveyStatus(value: String): SurveyStatus = try {
        SurveyStatus.valueOf(value)
    } catch (e: Exception) {
        SurveyStatus.PENDING
    }

    @TypeConverter
    fun fromSyncState(state: SyncState): String = state.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = try {
        SyncState.valueOf(value)
    } catch (e: Exception) {
        SyncState.SYNCED
    }
}
