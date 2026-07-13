package com.safebeauty.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import com.safebeauty.app.R
import com.safebeauty.app.data.model.LoggedInUser
import com.safebeauty.app.security.BiometricVault
import com.safebeauty.app.security.RootDetector
import com.safebeauty.app.ui.components.GradientButton
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.Gradients
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.RosePetal
import com.safebeauty.app.viewmodel.AuthViewModel
import com.safebeauty.app.viewmodel.LanguageViewModel

@Composable
fun LoginScreen(
    onAuthSuccess: (LoggedInUser) -> Unit,
    onRegisterTapped: () -> Unit,
    onForgotPinTapped: () -> Unit = {},
    authViewModel: AuthViewModel   = hiltViewModel(),
    langVm: LanguageViewModel      = hiltViewModel()
) {
    val strings         = LocalStrings.current
    val currentLanguage by langVm.language.collectAsStateWithLifecycle()
    val authState       = authViewModel.authState
    val context         = LocalContext.current

    var phone          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var passwordShown  by remember { mutableStateOf(false) }
    var showError      by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }

    // Fingerprint login: available only with enrolled biometrics; "enabled" once
    // the user has stored their credential (biometric-gated, hardware-backed).
    val biometricAvailable = remember { BiometricVault.isAvailable(context) }
    var biometricEnabled   by remember { mutableStateOf(BiometricVault.isEnabled(context)) }
    var rememberFingerprint by remember { mutableStateOf(false) }

    val securityCheck = remember { RootDetector.check(context) }
    var showSecurityWarning by remember { mutableStateOf(securityCheck.isRisky) }

    val isAuthenticating = authState is AuthViewModel.AuthState.Authenticating

    LaunchedEffect(authState) {
        when (authState) {
            is AuthViewModel.AuthState.Success -> {
                val user = authState.user
                authViewModel.resetState()
                // The credential is only known-good now. If the user opted in,
                // store phone+password behind the biometric-gated Keystore key
                // (one biometric prompt to set it up), then continue either way.
                val activity = context.findFragmentActivity()
                if (rememberFingerprint && biometricAvailable && !biometricEnabled &&
                    activity != null && phone.isNotBlank() && password.isNotBlank()
                ) {
                    BiometricVault.enable(
                        activity     = activity,
                        pin          = "$phone\n$password",
                        title        = strings.biometricPromptTitle,
                        subtitle     = strings.biometricPromptSubtitle,
                        negativeText = strings.cancel,
                        onSuccess    = { onAuthSuccess(user) },
                        onError      = { onAuthSuccess(user) }
                    )
                } else {
                    onAuthSuccess(user)
                }
            }
            is AuthViewModel.AuthState.Failure -> {
                showError = true
                authViewModel.resetState()
            }
            else -> Unit
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RoseGold,
        unfocusedBorderColor = ChipInactive,
        focusedLabelColor    = RoseGold,
        cursorColor          = RoseGold
    )

    fun submit() {
        showError = false
        if (phone.isNotBlank() && password.isNotBlank()) {
            authViewModel.authenticate(phone, password)
        }
    }

    fun loginWithFingerprint() {
        val activity = context.findFragmentActivity() ?: return
        BiometricVault.unlock(
            activity     = activity,
            title        = strings.biometricPromptTitle,
            subtitle     = strings.biometricPromptSubtitle,
            negativeText = strings.cancel,
            onPin        = { secret ->
                // Stored as "phone\npassword"; phone never contains a newline.
                val idx = secret.indexOf('\n')
                if (idx > 0) {
                    val savedPhone    = secret.substring(0, idx)
                    val savedPassword = secret.substring(idx + 1)
                    phone = savedPhone
                    authViewModel.authenticate(savedPhone, savedPassword)
                }
            },
            onError      = {
                // Key invalidated by new enrollment → BiometricVault wiped it.
                biometricEnabled = BiometricVault.isEnabled(context)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Gradients.ScreenBg)
    ) {
        IconButton(
            onClick  = { showLangPicker = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) {
            Icon(Icons.Default.Language, strings.languagePickerTitle, tint = RoseGold, modifier = Modifier.size(24.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))

            // ── Logo ──────────────────────────────────────────────────────────
            val logoAlpha = remember { Animatable(0f) }
            LaunchedEffect(Unit) { logoAlpha.animateTo(1f, animationSpec = tween(700)) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .alpha(logoAlpha.value)
                    .size(100.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Gradients.Petal)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                strings.loginTitle,
                style = TextStyle(brush = Gradients.BrandRose, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            )
            Spacer(Modifier.height(6.dp))
            Text(strings.loginTagline, fontSize = 14.sp, color = RoseGold)

            // ── Delicate floral flourish ──────────────────────────────────────
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, RosePetal)))
                )
                Text(" ❀ ", fontSize = 15.sp, color = RosePetal)
                Box(
                    Modifier
                        .width(44.dp)
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(RosePetal, Color.Transparent)))
                )
            }

            Spacer(Modifier.height(30.dp))

            // ── Phone ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value         = phone,
                onValueChange = { phone = it; showError = false },
                label         = { Text(strings.loginPhoneLabel, fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Default.Phone, null, tint = RoseGold, modifier = Modifier.size(18.dp)) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                colors        = fieldColors
            )

            Spacer(Modifier.height(12.dp))

            // ── Password ──────────────────────────────────────────────────────
            OutlinedTextField(
                value         = password,
                onValueChange = { password = it; showError = false },
                label         = { Text(strings.loginPasswordLabel, fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Default.Lock, null, tint = RoseGold, modifier = Modifier.size(18.dp)) },
                trailingIcon  = {
                    IconButton(onClick = { passwordShown = !passwordShown }) {
                        Icon(
                            if (passwordShown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null, tint = ChipInactive, modifier = Modifier.size(20.dp)
                        )
                    }
                },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordShown) VisualTransformation.None else PasswordVisualTransformation(),
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                colors        = fieldColors
            )

            // ── Enable fingerprint for next time ──────────────────────────────
            if (biometricAvailable && !biometricEnabled) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { rememberFingerprint = !rememberFingerprint }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = rememberFingerprint,
                        onCheckedChange = { rememberFingerprint = it },
                        colors = CheckboxDefaults.colors(checkedColor = RoseGold)
                    )
                    Icon(Icons.Default.Fingerprint, null, tint = RoseGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.biometricEnableLabel, fontSize = 13.sp, color = DeepRose)
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text(
                text     = if (showError) strings.loginWrongPin else "",
                fontSize = 13.sp,
                color    = Color(0xFFD32F2F)
            )
            Spacer(Modifier.height(8.dp))

            // ── Login button ──────────────────────────────────────────────────
            if (isAuthenticating) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoseGold, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            } else {
                GradientButton(
                    text    = strings.loginButton,
                    onClick = { submit() },
                    enabled = phone.isNotBlank() && password.isNotBlank()
                )
            }

            // ── Fingerprint sign-in ───────────────────────────────────────────
            if (biometricEnabled && biometricAvailable && !isAuthenticating) {
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RoseGold.copy(alpha = 0.10f))
                        .clickable { loginWithFingerprint() }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, null, tint = RoseGold, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(strings.biometricLoginButton, fontSize = 14.sp, color = DeepRose, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = {
                    BiometricVault.disable(context)
                    biometricEnabled = false
                }) {
                    Text(strings.biometricDisable, fontSize = 12.sp, color = Color(0xFF999999))
                }
            }

            Spacer(Modifier.height(20.dp))

            TextButton(onClick = onRegisterTapped) {
                Text(strings.loginRegisterPrompt, fontSize = 14.sp, color = RoseGold)
            }
            TextButton(onClick = onForgotPinTapped) {
                Text(strings.forgotPin, fontSize = 13.sp, color = Color(0xFF999999))
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLangPicker) {
        LanguagePickerDialog(
            current   = currentLanguage,
            onPick    = { langVm.setLanguage(it); showLangPicker = false },
            onDismiss = { showLangPicker = false }
        )
    }

    if (showSecurityWarning) {
        val warningBody = buildString {
            if (securityCheck.isRooted) append(strings.securityWarningRooted)
            if (securityCheck.isRooted && securityCheck.isEmulator) append("\n\n")
            if (securityCheck.isEmulator) append(strings.securityWarningEmulator)
        }
        AlertDialog(
            onDismissRequest = { showSecurityWarning = false },
            title  = { Text(strings.securityWarningTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
            text   = { Text(warningBody, color = RoseGold, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { showSecurityWarning = false }) {
                    Text(strings.gotIt, color = DeepRose, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = ElegantCream
        )
    }
}

/** Unwraps a Compose [Context] to the hosting [FragmentActivity] (needed for BiometricPrompt). */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
