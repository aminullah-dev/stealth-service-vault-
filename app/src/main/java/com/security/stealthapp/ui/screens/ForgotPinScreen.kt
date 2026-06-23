package com.security.stealthapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.security.stealthapp.ui.theme.AvailableGreen
import com.security.stealthapp.ui.theme.ChipInactive
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.viewmodel.ForgotPinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPinScreen(
    onBack: () -> Unit,
    onLoginTapped: () -> Unit,
    viewModel: ForgotPinViewModel = hiltViewModel()
) {
    val strings     = LocalStrings.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        focusedLabelColor    = RoseGold,
        cursorColor          = RoseGold
    )

    DashboardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            strings.forgotPinTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 17.sp,
                            color      = DeepRose
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            },
            containerColor = ElegantCream
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ElegantCream, Color(0xFFF0D9DF))
                        )
                    )
                    .padding(padding)
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape  = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier            = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                strings.forgotPinSubtitle,
                                fontSize  = 13.sp,
                                color     = Color(0xFF666666),
                                textAlign = TextAlign.Start
                            )

                            OutlinedTextField(
                                value           = viewModel.phone,
                                onValueChange   = { viewModel.phone = it },
                                label           = { Text(strings.phoneNumber, fontSize = 13.sp) },
                                leadingIcon     = { Icon(Icons.Default.Phone, null, tint = RoseGold) },
                                singleLine      = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier        = Modifier.fillMaxWidth(),
                                shape           = RoundedCornerShape(12.dp),
                                colors          = fieldColors
                            )

                            if (viewModel.state is ForgotPinViewModel.State.Error) {
                                Text(
                                    (viewModel.state as ForgotPinViewModel.State.Error).message,
                                    fontSize = 12.sp,
                                    color    = Color(0xFFD32F2F)
                                )
                            }

                            if (viewModel.state == ForgotPinViewModel.State.NoEmail) {
                                Text(
                                    strings.forgotPinNoEmail,
                                    fontSize  = 13.sp,
                                    color     = Color(0xFF888888),
                                    textAlign = TextAlign.Start
                                )
                            }

                            Button(
                                onClick  = { viewModel.sendResetLink() },
                                enabled  = viewModel.state !is ForgotPinViewModel.State.Loading,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
                            ) {
                                if (viewModel.state is ForgotPinViewModel.State.Loading) {
                                    CircularProgressIndicator(
                                        color       = Color.White,
                                        modifier    = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        strings.forgotPinSendLink,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = onLoginTapped) {
                        Text("← ${strings.loginTitle}", color = RoseGold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // ── Email Sent dialog ─────────────────────────────────────────────────────
    if (viewModel.state == ForgotPinViewModel.State.EmailSent) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissState(); onBack() },
            icon  = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint     = AvailableGreen,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text(
                    strings.forgotPinEmailSentTitle,
                    fontWeight = FontWeight.Bold,
                    color      = DeepRose
                )
            },
            text = {
                Text(
                    strings.forgotPinEmailSentText,
                    fontSize  = 13.sp,
                    color     = Color(0xFF555555),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissState(); onBack() },
                    colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) { Text(strings.ok, color = Color.White) }
            },
            containerColor = ElegantCream
        )
    }
}
