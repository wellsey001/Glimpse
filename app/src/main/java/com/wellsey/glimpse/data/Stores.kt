package com.wellsey.glimpse.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Favourites are kept by Glimpse itself (by MediaStore id) so starring is instant —
 * the system "favourite" flag needs a confirmation dialog every time.
 * Ids survive moving a file between folders.
 */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    private fun load(): Set<Long> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun set(ids: Collection<Long>, favorite: Boolean) {
        val next = if (favorite) _ids.value + ids else _ids.value - ids.toSet()
        _ids.value = next
        prefs.edit().putStringSet(KEY, next.map { it.toString() }.toSet()).apply()
    }

    fun toggle(id: Long) = set(listOf(id), id !in _ids.value)

    private companion object {
        const val KEY = "ids"
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _columns = MutableStateFlow(prefs.getInt("columns", 4))
    val columns: StateFlow<Int> = _columns.asStateFlow()

    /** Folders (MediaStore bucket ids) left out of Photos, Search, Map and Videos. */
    private val _hiddenFolders = MutableStateFlow(
        prefs.getStringSet("hiddenFolders", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    )
    val hiddenFolders: StateFlow<Set<Long>> = _hiddenFolders.asStateFlow()

    fun setHiddenFolders(ids: Set<Long>) {
        _hiddenFolders.value = ids
        prefs.edit().putStringSet("hiddenFolders", ids.map { it.toString() }.toSet()).apply()
    }

    fun setFolderShown(id: Long, shown: Boolean) =
        setHiddenFolders(if (shown) _hiddenFolders.value - id else _hiddenFolders.value + id)

    fun setColumns(value: Int) {
        val v = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        _columns.value = v
        prefs.edit().putInt("columns", v).apply()
    }

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 7
    }
}
