package com.example.data.local

import androidx.room.TypeConverter

class FtthTypeConverters {
    @TypeConverter
    fun fromNodeType(type: FtthNodeType): String = type.name

    @TypeConverter
    fun toNodeType(value: String): FtthNodeType = try {
        FtthNodeType.valueOf(value)
    } catch (e: Exception) {
        FtthNodeType.BOITIER
    }

    @TypeConverter
    fun fromNodeStatus(status: NodeStatus): String = status.name

    @TypeConverter
    fun toNodeStatus(value: String): NodeStatus = try {
        NodeStatus.valueOf(value)
    } catch (e: Exception) {
        NodeStatus.EXISTANT
    }

    @TypeConverter
    fun fromNodeConformity(conformity: NodeConformity): String = conformity.name

    @TypeConverter
    fun toNodeConformity(value: String): NodeConformity = try {
        NodeConformity.valueOf(value)
    } catch (e: Exception) {
        NodeConformity.CONFORME
    }

    @TypeConverter
    fun fromSyncState(state: SyncState): String = state.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = try {
        SyncState.valueOf(value)
    } catch (e: Exception) {
        SyncState.SYNCED
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String =
        list?.joinToString(";;;") ?: ""

    @TypeConverter
    fun toStringList(data: String?): List<String> =
        if (data.isNullOrBlank()) emptyList() else data.split(";;;").filter { it.isNotBlank() }
}
