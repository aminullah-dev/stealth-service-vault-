package com.safebeauty.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.AppointmentDocument
import com.safebeauty.app.data.firebase.FirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class ExportPhase { IDLE, WORKING, DONE, ERROR }

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("userId") ?: ""

    var phase by mutableStateOf(ExportPhase.IDLE)
        private set
    var shareUri: Uri? by mutableStateOf(null)
        private set

    fun export() {
        if (userId.isBlank()) { phase = ExportPhase.ERROR; return }
        viewModelScope.launch {
            phase = ExportPhase.WORKING
            runCatching {
                val appointments = firestoreRepository.getAppointmentsForUser(userId)
                val csv = buildCsv(appointments)
                val file = File(context.cacheDir, "my_appointments.csv")
                file.writeText(csv)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.onSuccess { uri ->
                shareUri = uri
                phase = ExportPhase.DONE
            }.onFailure {
                phase = ExportPhase.ERROR
            }
        }
    }

    fun reset() {
        phase = ExportPhase.IDLE
        shareUri = null
    }

    private fun buildCsv(appointments: List<AppointmentDocument>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            appendLine("Date,Service,Salon,Status")
            for (appt in appointments) {
                val date = fmt.format(Date(appt.appointmentDate))
                appendLine("$date,${appt.serviceName},${appt.salonName},${appt.status}")
            }
        }
    }
}
