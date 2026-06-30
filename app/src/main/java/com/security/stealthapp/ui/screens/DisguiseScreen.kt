package com.security.stealthapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.security.stealthapp.data.model.LoggedInUser
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.NotepadBg
import com.security.stealthapp.ui.theme.NotepadLines
import com.security.stealthapp.ui.theme.NotepadPrimary
import com.security.stealthapp.ui.theme.NotepadSecondary
import com.security.stealthapp.ui.theme.NotepadSurface
import com.security.stealthapp.ui.theme.NotepadTheme
import com.security.stealthapp.viewmodel.AuthViewModel
import com.security.stealthapp.viewmodel.DisguiseNote
import com.security.stealthapp.viewmodel.DisguiseViewModel
import com.security.stealthapp.viewmodel.LanguageViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DisguiseScreen(
    onAuthSuccess: (LoggedInUser) -> Unit = {},
    onRegisterTapped: () -> Unit = {},
    noteViewModel: DisguiseViewModel = hiltViewModel(),
    authViewModel: AuthViewModel     = hiltViewModel(),
    langVm: LanguageViewModel        = hiltViewModel()
) {
    val authState = authViewModel.authState
    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.Success) {
            authViewModel.resetState()
            onAuthSuccess(authState.user)
        }
    }

    val strings         = LocalStrings.current
    val currentLanguage by langVm.language.collectAsStateWithLifecycle()

    // Seed a few believable starter notes on first ever open (localized).
    LaunchedEffect(Unit) {
        noteViewModel.seedIfEmpty(
            listOf(
                strings.notepadSeed1Title to strings.notepadSeed1Body,
                strings.notepadSeed2Title to strings.notepadSeed2Body,
                strings.notepadSeed3Title to strings.notepadSeed3Body,
            )
        )
    }

    val notes = noteViewModel.notes
    var searchQuery    by remember { mutableStateOf("") }
    var openNoteId     by remember { mutableStateOf<Long?>(null) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var showMenu       by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val openNote = notes.find { it.id == openNoteId }

    val visibleNotes = if (searchQuery.isBlank()) notes
        else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.body.contains(searchQuery, ignoreCase = true)
        }

    NotepadTheme {
        Scaffold(
            containerColor = NotepadBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text       = strings.notepadTitle,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                            fontSize   = 22.sp,
                            color      = NotepadPrimary,
                            modifier   = Modifier.combinedClickable(onClick = {}, onLongClick = {})
                        )
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, null, tint = NotepadPrimary)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, null, tint = NotepadPrimary)
                            }
                            DropdownMenu(
                                expanded         = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text    = { Text(strings.notepadSortByDate, fontSize = 14.sp, color = NotepadPrimary) },
                                    onClick = { showMenu = false; noteViewModel.sortByDate() }
                                )
                                DropdownMenuItem(
                                    text    = { Text(strings.notepadNewAccount, fontSize = 14.sp, color = NotepadPrimary) },
                                    onClick = { showMenu = false; onRegisterTapped() }
                                )
                                DropdownMenuItem(
                                    text    = { Text(strings.languagePickerTitle, fontSize = 14.sp, color = NotepadPrimary) },
                                    onClick = { showMenu = false; showLangPicker = true }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NotepadBg)
                )
            },
            floatingActionButton = {
                if (openNote == null) {
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

                // ── Search bar — also the hidden PIN entry point ──────────────
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        if (q.length in 4..8 && q.all { it.isDigit() }) {
                            authViewModel.authenticate(q)
                        }
                    },
                    placeholder = {
                        Text(strings.notepadSearchHint, color = Color.Gray, fontSize = 14.sp)
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

                if (openNote != null) {
                    NoteEditorPanel(
                        title     = openNote.title,
                        content   = noteViewModel.noteContent,
                        backLabel = strings.notepadBack,
                        deleteLabel = strings.notepadDelete,
                        onChanged = { text ->
                            noteViewModel.onNoteContentChanged(text)
                            noteViewModel.updateNoteBody(openNote.id, text)
                        },
                        onDelete  = { noteViewModel.deleteNote(openNote.id); openNoteId = null },
                        onBack    = { openNoteId = null },
                        modifier  = Modifier.fillMaxSize()
                    )
                } else if (visibleNotes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(strings.notepadEmpty, color = Color(0xFF9E9E9E), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(visibleNotes, key = { it.id }) { note ->
                            NoteListRow(
                                note    = note,
                                dateText = dateFmt.format(Date(note.updatedAt)),
                                onClick = {
                                    noteViewModel.onNoteContentChanged(note.body)
                                    openNoteId = note.id
                                }
                            )
                            HorizontalDivider(color = NotepadLines.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            if (showAddDialog) {
                AddNoteDialog(
                    titleHint   = strings.notepadNoteTitleHint,
                    heading     = strings.notepadNewNote,
                    createLabel = strings.notepadCreate,
                    cancelLabel = strings.cancel,
                    untitled    = strings.notepadUntitled,
                    onDismiss   = { showAddDialog = false },
                    onConfirm   = { title ->
                        showAddDialog = false
                        noteViewModel.onNoteContentChanged("")
                        openNoteId = noteViewModel.addNote(title)
                    }
                )
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

// ── Private composable helpers ────────────────────────────────────────────────

@Composable
private fun NoteListRow(note: DisguiseNote, dateText: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(NotepadBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
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
            Text(
                text = note.body.ifBlank { " " },
                fontSize = 12.sp,
                color = Color(0xFF757575),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = dateText, fontSize = 10.sp, color = Color(0xFFBDBDBD))
    }
}

@Composable
private fun NoteEditorPanel(
    title: String,
    content: String,
    backLabel: String,
    deleteLabel: String,
    onChanged: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = NotepadLines
    Column(modifier = modifier.background(NotepadBg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().background(NotepadSurface).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text     = "← $backLabel",
                    fontSize = 14.sp,
                    color    = NotepadSecondary,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NotepadPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = deleteLabel, tint = NotepadSecondary)
            }
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
private fun AddNoteDialog(
    titleHint: String,
    heading: String,
    createLabel: String,
    cancelLabel: String,
    untitled: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(heading, fontFamily = FontFamily.Serif) },
        text             = {
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text(titleHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton    = {
            TextButton(onClick = { onConfirm(titleInput.trim().ifBlank { untitled }) }) {
                Text(createLabel)
            }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
        containerColor   = NotepadBg
    )
}
