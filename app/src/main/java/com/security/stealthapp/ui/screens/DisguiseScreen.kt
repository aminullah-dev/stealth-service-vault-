package com.security.stealthapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.security.stealthapp.data.db.entities.User
import com.security.stealthapp.ui.theme.NotepadBg
import com.security.stealthapp.ui.theme.NotepadLines
import com.security.stealthapp.ui.theme.NotepadPrimary
import com.security.stealthapp.ui.theme.NotepadSecondary
import com.security.stealthapp.ui.theme.NotepadSurface
import com.security.stealthapp.ui.theme.NotepadTheme
import com.security.stealthapp.viewmodel.AuthViewModel
import com.security.stealthapp.viewmodel.DisguiseViewModel

// ── Static mock data ──────────────────────────────────────────────────────────

private data class MockNote(val id: Int, val title: String, val body: String, val date: String)

private val MOCK_NOTES = listOf(
    MockNote(1, "Grocery List",         "Tomatoes, onions, naan bread, yogurt, rice, lentils, cooking oil, cardamom…",   "Today"),
    MockNote(2, "Mom's Bolani Recipe",  "Dough: 2 cups flour, ½ tsp salt, warm water. Fill with potato & leek. Pan-fry.", "Yesterday"),
    MockNote(3, "Phone Numbers",        "Doctor Ahmadi: 0700-112233\nSchool: 0700-445566\nNeighbour Fatima: 0799-887766", "Jun 15"),
    MockNote(4, "Reminders",            "- Pick up package from post office\n- Pay electricity bill before the 20th",      "Jun 12"),
    MockNote(5, "Monthly Budget",       "Rent: 8,000 AFN\nFood: 4,000 AFN\nSchool fees: 1,500 AFN\nTransport: 800 AFN",   "Jun 05"),
    MockNote(6, "Reading List",         "1. Khaled Hosseini – A Thousand Splendid Suns\n2. Rumi – Masnavi",               "Jun 01")
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DisguiseScreen(
    onAuthSuccess: (User) -> Unit,
    noteViewModel: DisguiseViewModel = hiltViewModel(),
    authViewModel: AuthViewModel     = hiltViewModel()
) {
    // Consume the one-shot auth success produced by AuthViewModel.
    val authState = authViewModel.authState
    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.Success) {
            authViewModel.resetState()
            onAuthSuccess(authState.user)
        }
    }

    var searchQuery    by remember { mutableStateOf("") }
    var selectedNoteId by remember { mutableStateOf(MOCK_NOTES.first().id) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var showEditor     by remember { mutableStateOf(false) }

    val selectedNote  = MOCK_NOTES.find { it.id == selectedNoteId } ?: MOCK_NOTES.first()
    val visibleNotes  = remember(searchQuery) {
        if (searchQuery.isBlank()) MOCK_NOTES
        else MOCK_NOTES.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.body.contains(searchQuery, ignoreCase = true)
        }
    }

    NotepadTheme {
        Scaffold(
            containerColor = NotepadBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text       = "My Notes",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                            fontSize   = 22.sp,
                            color      = NotepadPrimary,
                            // Long-press falls through to auth with no-op PIN ""
                            // (won't match any stored PIN — purely visual affordance).
                            modifier   = Modifier.combinedClickable(onClick = {}, onLongClick = {})
                        )
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, null, tint = NotepadPrimary)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreVert, null, tint = NotepadPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NotepadBg)
                )
            },
            floatingActionButton = {
                if (!showEditor) {
                    FloatingActionButton(
                        onClick        = { showAddDialog = true },
                        containerColor = NotepadPrimary
                    ) {
                        Icon(Icons.Default.NoteAlt, null, tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                // ── Search bar — also the PIN entry point ─────────────────────
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        // Attempt auth on every 4-digit numeric entry.
                        // PBKDF2 is fast at low iteration count; the device
                        // never shows any error on non-matching input.
                        if (q.length == 4 && q.all { it.isDigit() }) {
                            authViewModel.authenticate(q)
                        }
                    },
                    placeholder = {
                        Text("Search notes…", color = Color.Gray, fontSize = 14.sp)
                    },
                    leadingIcon  = {
                        Icon(Icons.Default.Search, null, tint = Color.Gray)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = NotepadSecondary,
                        unfocusedBorderColor    = NotepadLines,
                        focusedContainerColor   = NotepadSurface,
                        unfocusedContainerColor = NotepadSurface
                    )
                )

                if (showEditor) {
                    NoteEditorPanel(
                        title     = selectedNote.title,
                        content   = noteViewModel.noteContent,
                        onChanged = noteViewModel::onNoteContentChanged,
                        onBack    = { showEditor = false },
                        modifier  = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(visibleNotes, key = { it.id }) { note ->
                            NoteListRow(
                                note       = note,
                                isSelected = note.id == selectedNoteId,
                                onClick    = {
                                    selectedNoteId = note.id
                                    noteViewModel.onNoteContentChanged(note.body)
                                    showEditor = true
                                }
                            )
                            HorizontalDivider(color = NotepadLines.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddNoteDialog(onDismiss = { showAddDialog = false }, onConfirm = { showAddDialog = false })
            }
        }
    }
}

// ── Private composable helpers ────────────────────────────────────────────────

@Composable
private fun NoteListRow(note: MockNote, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) NotepadSurface else NotepadBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isSelected) NotepadSecondary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = note.title,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = NotepadPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(text = note.body, fontSize = 12.sp, color = Color(0xFF757575), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(text = note.date, fontSize = 10.sp, color = Color(0xFFBDBDBD))
    }
}

@Composable
private fun NoteEditorPanel(
    title: String,
    content: String,
    onChanged: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = NotepadLines
    Column(modifier = modifier.background(NotepadBg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(NotepadSurface).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text       = "← Notes",
                fontSize   = 14.sp,
                color      = NotepadSecondary,
                modifier   = Modifier.clickable(onClick = onBack).padding(end = 8.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = NotepadPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = NotepadLines)
        BasicTextField(
            value         = content,
            onValueChange = onChanged,
            textStyle     = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Color(0xFF333333), lineHeight = 28.sp),
            cursorBrush   = SolidColor(NotepadSecondary),
            modifier      = Modifier
                .fillMaxSize()
                .padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                .drawBehind {
                    val sp = 28.dp.toPx(); var y = sp
                    while (y < size.height) {
                        drawLine(lineColor, Offset(-52.dp.toPx(), y), Offset(size.width, y), strokeWidth = 0.8f)
                        y += sp
                    }
                    drawLine(Color(0xFFEF9A9A).copy(alpha = 0.6f), Offset(-36.dp.toPx(), 0f), Offset(-36.dp.toPx(), size.height), strokeWidth = 1.2f)
                }
        )
    }
}

@Composable
private fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var titleInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("New Note", fontFamily = FontFamily.Serif) },
        text             = { OutlinedTextField(value = titleInput, onValueChange = { titleInput = it }, label = { Text("Note title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton    = { TextButton(onClick = onConfirm) { Text("Create") } },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor   = NotepadBg
    )
}
