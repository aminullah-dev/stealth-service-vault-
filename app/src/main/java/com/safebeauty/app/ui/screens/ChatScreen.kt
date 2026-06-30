package com.safebeauty.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeauty.app.data.firebase.ChatMessage
import com.safebeauty.app.ui.theme.BlushPink
import com.safebeauty.app.ui.theme.DashboardSurface
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.ui.theme.DeepRose
import com.safebeauty.app.ui.theme.ElegantCream
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.RoseGold
import com.safebeauty.app.ui.theme.ChipInactive
import com.safebeauty.app.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val strings   = LocalStrings.current
    val messages  by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    DashboardTheme {
        Scaffold(
            containerColor = ElegantCream,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            viewModel.otherName,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp,
                            color      = DeepRose
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint               = DeepRose
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ElegantCream)
                )
            },
            bottomBar = {
                ChatInputBar(
                    draft          = viewModel.draft,
                    onDraftChanged = viewModel::onDraftChanged,
                    onSend         = viewModel::send,
                    placeholder    = strings.messagePlaceholder,
                    sendLabel      = strings.send
                )
            }
        ) { padding ->
            LazyColumn(
                state               = listState,
                contentPadding      = PaddingValues(
                    start  = 16.dp,
                    end    = 16.dp,
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier            = Modifier.fillMaxSize()
            ) {
                items(messages, key = { it.id.ifBlank { it.timestamp.toString() } }) { msg ->
                    ChatBubble(
                        message = msg,
                        isMine  = msg.senderId == viewModel.myUserId
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, isMine: Boolean) {
    val timeFmt   = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val bg        = if (isMine) RoseGold else DashboardSurface
    val fg        = if (isMine) Color.White else DeepRose
    val timeColor = if (isMine) Color.White.copy(alpha = 0.7f) else Color(0xFFAAAAAA)

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BlushPink)
                    .align(Alignment.Bottom)
            ) {
                Text(
                    message.senderName.firstOrNull()?.toString() ?: "?",
                    fontSize   = 14.sp,
                    color      = DeepRose,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier            = Modifier.widthIn(max = 260.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart    = 16.dp,
                            topEnd      = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd   = if (isMine) 4.dp else 16.dp
                        )
                    )
                    .background(bg)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(message.content, fontSize = 14.sp, color = fg)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                timeFmt.format(Date(message.timestamp)),
                fontSize = 10.sp,
                color    = timeColor
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    sendLabel: String
) {
    Surface(
        tonalElevation = 4.dp,
        color          = ElegantCream
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            OutlinedTextField(
                value         = draft,
                onValueChange = onDraftChanged,
                placeholder   = { Text(placeholder, fontSize = 14.sp, color = Color(0xFFAAAAAA)) },
                singleLine    = false,
                maxLines      = 4,
                modifier      = Modifier.weight(1f),
                shape         = RoundedCornerShape(24.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = RoseGold,
                    unfocusedBorderColor = BlushPink,
                    cursorColor          = RoseGold
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick  = onSend,
                enabled  = draft.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank()) RoseGold else ChipInactive)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = sendLabel,
                    tint               = Color.White,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
    }
}
