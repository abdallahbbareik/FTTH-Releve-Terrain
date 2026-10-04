package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FtthDao {
    @Query("SELECT * FROM ftth_nodes ORDER BY updatedAt DESC")
    fun getAllNodes(): Flow<List<FtthNodeEntity>>

    @Query("SELECT * FROM ftth_nodes WHERE id = :id LIMIT 1")
    fun getNodeById(id: String): Flow<FtthNodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: FtthNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<FtthNodeEntity>)

    @Update
    suspend fun updateNode(node: FtthNodeEntity)

    @Delete
    suspend fun deleteNode(node: FtthNodeEntity)

    @Query("DELETE FROM ftth_nodes WHERE id = :id")
    suspend fun deleteNodeById(id: String)

    @Query("SELECT * FROM ftth_links ORDER BY id ASC")
    fun getAllLinks(): Flow<List<FtthLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: FtthLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<FtthLinkEntity>)

    @Delete
    suspend fun deleteLink(link: FtthLinkEntity)

    @Query("DELETE FROM ftth_links WHERE id = :id")
    suspend fun deleteLinkById(id: String)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 50")
    fun getAllSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("SELECT COUNT(*) FROM ftth_nodes")
    suspend fun getNodeCount(): Int

    @Query("DELETE FROM ftth_nodes")
    suspend fun clearNodes()

    @Query("DELETE FROM ftth_links")
    suspend fun clearLinks()
}
