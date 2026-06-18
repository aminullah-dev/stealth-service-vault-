package com.security.stealthapp.data.repository

import com.security.stealthapp.data.db.dao.SalonDao
import com.security.stealthapp.data.db.entities.Salon
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [Salon] data.
 *
 * All public flows are hot Room-backed Flows — every Room update automatically
 * propagates to every collector, so toggling a provider's availability on the
 * ProviderDashboard instantly removes/adds them from the CustomerDashboard list.
 */
@Singleton
class SalonRepository @Inject constructor(private val dao: SalonDao) {

    /** Emits all available salons whenever the table changes. */
    val availableSalons: Flow<List<Salon>> = dao.observeAvailable()

    /** Emits every salon (including busy) — used for admin/provider views. */
    val allSalons: Flow<List<Salon>> = dao.observeAll()

    fun observeSalonByProvider(providerId: String): Flow<Salon?> =
        dao.observeByProvider(providerId)

    fun observeByDistrict(district: String): Flow<List<Salon>> =
        dao.observeByDistrict(district)

    suspend fun insertSalon(salon: Salon): Long = dao.insert(salon)

    suspend fun updateSalon(salon: Salon): Int = dao.update(salon)

    /**
     * Atomic single-column update — avoids race conditions on the busy toggle
     * since the Row object doesn't need to be loaded first.
     */
    suspend fun setAvailability(providerId: String, isAvailable: Boolean): Int =
        dao.setAvailability(providerId, isAvailable)
}
