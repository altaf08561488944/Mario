package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.BootConfigEntity
import com.example.data.entity.MyFolderFileEntity
import com.example.data.entity.SaveStateEntity
import com.example.data.entity.VirtualCartridgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SwtcDao {

    @Query("SELECT * FROM boot_config WHERE id = 1")
    fun getBootConfigFlow(): Flow<BootConfigEntity?>

    @Query("SELECT * FROM boot_config WHERE id = 1")
    suspend fun getBootConfig(): BootConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBootConfig(config: BootConfigEntity)

    @Query("SELECT * FROM virtual_cartridges ORDER BY createdTimestamp DESC")
    fun getAllCartridgesFlow(): Flow<List<VirtualCartridgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartridge(cartridge: VirtualCartridgeEntity)

    @Query("DELETE FROM virtual_cartridges WHERE id = :id")
    suspend fun deleteCartridge(id: String)

    @Query("SELECT * FROM my_folder_files ORDER BY lastModified DESC")
    fun getAllFolderFilesFlow(): Flow<List<MyFolderFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderFile(file: MyFolderFileEntity)

    @Query("DELETE FROM my_folder_files WHERE id = :id")
    suspend fun deleteFolderFile(id: String)

    @Query("DELETE FROM my_folder_files")
    suspend fun clearFolderFiles()

    @Query("SELECT * FROM save_states ORDER BY timestamp DESC")
    fun getAllSaveStatesFlow(): Flow<List<SaveStateEntity>>

    @Query("SELECT * FROM save_states WHERE titleId = :titleId ORDER BY timestamp DESC")
    fun getSaveStatesForTitleFlow(titleId: String): Flow<List<SaveStateEntity>>

    @Query("SELECT * FROM save_states WHERE id = :id")
    suspend fun getSaveStateById(id: String): SaveStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaveState(state: SaveStateEntity)

    @Update
    suspend fun updateSaveState(state: SaveStateEntity)

    @Query("UPDATE save_states SET slotName = :newName WHERE id = :id")
    suspend fun renameSaveState(id: String, newName: String)

    @Query("DELETE FROM save_states WHERE id = :id")
    suspend fun deleteSaveState(id: String)

    @Query("DELETE FROM save_states WHERE titleId = :titleId")
    suspend fun deleteSaveStatesForTitle(titleId: String)
}
