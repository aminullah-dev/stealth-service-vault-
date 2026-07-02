package com.safebeauty.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeauty.app.ui.theme.AvailableGreen
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.util.ImageUtils
import com.safebeauty.app.viewmodel.KycViewModel
import kotlinx.coroutines.launch

/**
 * Identity-verification screen. Renders one of four states based on the user's
 * live kycStatus: the submission form (NONE/REJECTED), an "under review"
 * message (PENDING), or a "verified" message (APPROVED). A rejected user sees
 * the reason and can resubmit.
 */
@Composable
fun KycScreen(
    onDone: () -> Unit,
    viewModel: KycViewModel = hiltViewModel()
) {
    val strings = LocalStrings.current
    val user by viewModel.user.collectAsStateWithLifecycle()
    val status = user?.kycStatus ?: "NONE"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElegantCream)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(strings.kycTitle, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = DeepRose)

            when (status) {
                "PENDING"  -> StatusCard(
                    icon = Icons.Default.HourglassEmpty, tint = RoseGold,
                    title = strings.kycPendingTitle, body = strings.kycPendingText
                )
                "APPROVED" -> {
                    StatusCard(
                        icon = Icons.Default.CheckCircle, tint = AvailableGreen,
                        title = strings.kycApprovedTitle, body = strings.kycApprovedText
                    )
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                    ) { Text(strings.ok, color = Color.White, fontWeight = FontWeight.Bold) }
                }
                else       -> SubmitForm(viewModel, status)
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(48.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DeepRose)
            Text(body, fontSize = 14.sp, color = Color(0xFF555555))
        }
    }
}

@Composable
private fun SubmitForm(viewModel: KycViewModel, status: String) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        focusedLabelColor    = RoseGold,
        cursorColor          = RoseGold
    )

    Text(strings.kycSubtitle, fontSize = 14.sp, color = Color(0xFF555555))

    // If a previous submission was rejected, show the admin's reason.
    if (status == "REJECTED") {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFB00020), modifier = Modifier.size(20.dp))
                Text(
                    strings.kycRejectedReason(viewModel.user.collectAsStateWithLifecycle().value?.kycRejectionReason ?: ""),
                    fontSize = 13.sp, color = Color(0xFFB00020)
                )
            }
        }
    }

    OutlinedTextField(
        value = viewModel.tazkiraNumber,
        onValueChange = { viewModel.tazkiraNumber = it },
        label = { Text(strings.kycTazkiraNumber, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors
    )
    OutlinedTextField(
        value = viewModel.addressProvince,
        onValueChange = { viewModel.addressProvince = it },
        label = { Text(strings.kycProvince, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors
    )
    OutlinedTextField(
        value = viewModel.addressDetail,
        onValueChange = { viewModel.addressDetail = it },
        label = { Text(strings.kycAddressDetail, fontSize = 13.sp) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors
    )

    // Tazkira photo — from gallery.
    val tazkiraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val r = ImageUtils.uriToCompressedBytes(context, uri)) {
                is ImageUtils.BytesResult.Success -> viewModel.tazkiraBytes = r.bytes
                else -> {}
            }
        }
    }
    PhotoRow(
        label = strings.kycTazkiraPhoto,
        actionLabel = strings.kycPickPhoto,
        icon = Icons.Default.PhotoCamera,
        ready = viewModel.tazkiraBytes != null,
        onClick = { tazkiraPicker.launch("image/*") }
    )

    // Selfie — from the camera.
    val selfieCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
            viewModel.selfieBytes = out.toByteArray()
        }
    }
    PhotoRow(
        label = strings.kycSelfie,
        actionLabel = strings.kycTakeSelfie,
        icon = Icons.Default.CameraAlt,
        ready = viewModel.selfieBytes != null,
        onClick = { selfieCamera.launch(null) }
    )

    val submitState = viewModel.submitState
    (submitState as? KycViewModel.SubmitState.Error)?.let {
        Text(it.message, color = Color(0xFFB00020), fontSize = 13.sp)
    }

    Spacer(Modifier.height(4.dp))
    Button(
        onClick = { viewModel.submit() },
        enabled = submitState !is KycViewModel.SubmitState.Submitting,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
    ) {
        if (submitState is KycViewModel.SubmitState.Submitting) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(
                if (status == "REJECTED") strings.kycResubmit else strings.kycSubmit,
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun PhotoRow(
    label: String,
    actionLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ready: Boolean,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DeepRose)
                if (ready) {
                    Text(strings.kycPhotoReady, fontSize = 12.sp, color = AvailableGreen)
                }
            }
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(icon, null, tint = RoseGold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(actionLabel, color = RoseGold, fontSize = 13.sp)
            }
        }
    }
}
