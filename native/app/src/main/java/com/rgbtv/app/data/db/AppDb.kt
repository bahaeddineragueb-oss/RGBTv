package com.rgbtv.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "favs", primaryKeys = ["acc", "type", "itemId"])
data class FavEntity(
    val acc: String,
    val type: String,
    val itemId: String,
    val name: String = "",
    val img: String = "",
    val data: String = "",
    val at: Long = 0
)

@Entity(tableName = "history", primaryKeys = ["acc", "type", "itemId"])
data class HistEntity(
    val acc: String,
    val type: String,
    val itemId: String,
    val name: String = "",
    val img: String = "",
    val data: String = "",
    val at: Long = 0
)

@Entity(tableName = "positions", primaryKeys = ["acc", "itemKey"])
data class PosEntity(
    val acc: String,
    val itemKey: String,
    val pos: Long = 0,
    val dur: Long = 0,
    val at: Long = 0
)

@Dao
interface FavDao {
    @Query("SELECT * FROM favs WHERE acc = :acc ORDER BY at DESC LIMIT 300")
    suspend fun list(acc: String): List<FavEntity>

    @Query("SELECT COUNT(*) FROM favs WHERE acc = :acc AND type = :type AND itemId = :id")
    suspend fun exists(acc: String, type: String, id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: FavEntity)

    @Query("DELETE FROM favs WHERE acc = :acc AND type = :type AND itemId = :id")
    suspend fun delete(acc: String, type: String, id: String)

    @Query("DELETE FROM favs WHERE acc = :acc")
    suspend fun clearAcc(acc: String)

    @Query("SELECT * FROM favs")
    suspend fun all(): List<FavEntity>
}

@Dao
interface HistDao {
    @Query("SELECT * FROM history WHERE acc = :acc ORDER BY at DESC LIMIT 60")
    suspend fun list(acc: String): List<HistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: HistEntity)

    @Query("DELETE FROM history WHERE acc = :acc AND at NOT IN (SELECT at FROM history WHERE acc = :acc ORDER BY at DESC LIMIT 60)")
    suspend fun trim(acc: String)

    @Query("DELETE FROM history WHERE acc = :acc")
    suspend fun clearAcc(acc: String)

    @Query("SELECT * FROM history")
    suspend fun all(): List<HistEntity>
}

@Dao
interface PosDao {
    @Query("SELECT * FROM positions WHERE acc = :acc AND itemKey = :key")
    suspend fun get(acc: String, key: String): PosEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: PosEntity)

    @Query("DELETE FROM positions WHERE acc = :acc AND itemKey = :key")
    suspend fun delete(acc: String, key: String)

    @Query("DELETE FROM positions WHERE acc = :acc")
    suspend fun clearAcc(acc: String)

    @Query("SELECT * FROM positions")
    suspend fun all(): List<PosEntity>
}

@Database(entities = [FavEntity::class, HistEntity::class, PosEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun favs(): FavDao
    abstract fun hist(): HistDao
    abstract fun pos(): PosDao

    companion object {
        @Volatile
        private var inst: AppDb? = null

        fun get(ctx: Context): AppDb {
            return inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "rgbtv.db")
                    .fallbackToDestructiveMigration()
                    .build().also { inst = it }
            }
        }
    }
}
