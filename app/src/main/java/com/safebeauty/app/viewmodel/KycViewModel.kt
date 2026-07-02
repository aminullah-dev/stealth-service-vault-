package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.StorageRepository
import com.safebeauty.app.data.firebase.UserDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Backs the identity-verification (KYC) screen. Reads the user's live
 * kycStatus so the UI can show the right state (submit form / "under review" /
 * "rejected, resubmit" / "verified"), uploads the tazkira + selfie photos to
 * the private kyc/{uid}/ Storage path, and submits the rest via the submitKyc
 * Cloud Function (which alone can move kycStatus to PENDING).
 */
@HiltViewModel
class KycViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    val userId: String = checkNotNull(savedStateHandle["userId"])

    private val functions = FirebaseFunctions.getInstance()

    val user: StateFlow<UserDocument?> =
        firestoreRepository.observeUser(userId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed class SubmitState {
        object Idle       : SubmitState()
        object Submitting : SubmitState()
        object Success    : SubmitState()
        data class Error(val message: String) : SubmitState()
    }

    // ── Form fields ─────────────────────────────────────────────────────────────
    var tazkiraNumber   by mutableStateOf("")
    var addressProvince by mutableStateOf("")
    var addressDetail   by mutableStateOf("")
    var tazkiraBytes    by mutableStateOf<ByteArray?>(null)
    var selfieBytes     by mutableStateOf<ByteArray?>(null)

    var submitState: SubmitState by mutableStateOf(SubmitState.Idle)
        private set

    fun dismissState() { submitState = SubmitState.Idle }

    private fun validate(): String? {
        if (tazkiraNumber.isBlank())   return "Tazkira number is required"
        if (addressProvince.isBlank()) return "Province is required"
        if (addressDetail.isBlank())   return "Full address is required"
        if (tazkiraBytes == null)      return "Tazkira photo is required"
        if (selfieBytes == null)       return "Selfie is required"
        return null
    }

    fun submit() {
        val error = validate()
        if (error != null) { submitState = SubmitState.Error(error); return }

        viewModelScope.launch {
            submitState = SubmitState.Submitting
            runCatching {
                val tazkiraUrl = storageRepository.uploadKycTazkira(userId, tazkiraBytes!!)
                val selfieUrl  = storageRepository.uploadKycSelfie(userId, selfieBytes!!)

                functions.getHttpsCallable("submitKyc")
                    .call(
                        hashMapOf(
                            "tazkiraNumber"   to tazkiraNumber.trim(),
                            "addressProvince" to addressProvince.trim(),
                            "addressDetail"   to addressDetail.trim(),
                            "tazkiraPhotoUrl" to tazkiraUrl,
                            "selfiePhotoUrl"  to selfieUrl
                        )
                    )
                    .await()
            }.onSuccess {
                tazkiraBytes = null; selfieBytes = null
                submitState = SubmitState.Success
            }.onFailure { e ->
                submitState = SubmitState.Error(e.message ?: "Submission failed. Try again.")
            }
        }
    }
}
