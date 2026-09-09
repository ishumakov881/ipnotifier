package com.example.ipnotifier.log

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DeliveryLogEntity): Long

    @Query("DELETE FROM delivery_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM delivery_logs ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<DeliveryLogEntity>>

    @Query(
        """
        SELECT * FROM delivery_logs
        WHERE :query = ''
           OR destinationName LIKE '%' || :query || '%'
           OR destinationId LIKE '%' || :query || '%'
           OR messageText LIKE '%' || :query || '%'
           OR responseText LIKE '%' || :query || '%'
        ORDER BY timestampMillis DESC
        """,
    )
    fun observeSearch(query: String): Flow<List<DeliveryLogEntity>>
}
