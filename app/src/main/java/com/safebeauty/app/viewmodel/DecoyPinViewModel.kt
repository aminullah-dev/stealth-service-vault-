package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.repository.VaultRepository
import com.safebeauty.app.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DecoyPinViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val pinHasher: PinHasher,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    var showDialog   by mutableStateOf(false)
    var newPin       by mutableStateOf("")
    var confirmPin   by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)
    var saveSuccess  by mutableStateOf(false)
    var isSaving     by mutableStateOf(false)
    var hasDecoyPin  by mutableStateOf(false)
        private set

    fun loadDecoyPinStatus(uid: String) {
        viewModelScope.launch {
            runCatching {
                val user = firestoreRepository.getUserById(uid)
                hasDecoyPin = user?.decoyPinHash?.isNotBlank() == true
            }
        }
    }

    fun openDialog() {
        newPin = ""; confirmPin = ""; errorMessage = null; saveSuccess = false
        showDialog = true
    }

    fun dismissDialog() { showDialog = false }

    fun save(uid: String, mismatchMsg: String, sameAsRealMsg: String) {
        if (newPin != confirmPin) { errorMessage = mismatchMsg; return }
        if (newPin.length < 6)    { return }
        errorMessage = null
        isSaving = true
        viewModelScope.launch {
            runCatching {
                val user = firestoreRepository.getUserById(uid)
                // Guard: decoy PIN must not match the real PIN
                if (user != null && user.salt.isNotBlank() &&
                    pinHasher.verify(newPin, user.salt, user.pinHash)) {
                    errorMessage = sameAsRealMsg
                    isSaving = false
                    return@runCatching
                }
                val salt = pinHasher.generateSalt()
                val hash = pinHasher.hash(newPin, salt)
                firestoreRepository.setDecoyPin(uid, hash, salt)
                vaultRepository.log("DECOY_PIN_SET", "uid=$uid")
                saveSuccess = true
                hasDecoyPin = true
                showDialog  = false
            }.onFailure {
                errorMessage = it.message
            }
            isSaving = false
        }
    }

    fun dismissSuccess() { saveSuccess = false }
}
