package org.bibletranslationtools.wat.domain

import io.github.mxaln.kotlin.document.store.core.KotlinDocumentStore
import io.github.mxaln.kotlin.document.store.core.ObjectCollection
import io.github.mxaln.kotlin.document.store.core.find
import io.github.mxaln.kotlin.document.store.core.getObjectCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.Word

interface WordDataSource {
    suspend fun getAll(): ObjectCollection<Word>
    suspend fun getById(id: Long): Word?
    suspend fun getByWord(original: String): Word?
    suspend fun add(word: Word)
    suspend fun update(word: Word)
    suspend fun delete(id: Long)
}

class WordDataSourceImpl(private val db: KotlinDocumentStore) : WordDataSource {
    override suspend fun getAll(): ObjectCollection<Word> {
        return db.getObjectCollection<Word>("words")
    }

    override suspend fun getById(id: Long): Word? {
        return withContext(Dispatchers.Default) {
            getAll().findById(id)
        }
    }

    override suspend fun getByWord(original: String): Word? {
        return withContext(Dispatchers.Default) {
            getAll().find("original", original).firstOrNull()
        }
    }

    override suspend fun add(word: Word) {
        withContext(Dispatchers.Default) {
            getAll().insert(word)
        }
    }

    override suspend fun update(word: Word) {
        withContext(Dispatchers.Default) {
            getAll().insert(word)
        }
    }

    override suspend fun delete(id: Long) {
        withContext(Dispatchers.Default) {
            getAll().removeById(id)
        }
    }
}