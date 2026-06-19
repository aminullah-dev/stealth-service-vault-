package com.security.stealthapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.security.stealthapp.ui.theme.AvailableGreen
import com.security.stealthapp.ui.theme.BlushPink
import com.security.stealthapp.ui.theme.ChipActive
import com.security.stealthapp.ui.theme.ChipInactive
import com.security.stealthapp.ui.theme.DashboardSurface
import com.security.stealthapp.ui.theme.DashboardTheme
import com.security.stealthapp.ui.theme.DeepRose
import com.security.stealthapp.ui.theme.ElegantCream
import com.security.stealthapp.ui.theme.RoseGold
import com.security.stealthapp.ui.theme.UnavailableGrey
import com.security.stealthapp.viewmodel.RegisterViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    // Handle success states → navigate back to notepad after showing dialog
    val state = viewModel.state
    LaunchedEffect(state) {
        // Nothing auto-navigates: user must dismiss the success dialog first
    }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text       = "Create Account",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                color      = DeepRose
                            )
                            Text(
                                text     = "Private · Secure · Discreet",
                                fontSize = 11.sp,
                                color    = RoseGold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = DeepRose)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Role toggle ───────────────────────────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DashboardSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text       = if (viewModel.isProvider) "Service Provider" else "Customer",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp,
                            color      = DeepRose
                        )
                        Text(
                            text     = if (viewModel.isProvider)
                                "Register your salon (pending admin approval)"
                            else
                                "Find and book beauty services",
                            fontSize = 12.sp,
                            color    = RoseGold
                        )
                    }
                    Switch(
                        checked         = viewModel.isProvider,
                        onCheckedChange = { viewModel.isProvider = it },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = Color.White,
                            checkedTrackColor   = RoseGold,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = UnavailableGrey.copy(alpha = 0.4f)
                        )
                    )
                }

                HorizontalDivider(color = BlushPink)

                // ── Common fields ─────────────────────────────────────────────
                FormField(
                    value         = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label         = "Full Name"
                )
                FormField(
                    value         = viewModel.phone,
                    onValueChange = { viewModel.phone = it },
                    label         = "Phone Number",
                    keyboard      = KeyboardType.Phone
                )
                FormField(
                    value         = viewModel.pin,
                    onValueChange = { viewModel.pin = it },
                    label         = "Secret PIN (4+ digits)",
                    keyboard      = KeyboardType.NumberPassword,
                    password      = true
                )
                FormField(
                    value         = viewModel.confirmPin,
                    onValueChange = { viewModel.confirmPin = it },
                    label         = "Confirm PIN",
                    keyboard      = KeyboardType.NumberPassword,
                    password      = true
                )

                // ── Provider-only fields ──────────────────────────────────────
                if (viewModel.isProvider) {
                    HorizontalDivider(color = BlushPink)

                    Text(
                        text       = "Salon Details",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        color      = DeepRose
                    )

                    FormField(
                        value         = viewModel.salonName,
                        onValueChange = { viewModel.salonName = it },
                        label         = "Salon Name"
                    )
                    FormField(
                        value         = viewModel.district,
                        onValueChange = { viewModel.district = it },
                        label         = "District / Area"
                    )

                    // Services chip list
                    if (viewModel.services.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(4.dp),
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            viewModel.services.forEach { service ->
                                InputChip(
                                    selected     = false,
                                    onClick      = {},
                                    label        = { Text(service, fontSize = 12.sp) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick  = { viewModel.removeService(service) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(12.dp))
                                        }
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = ChipInactive,
                                        labelColor     = DeepRose,
                                        trailingIconColor = RoseGold
                                    )
                                )
                            }
                        }
                    }

                    // Add service row
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value         = viewModel.serviceInput,
                            onValueChange = { viewModel.serviceInput = it },
                            label         = { Text("Add service (e.g. Hair)", fontSize = 12.sp) },
                            singleLine    = true,
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(10.dp),
                            colors        = fieldColors()
                        )
                        IconButton(
                            onClick  = { viewModel.addService() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (viewModel.serviceInput.isNotBlank()) RoseGold else ChipInactive
                                )
                        ) {
                            Icon(Icons.Default.Add, "Add service", tint = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Register button ───────────────────────────────────────────
                Button(
                    onClick  = { viewModel.register() },
                    enabled  = viewModel.state !is RegisterViewModel.RegisterState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) {
                    Text(
                        text = when (viewModel.state) {
                            is RegisterViewModel.RegisterState.Loading -> "Creating account…"
                            else -> "Create Account"
                        },
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Success dialogs ───────────────────────────────────────────────────
        when (val s = viewModel.state) {
            is RegisterViewModel.RegisterState.CustomerSuccess -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissState(); onBack() },
                    icon = {
                        Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp))
                    },
                    title = { Text("Welcome, ${s.name}!", fontWeight = FontWeight.Bold, color = DeepRose) },
                    text  = { Text("Your account is ready. Return to the notepad and enter your PIN to sign in.", fontSize = 14.sp, color = Color(0xFF555555)) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissState(); onBack() },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text("Go to app", color = Color.White) }
                    },
                    containerColor = ElegantCream
                )
            }
            is RegisterViewModel.RegisterState.ProviderPending -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissState(); onBack() },
                    icon = {
                        Icon(Icons.Default.CheckCircle, null, tint = RoseGold, modifier = Modifier.size(40.dp))
                    },
                    title = { Text("Registration Submitted", fontWeight = FontWeight.Bold, color = DeepRose) },
                    text  = { Text("Your salon is under review. Once approved by our team, your PIN will activate and you can sign in.", fontSize = 14.sp, color = Color(0xFF555555)) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissState(); onBack() },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text("Got it", color = Color.White) }
                    },
                    containerColor = ElegantCream
                )
            }
            is RegisterViewModel.RegisterState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissState() },
                    title = { Text("Please check your details", fontWeight = FontWeight.Bold, color = DeepRose) },
                    text  = { Text(s.message, fontSize = 14.sp, color = Color(0xFF555555)) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissState() }) {
                            Text("OK", color = RoseGold)
                        }
                    },
                    containerColor = ElegantCream
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = RoseGold,
    unfocusedBorderColor = ChipInactive,
    focusedLabelColor    = RoseGold,
    cursorColor          = RoseGold
)

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value                  = value,
        onValueChange          = onValueChange,
        label                  = { Text(label, fontSize = 13.sp) },
        singleLine             = true,
        keyboardOptions        = KeyboardOptions(keyboardType = keyboard),
        visualTransformation   = if (password) PasswordVisualTransformation() else
            androidx.compose.ui.text.input.VisualTransformation.None,
        modifier               = Modifier.fillMaxWidth(),
        shape                  = RoundedCornerShape(12.dp),
        colors                 = fieldColors()
    )
}
