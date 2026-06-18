package com.security.stealthapp.data.repository

import com.security.stealthapp.data.db.dao.AppointmentDao
import com.security.stealthapp.data.db.dao.UserDao
import com.security.stealthapp.data.db.entities.Appointment
import com.security.stealthapp.data.db.entities.AppointmentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Display-safe projection used by [ProviderDashboardScreen]. */
data class AppointmentDetail(
    val id: String,
    val customerName: String,
    val serviceName: String,
    val appointmentDate: Long,
    val status: AppointmentStatus
)

@Singleton
class AppointmentRepository @Inject constructor(
    private val dao: AppointmentDao,
    private val userDao: UserDao
) {

    fun observeForCustomer(customerId: String): Flow<List<Appointment>> =
        dao.observeForCustomer(customerId)

    fun observeForSalon(salonId: String): Flow<List<Appointment>> =
        dao.observeForSalon(salonId)

    /**
     * Returns a live stream of PENDING appointments enriched with the requesting
     * customer's name — avoids a JOIN by mapping in the repository layer.
     */
    fun observePendingDetailsForSalon(salonId: String): Flow<List<AppointmentDetail>> =
        dao.observePendingForSalon(salonId).map { appointments ->
            appointments.map { appt ->
                val customerName = userDao.getUserById(appt.customerId)?.name ?: "Unknown"
                AppointmentDetail(
                    id              = appt.id,
                    customerName    = customerName,
                    serviceName     = appt.serviceName,
                    appointmentDate = appt.appointmentDate,
                    status          = appt.status
                )
            }
        }

    suspend fun createAppointment(appointment: Appointment): Long =
        dao.insert(appointment)

    suspend fun confirm(appointmentId: String): Int =
        dao.updateStatus(appointmentId, AppointmentStatus.CONFIRMED.name)

    suspend fun cancel(appointmentId: String): Int =
        dao.updateStatus(appointmentId, AppointmentStatus.CANCELLED.name)

    suspend fun sweepOld(windowMs: Long): Int {
        val cutoff = System.currentTimeMillis() - windowMs
        return dao.deleteOlderThan(cutoff)
    }

    suspend fun deleteAll(): Int = dao.deleteAll()
}
