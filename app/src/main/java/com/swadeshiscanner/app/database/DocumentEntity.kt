package com.swadeshiscanner.app.database
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var name: String,
    val createdTime: Long = System.currentTimeMillis(),
    var thumbnailPath: String? = null,
    var pageCount: Int = 0
)