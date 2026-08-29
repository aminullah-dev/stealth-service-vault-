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

    /**
     * What is wrong, as something the screen can translate.
     *
     * These were English literals — "Tazkira number is required", and whatever
     * Firebase happened to put in an exception message — on the one screen a
     * woman cannot get past without completing. The app is trilingual precisely
     * because most of the people using it do not read English, and this was the
     * gate where that mattered most.
     */
    enum class SubmitError {
        TAZKIRA_NUMBER_REQUIRED, PROVINCE_REQUIRED, ADDRESS_REQUIRED,
        TAZKIRA_PHOTO_REQUIRED, SELFIE_REQUIRED,
        PHOTO_TOO_LARGE, PHOTO_UNREADABLE,
        NO_CONNECTION, UPLOAD_FAILED,
    }

    sealed class SubmitState {
        object Idle       : SubmitState()
        object Submitting : SubmitState()
        object Success    : SubmitState()
        data class Error(val reason: SubmitError) : SubmitState()
    }

    // ── Form fields ─────────────────────────────────────────────────────────────
    var tazkiraNumber   by mutableStateOf("")
    var birthYear       by mutableStateOf("")
    var tazkiraIssueDate  by mutableStateOf("")
    var tazkiraExpiryDate by mutableStateOf("")
    var addressProvince by mutableStateOf("")
    var addressDetail   by mutableStateOf("")
    var tazkiraBytes    by mutableStateOf<ByteArray?>(null)
    var selfieBytes     by mutableStateOf<ByteArray?>(null)

    var submitState: SubmitState by mutableStateOf(SubmitState.Idle)
        private set

    fun dismissState() { submitState = SubmitState.Idle }

    /** Report a photo the picker could not turn into something uploadable. */
    fun reportPhotoProblem(tooLarge: Boolean) {
        submitState = SubmitState.Error(
            if (tooLarge) SubmitError.PHOTO_TOO_LARGE else SubmitError.PHOTO_UNREADABLE
        )
    }

    private fun validate(): SubmitError? = when {
        tazkiraNumber.isBlank()   -> SubmitError.TAZKIRA_NUMBER_REQUIRED
        addressProvince.isBlank() -> SubmitError.PROVINCE_REQUIRED
        addressDetail.isBlank()   -> SubmitError.ADDRESS_REQUIRED
        tazkiraBytes == null      -> SubmitError.TAZKIRA_PHOTO_REQUIRED
        selfieBytes == null       -> SubmitError.SELFIE_REQUIRED
        else                      -> null
    }

    fun submit() {
        val error = validate()
        if (error != null) { submitState = SubmitState.Error(error); return }

        viewModelScope.launch {
            submitState = SubmitState.Submitting
            runCatching {
                // Upload first, then tell the server. Neither call returns an
                // address: the KYC path is fixed, so submitKyc composes it from
                // the caller's own uid and checks the objects are really there.
                // Nothing about where a woman's identity card lives is taken
                // from the client any more, and no shareable URL is minted.
                storageRepository.uploadKycTazkira(userId, tazkiraBytes!!)
                storageRepository.uploadKycSelfie(userId, selfieBytes!!)

                functions.getHttpsCallable("submitKyc")
                    .call(
                        hashMapOf(
                            "tazkiraNumber"     to tazkiraNumber.trim(),
                            "birthYear"         to birthYear.trim(),
                            "tazkiraIssueDate"  to tazkiraIssueDate.trim(),
                            "tazkiraExpiryDate" to tazkiraExpiryDate.trim(),
                            "addressProvince"   to addressProvince.trim(),
                            "addressDetail"     to addressDetail.trim()
                        )
                    )
                    .await()
            }.onSuccess {
                tazkiraBytes = null; selfieBytes = null
                submitState = SubmitState.Success
            }.onFailure { e ->
                val fx = e as? com.google.firebase.functions.FirebaseFunctionsException
                val offline = fx?.code == com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE ||
                    fx?.code == com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
                    e.javaClass.simpleName.contains("UnknownHost") ||
                    e.javaClass.simpleName.contains("Timeout")
                submitState = SubmitState.Error(
                    if (offline) SubmitError.NO_CONNECTION else SubmitError.UPLOAD_FAILED
                )
            }
        }
    }
}
