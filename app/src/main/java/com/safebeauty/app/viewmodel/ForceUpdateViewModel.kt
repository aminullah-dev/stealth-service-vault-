package com.safebeauty.app.viewmodel

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
 * What the installed version should do about a newer one.
 *
 * Two levels, because they are different situations. A build that can no longer
 * be trusted — a broken payment path, a rule it does not satisfy — has to stop,
 * and that dialog cannot be dismissed. A build that merely has something newer
 * available should say so once and get out of the way; blocking someone from
 * booking a haircut because a nicer version exists is not a kindness.
 *
 * Firestore document layout:
 *   platform_config/force_update:
 *     minVersionCode    : Number  below this the app is blocked
 *     minVersionName    : String  shown in the blocking dialog
 *     latestVersionCode : Number  below this a dismissible banner appears
 *     latestVersionName : String  shown in the banner
 *     whatsNew          : Map     { en, fa, ps } — optional, one line
 *     updateUrl         : String  Play listing or APK
 */
@HiltViewModel
class ForceUpdateViewModel @Inject constructor() : ViewModel() {

    data class UpdateInfo(
        val minVersionName: String,
        val updateUrl: String,
    )

    /** A newer version exists, but this one still works. Dismissible. */
    data class UpdateAvailable(
        val versionName: String,
        val updateUrl: String,
        /** One line per language, as the console wrote it. May be empty. */
        val whatsNew: Map<String, String>,
    )

    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    var updateAvailable by mutableStateOf<UpdateAvailable?>(null)
        private set

    fun dismissUpdateBanner() { updateAvailable = null }

    private val db = FirebaseFirestore.getInstance()

    fun check(currentVersionCode: Int) {
        viewModelScope.launch {
            runCatching {
                val snap = db.document("platform_config/force_update").get().await()
                if (!snap.exists()) return@launch
                val url = snap.getString("updateUrl") ?: ""
                val minCode = snap.getLong("minVersionCode")?.toInt() ?: 0
                if (minCode > 0 && currentVersionCode < minCode) {
                    updateInfo = UpdateInfo(
                        minVersionName = snap.getString("minVersionName") ?: "",
                        updateUrl      = url,
                    )
                    // Blocked already; a banner underneath a dialog nobody can
                    // dismiss would be two messages about one thing.
                    return@launch
                }
                val latestCode = snap.getLong("latestVersionCode")?.toInt() ?: 0
                if (latestCode > 0 && currentVersionCode < latestCode) {
                    @Suppress("UNCHECKED_CAST")
                    val whatsNew = (snap.get("whatsNew") as? Map<String, Any?>)
                        ?.mapValues { (_, v) -> v?.toString().orEmpty() }
                        ?.filterValues { it.isNotBlank() }
                        ?: emptyMap()
                    updateAvailable = UpdateAvailable(
                        versionName = snap.getString("latestVersionName") ?: "",
                        updateUrl   = url,
                        whatsNew    = whatsNew,
                    )
                }
            }
            // Silent failure: if Firestore is unreachable, don't block the user.
        }
    }
}
