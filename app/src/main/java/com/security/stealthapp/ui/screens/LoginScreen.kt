package com.security.stealthapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.security.stealthapp.R
import com.security.stealthapp.data.model.LoggedInUser
import com.security.stealthapp.ui.theme.ChipInactive
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.viewmodel.AuthViewModel
import com.security.stealthapp.viewmodel.LanguageViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val PIN_LENGTH = 6

@Composable
fun LoginScreen(
    onAuthSuccess: (LoggedInUser) -> Unit,
    onDecoyMode: () -> Unit = {},
    onRegisterTapped: () -> Unit,
    onForgotPinTapped: () -> Unit = {},
    authViewModel: AuthViewModel   = hiltViewModel(),
    langVm: LanguageViewModel      = hiltViewModel()
) {
    val strings         = LocalStrings.current
    val currentLanguage by langVm.language.collectAsStateWithLifecycle()
    val authState       = authViewModel.authState

    var pin          by remember { mutableStateOf("") }
    var showError    by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }

    // Shake animation offset
    val shakeOffset = remember { Animatable(0f) }

    // Track whether we were Authenticating so we know if going to Idle is a failure
    var wasAuthenticating by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthViewModel.AuthState.Authenticating -> {
                wasAuthenticating = true
            }
            is AuthViewModel.AuthState.Success -> {
                authViewModel.resetState()
                onAuthSuccess(authState.user)
            }
            is AuthViewModel.AuthState.DecoyMode -> {
                authViewModel.resetState()
                onDecoyMode()
            }
            is AuthViewModel.AuthState.Failure -> {
                wasAuthenticating = false
                for (i in 0 until 3) {
                    shakeOffset.animateTo(12f, animationSpec = tween(60))
                    shakeOffset.animateTo(-12f, animationSpec = tween(60))
                }
                shakeOffset.animateTo(0f, animationSpec = tween(60))
                pin = ""
                showError = true
                authViewModel.resetState()
                delay(2000)
                showError = false
            }
            is AuthViewModel.AuthState.Idle -> {
                if (wasAuthenticating) {
                    wasAuthenticating = false
                    for (i in 0 until 3) {
                        shakeOffset.animateTo(12f, animationSpec = tween(60))
                        shakeOffset.animateTo(-12f, animationSpec = tween(60))
                    }
                    shakeOffset.animateTo(0f, animationSpec = tween(60))
                    pin = ""
                    showError = true
                    delay(2000)
                    showError = false
                }
            }
        }
    }

    DashboardTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ElegantCream)
        ) {
            // Language picker button — top-right
            IconButton(
                onClick  = { showLangPicker = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Language,
                    contentDescription = strings.languagePickerTitle,
                    tint               = RoseGold,
                    modifier           = Modifier.size(24.dp)
                )
            }

            Column(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.Center
            ) {

                // ── App logo (rose) ────────────────────────────────────────────
                val logoAlpha = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    logoAlpha.animateTo(1f, animationSpec = tween(700))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .alpha(logoAlpha.value)
                        .size(100.dp)
                        .shadow(12.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFFDF9),
                                    Color(0xFFFBEFEA),
                                    Color(0xFFF3D8DE)
                                )
                            )
                        )
                ) {
                    Image(
                        painter            = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier           = Modifier.size(100.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── App name ──────────────────────────────────────────────────
                Text(
                    text       = strings.loginTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 32.sp,
                    color      = DeepRose
                )

                Spacer(Modifier.height(6.dp))

                // ── Tagline ───────────────────────────────────────────────────
                Text(
                    text     = strings.loginTagline,
                    fontSize = 14.sp,
                    color    = RoseGold
                )

                Spacer(Modifier.height(40.dp))

                // ── PIN dots ──────────────────────────────────────────────────
                val isAuthenticating = authState is AuthViewModel.AuthState.Authenticating
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            color    = RoseGold,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            repeat(PIN_LENGTH) { index ->
                                val filled = index < pin.length
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .then(
                                            if (filled) {
                                                Modifier
                                                    .clip(CircleShape)
                                                    .background(DeepRose)
                                            } else {
                                                Modifier
                                                    .clip(CircleShape)
                                                    .border(2.dp, ChipInactive, CircleShape)
                                            }
                                        )
                                )
                            }
                        }
                    }
                }

                // ── Error text ────────────────────────────────────────────────
                Spacer(Modifier.height(12.dp))
                if (showError) {
                    Text(
                        text     = strings.loginWrongPin,
                        fontSize = 13.sp,
                        color    = Color(0xFFD32F2F)
                    )
                } else {
                    // Reserve space so layout doesn't jump
                    Text(
                        text     = "",
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(32.dp))

                // ── Keypad ────────────────────────────────────────────────────
                val keyRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    keyRows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier              = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "" -> {
                                        // Empty spacer cell — same size as a key
                                        Spacer(Modifier.size(72.dp))
                                    }
                                    "⌫" -> {
                                        // Backspace key
                                        Button(
                                            onClick  = {
                                                if (!isAuthenticating && pin.isNotEmpty()) {
                                                    pin = pin.dropLast(1)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(72.dp)
                                                .shadow(4.dp, CircleShape),
                                            shape    = CircleShape,
                                            colors   = ButtonDefaults.buttonColors(
                                                containerColor = DashboardSurface
                                            ),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                imageVector        = Icons.Default.Backspace,
                                                contentDescription = "Backspace",
                                                tint               = RoseGold,
                                                modifier           = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        // Digit key
                                        Button(
                                            onClick  = {
                                                if (!isAuthenticating && pin.length < PIN_LENGTH) {
                                                    pin += key
                                                    if (pin.length == PIN_LENGTH) {
                                                        authViewModel.authenticate(pin)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(72.dp)
                                                .shadow(4.dp, CircleShape),
                                            shape    = CircleShape,
                                            colors   = ButtonDefaults.buttonColors(
                                                containerColor = DashboardSurface
                                            ),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text       = key,
                                                fontSize   = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color      = DeepRose
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Register prompt ───────────────────────────────────────────
                TextButton(onClick = onRegisterTapped) {
                    Text(
                        text     = strings.loginRegisterPrompt,
                        fontSize = 14.sp,
                        color    = RoseGold
                    )
                }
                TextButton(onClick = onForgotPinTapped) {
                    Text(
                        text     = strings.forgotPin,
                        fontSize = 13.sp,
                        color    = Color(0xFF999999)
                    )
                }
            }
        }

        if (showLangPicker) {
            LanguagePickerDialog(
                current   = currentLanguage,
                onPick    = { langVm.setLanguage(it); showLangPicker = false },
                onDismiss = { showLangPicker = false }
            )
        }
    }
}
