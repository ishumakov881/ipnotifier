package com.example.ipnotifier.log

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "delivery_logs")
data class DeliveryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val destinationId: String,
    val destinationName: String,
    val messageText: String,
    val responseText: String,
    val success: Boolean,
)
