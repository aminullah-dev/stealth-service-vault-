package com.safebeauty.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.NeutralGrey
import com.safebeauty.app.ui.theme.RoseGold

/**
 * Shows a KYC photo inside the app, from its Storage path.
 *
 * It replaces an Intent(ACTION_VIEW) on a Firebase download URL. That URL
 * carried a token, which meant it was served without authentication and
 * storage.rules never saw the request — so reviewing a customer's identity card
 * handed the phone's browser a permanent public link to a photograph of her
 * tazkira, and left it in the browser history and in Chrome sync.
 *
 * Fetching the bytes through the SDK instead sends the reviewer's own ID token,
 * so the rules decide, and the image never acquires an address anyone can pass
 * on. The bytes stay in this composable and go when the dialog closes.
 */
@Composable
fun KycPhotoDialog(
    storagePath: String,
    title: String,
    load: suspend (String) -> ByteArray,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var bytes by remember(storagePath) { mutableStateOf<ByteArray?>(null) }
    var failed by remember(storagePath) { mutableStateOf(false) }

    LaunchedEffect(storagePath) {
        if (storagePath.isBlank()) { failed = true; return@LaunchedEffect }
        runCatching { load(storagePath) }
            .onSuccess { bytes = it }
            .onFailure { failed = true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close, color = RoseGold) }
        },
        title = { Text(title, fontSize = 15.sp) },
        text = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            ) {
                val data = bytes
                when {
                    failed -> Text(
                        strings.kycPhotoUnavailable,
                        fontSize = 12.sp, color = NeutralGrey,
                        modifier = Modifier.padding(12.dp),
                    )
                    data == null -> CircularProgressIndicator(color = RoseGold)
                    else -> {
                        val bmp = remember(data) { BitmapFactory.decodeByteArray(data, 0, data.size) }
                        if (bmp == null) {
                            Text(
                                strings.kycPhotoUnavailable,
                                fontSize = 12.sp, color = NeutralGrey,
                                modifier = Modifier.padding(12.dp),
                            )
                        } else {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
    )
}
