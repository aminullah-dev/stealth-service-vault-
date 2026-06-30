package com.safebeauty.app.data.repository

import com.safebeauty.app.data.db.dao.FavoriteSalonDao
import com.safebeauty.app.data.db.entities.FavoriteSalonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteSalonDao
) {
    val favoriteIds: Flow<Set<String>> = dao.observeFavoriteIds().map { it.toSet() }

    suspend fun toggle(salonId: String) {
        if (dao.isFavorite(salonId)) dao.remove(salonId)
        else dao.add(FavoriteSalonEntity(salonId))
    }
}
