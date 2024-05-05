package com.google.mediapipe.examples.production.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImage(player: Player)

    @Query("SELECT player_image FROM player_table")
    fun getAllPlayerImages(): Flow<List<ByteArray>>

    @Query("SELECT * FROM player_table WHERE player_name = :playerName LIMIT 1")
    suspend fun getPlayerByName(playerName: String): Player?
}
