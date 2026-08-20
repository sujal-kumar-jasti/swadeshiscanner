package com.swadeshiscanner.app.database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["docId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val originalPath: String,
    var processedPath: String? = null,
    var orderIndex: Int = 0,
    val cropData: String? = null,
    val notes: String? = null
)