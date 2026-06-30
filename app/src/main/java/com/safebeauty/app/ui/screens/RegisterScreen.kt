package com.safebeauty.app.ui.screens

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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeauty.app.ui.theme.AvailableGreen
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.UnavailableGrey
import com.safebeauty.app.viewmodel.LanguageViewModel
import com.safebeauty.app.viewmodel.RegisterViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
    langVm: LanguageViewModel    = hiltViewModel()
) {
    LaunchedEffect(viewModel.state) {
        // Navigation is handled via dialog dismiss
    }

    val strings         = LocalStrings.current
    val currentLanguage by langVm.language.collectAsStateWithLifecycle()
    var showLangPicker  by remember { mutableStateOf(false) }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text       = strings.createAccount,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 20.sp,
                                color      = DeepRose
                            )
                            Text(
                                text     = strings.taglineRegister,
                                fontSize = 11.sp,
                                color    = RoseGold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = DeepRose)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showLangPicker = true }) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = RoseGold)
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Hero icon ─────────────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(BlushPink)
                    ) {
                        Icon(
                            imageVector        = if (viewModel.isProvider) Icons.Default.Storefront else Icons.Default.Person,
                            contentDescription = null,
                            tint               = DeepRose,
                            modifier           = Modifier.size(36.dp)
                        )
                    }
                }

                // ── Role toggle ───────────────────────────────────────────────
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = if (viewModel.isProvider) strings.roleProvider else strings.roleCustomer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp,
                                color      = DeepRose
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text     = if (viewModel.isProvider) strings.roleProviderDesc else strings.roleCustomerDesc,
                                fontSize = 12.sp,
                                color    = RoseGold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
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
                }

                // ── Personal information card ──────────────────────────────────
                SectionCard(title = strings.sectionPersonalInfo) {
                    FormField(
                        value         = viewModel.name,
                        onValueChange = { viewModel.name = it },
                        label         = strings.fullName,
                        leadingIcon   = Icons.Default.Person
                    )
                    FormField(
                        value         = viewModel.phone,
                        onValueChange = { viewModel.phone = it },
                        label         = strings.phoneNumber,
                        leadingIcon   = Icons.Default.Phone,
                        keyboard      = KeyboardType.Phone
                    )
                    FormField(
                        value         = viewModel.email,
                        onValueChange = { viewModel.email = it },
                        label         = strings.emailAddress,
                        leadingIcon   = Icons.Default.Email,
                        keyboard      = KeyboardType.Email
                    )
                    FormField(
                        value         = viewModel.pin,
                        onValueChange = { viewModel.pin = it },
                        label         = strings.secretPin,
                        leadingIcon   = Icons.Default.Lock,
                        keyboard      = KeyboardType.NumberPassword,
                        password      = true
                    )
                    FormField(
                        value         = viewModel.confirmPin,
                        onValueChange = { viewModel.confirmPin = it },
                        label         = strings.confirmPin,
                        leadingIcon   = Icons.Default.Lock,
                        keyboard      = KeyboardType.NumberPassword,
                        password      = true
                    )
                }

                // ── Provider-only: salon details card ─────────────────────────
                if (viewModel.isProvider) {
                    SectionCard(title = strings.sectionSalonDetails) {
                        FormField(
                            value         = viewModel.salonName,
                            onValueChange = { viewModel.salonName = it },
                            label         = strings.salonName,
                            leadingIcon   = Icons.Default.Storefront
                        )
                        FormField(
                            value         = viewModel.district,
                            onValueChange = { viewModel.district = it },
                            label         = strings.districtArea,
                            leadingIcon   = Icons.Default.LocationOn
                        )

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
                                            containerColor    = ChipInactive,
                                            labelColor        = DeepRose,
                                            trailingIconColor = RoseGold
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value         = viewModel.serviceInput,
                                onValueChange = { viewModel.serviceInput = it },
                                label         = { Text(strings.addServiceHint, fontSize = 12.sp) },
                                leadingIcon   = { Icon(Icons.Default.ContentCut, null, tint = RoseGold, modifier = Modifier.size(18.dp)) },
                                singleLine    = true,
                                modifier      = Modifier.weight(1f),
                                shape         = RoundedCornerShape(12.dp),
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
                                Icon(Icons.Default.Add, contentDescription = strings.addServiceHint, tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Register button ───────────────────────────────────────────
                Button(
                    onClick  = { viewModel.register() },
                    enabled  = viewModel.state !is RegisterViewModel.RegisterState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) {
                    Text(
                        text = when (viewModel.state) {
                            is RegisterViewModel.RegisterState.Loading -> strings.creatingAccount
                            else -> strings.createAccount
                        },
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Success / error dialogs ───────────────────────────────────────────
        when (val s = viewModel.state) {
            is RegisterViewModel.RegisterState.CustomerSuccess -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissState(); onBack() },
                    icon = {
                        Icon(Icons.Default.CheckCircle, null, tint = AvailableGreen, modifier = Modifier.size(40.dp))
                    },
                    title = { Text(strings.welcomeTitle(s.name), fontWeight = FontWeight.Bold, color = DeepRose) },
                    text  = { Text(strings.accountReadyText, fontSize = 14.sp, color = Color(0xFF555555)) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissState(); onBack() },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text(strings.goToApp, color = Color.White) }
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
                    title = { Text(strings.registrationSubmittedTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                    text  = { Text(strings.salonUnderReviewText, fontSize = 14.sp, color = Color(0xFF555555)) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.dismissState(); onBack() },
                            colors  = ButtonDefaults.buttonColors(containerColor = RoseGold)
                        ) { Text(strings.gotIt, color = Color.White) }
                    },
                    containerColor = ElegantCream
                )
            }
            is RegisterViewModel.RegisterState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissState() },
                    title = { Text(strings.pleaseCheckTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                    text  = { Text(s.message, fontSize = 14.sp, color = Color(0xFF555555)) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissState() }) {
                            Text(strings.ok, color = RoseGold)
                        }
                    },
                    containerColor = ElegantCream
                )
            }
            else -> Unit
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

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardSurface),
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseGold)
            HorizontalDivider(color = BlushPink)
            content()
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
    leadingIcon: ImageVector? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value               = value,
        onValueChange       = onValueChange,
        label               = { Text(label, fontSize = 13.sp) },
        leadingIcon         = if (leadingIcon != null) {
            { Icon(leadingIcon, null, tint = RoseGold, modifier = Modifier.size(18.dp)) }
        } else null,
        singleLine          = true,
        keyboardOptions     = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else
            androidx.compose.ui.text.input.VisualTransformation.None,
        modifier            = Modifier.fillMaxWidth(),
        shape               = RoundedCornerShape(12.dp),
        colors              = fieldColors()
    )
}
