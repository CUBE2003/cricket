package com.google.mediapipe.examples.production.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_table")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "player_name") val name: String,
    @ColumnInfo(name = "player_image") val image: ByteArray
)
