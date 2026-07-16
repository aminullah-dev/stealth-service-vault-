package com.safebeauty.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.TextStrong
import com.safebeauty.app.ui.theme.TextFaint

private const val SUPPORT_EMAIL = "Aminhashemi979@gmail.com"
private const val TERMS_URL   = "https://safebeauty.web.app/terms"
private const val PRIVACY_URL = "https://safebeauty.web.app/privacy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    fun emailUs() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, strings.supportEmailSubject)
        }
        val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
        if (!ok) {
            Toast.makeText(context, "${strings.supportNoEmailApp} $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
        }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = { Text(strings.supportTitle, fontWeight = FontWeight.Bold, color = DeepRose) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RoseGold)
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
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Hero badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFC98490), Color(0xFF8B3A47))),
                            CircleShape
                        )
                ) {
                    Icon(Icons.Default.SupportAgent, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }

                Text(
                    strings.supportIntro,
                    fontSize = 14.sp,
                    color = TextStrong,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Email card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Email, null, tint = RoseGold, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Email", fontSize = 11.sp, color = RoseGold)
                            Text(SUPPORT_EMAIL, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DeepRose)
                        }
                    }
                }

                // Response time card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = RoseGold, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.supportHoursTitle, fontSize = 11.sp, color = RoseGold)
                            Text(strings.supportHoursText, fontSize = 14.sp, color = DeepRose)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { emailUs() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoseGold)
                ) {
                    Icon(Icons.Default.Email, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(strings.supportEmailButton, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Legal links
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DashboardSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        LegalLinkRow(
                            icon  = Icons.Default.Description,
                            label = strings.legalTermsLabel,
                            onClick = { openUrl(TERMS_URL) }
                        )
                        LegalLinkRow(
                            icon  = Icons.Default.PrivacyTip,
                            label = strings.legalPrivacyLabel,
                            onClick = { openUrl(PRIVACY_URL) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = RoseGold, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DeepRose, modifier = Modifier.weight(1f))
        Icon(Icons.Default.OpenInNew, null, tint = TextFaint, modifier = Modifier.size(16.dp))
    }
}
