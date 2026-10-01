package com.hobitv.app

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.progressStore by preferencesDataStore("hobi_progress")
class ProgressStore(private val context: Context) {
    private fun key(id: Int) = longPreferencesKey("p_$id")
    suspend fun get(id: Int) = context.progressStore.data.first()[key(id)] ?: 0L
    suspend fun save(id: Int, position: Long) { context.progressStore.edit { it[key(id)] = position } }
}
