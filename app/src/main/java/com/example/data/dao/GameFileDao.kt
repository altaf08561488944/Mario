package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ImportedGameFileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing imported NSP/XCI game files and metadata
 * in the SWTC NOOS library.
 */
@Dao
interface GameFileDao {

    @Query("SELECT * FROM imported_game_files ORDER BY addedTimestamp DESC")
    fun getAllGameFilesFlow(): Flow<List<ImportedGameFileEntity>>

    @Query("SELECT * FROM imported_game_files WHERE format = :format ORDER BY title ASC")
    fun getGameFilesByFormatFlow(format: String): Flow<List<ImportedGameFileEntity>>

    @Query("SELECT * FROM imported_game_files WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteGameFilesFlow(): Flow<List<ImportedGameFileEntity>>

    @Query("SELECT * FROM imported_game_files WHERE title LIKE '%' || :searchQuery || '%' OR titleId LIKE '%' || :searchQuery || '%' ORDER BY title ASC")
    fun searchGameFilesFlow(searchQuery: String): Flow<List<ImportedGameFileEntity>>

    @Query("SELECT * FROM imported_game_files WHERE id = :id LIMIT 1")
    suspend fun getGameFileById(id: String): ImportedGameFileEntity?

    @Query("SELECT * FROM imported_game_files WHERE titleId = :titleId LIMIT 1")
    suspend fun getGameFileByTitleId(titleId: String): ImportedGameFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameFile(gameFile: ImportedGameFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameFiles(gameFiles: List<ImportedGameFileEntity>)

    @Update
    suspend fun updateGameFile(gameFile: ImportedGameFileEntity)

    @Query("UPDATE imported_game_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE imported_game_files SET lastPlayedTimestamp = :timestamp, playTimeMinutes = playTimeMinutes + :additionalMinutes WHERE id = :id")
    suspend fun updatePlayTime(id: String, timestamp: Long, additionalMinutes: Long)

    @Query("DELETE FROM imported_game_files WHERE id = :id")
    suspend fun deleteGameFileById(id: String)

    @Query("DELETE FROM imported_game_files")
    suspend fun clearAllGameFiles()
}
