package com.arsipguru.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val emoji: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("categoryId")]
)
data class Doc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileName: String,   // nama file di penyimpanan internal aplikasi
    val mime: String,
    val sizeBytes: Long,
    val categoryId: Long,
    val createdAt: Long
)

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun documents(): Flow<List<Doc>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert suspend fun insertCategory(c: Category): Long
    @Update suspend fun updateCategory(c: Category)
    @Delete suspend fun deleteCategory(c: Category)

    @Insert suspend fun insertDoc(d: Doc): Long
    @Update suspend fun updateDoc(d: Doc)
    @Delete suspend fun deleteDocs(docs: List<Doc>)

    @Query("UPDATE documents SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveDocs(from: Long, to: Long)
}

@Database(entities = [Category::class, Doc::class], version = 1, exportSchema = false)
abstract class ArchiveDb : RoomDatabase() {
    abstract fun dao(): ArchiveDao

    companion object {
        @Volatile private var instance: ArchiveDb? = null
        fun get(context: Context): ArchiveDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ArchiveDb::class.java, "arsip.db")
                .build().also { instance = it }
        }
    }
}
