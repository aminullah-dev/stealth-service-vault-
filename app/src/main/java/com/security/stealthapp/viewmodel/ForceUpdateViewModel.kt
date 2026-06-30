package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Checks Firestore platform_config/force_update on launch.
 * If the installed versionCode is below minVersionCode, exposes
 * the update URL so MainActivity can show a non-dismissible dialog.
 *
 * Firestore document layout:
 *   platform_config/force_update:
 *     minVersionCode : Number   (e.g. 5)
 *     minVersionName : String   (e.g. "1.1" — shown in the dialog)
 *     updateUrl      : String   (APK download or Play Store link)
 */
@HiltViewModel
class ForceUpdateViewModel @Inject constructor() : ViewModel() {

    data class UpdateInfo(
        val minVersionName: String,
        val updateUrl: String,
    )

    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    private val db = FirebaseFirestore.getInstance()

    fun check(currentVersionCode: Int) {
        viewModelScope.launch {
            runCatching {
                val snap = db.document("platform_config/force_update").get().await()
                if (!snap.exists()) return@launch
                val minCode = snap.getLong("minVersionCode")?.toInt() ?: return@launch
                if (currentVersionCode < minCode) {
                    updateInfo = UpdateInfo(
                        minVersionName = snap.getString("minVersionName") ?: "",
                        updateUrl      = snap.getString("updateUrl") ?: "",
                    )
                }
            }
            // Silent failure: if Firestore is unreachable, don't block the user.
        }
    }
}
