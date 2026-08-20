package com.swadeshiscanner.app.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Documents ---
    @Query("SELECT * FROM documents ORDER BY createdTime DESC")
    fun getAllDocs(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' ORDER BY createdTime DESC")
    fun searchDocs(query: String): Flow<List<DocumentEntity>>

    @Insert
    suspend fun insertDoc(doc: DocumentEntity): Long

    @Delete
    suspend fun deleteDoc(doc: DocumentEntity)

    // NEW: Needed for cleaner deletion by ID
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocById(id: Long)

    @Query("UPDATE documents SET pageCount = :count, thumbnailPath = :thumb WHERE id = :id")
    suspend fun updateDocMeta(id: Long, count: Int, thumb: String?)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDoc(id: Long): DocumentEntity?  // Made nullable (?) for safety

    @Query("UPDATE documents SET name = :newName WHERE id = :docId")
    suspend fun updateDocName(docId: Long, newName: String)

    // --- Pages ---
    @Query("SELECT * FROM pages WHERE docId = :docId ORDER BY orderIndex ASC")
    fun getPages(docId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE docId = :docId ORDER BY orderIndex ASC")
    fun getPagesList(docId: Long): List<PageEntity>

    @Insert
    suspend fun insertPage(page: PageEntity): Long

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: Long): PageEntity

    @Update
    suspend fun updatePage(page: PageEntity)

    // --- Signatures ---
    @Query("SELECT * FROM signatures ORDER BY dateAdded DESC")
    fun getAllSignatures(): List<SignatureEntity>

    @Insert
    fun insertSignature(signature: SignatureEntity): Long

    @Delete
    fun deleteSignature(signature: SignatureEntity)

    // --- Aggressive Cleanup Helpers ---
    @Query("SELECT originalPath FROM pages")
    suspend fun getAllOriginalPaths(): List<String>

    @Query("SELECT processedPath FROM pages")
    suspend fun getAllProcessedPaths(): List<String>

    @Query("SELECT thumbnailPath FROM documents")
    suspend fun getAllThumbnailPaths(): List<String>

    @Query("SELECT path FROM signatures")
    suspend fun getAllSignaturePaths(): List<String>
}
