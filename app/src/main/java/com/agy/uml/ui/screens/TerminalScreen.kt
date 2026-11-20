package com.agy.uml.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agy.uml.ui.theme.TerminalDarkBg
import com.agy.uml.ui.theme.TerminalGreen
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    outputFlow: SharedFlow<String>,
    onSendInput: (String) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>("=== User-Mode Linux Console ===") }
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(outputFlow) {
        outputFlow.collect { text ->
            lines.add(text)
            if (lines.size > 1000) {
                lines.removeRange(0, 100)
            }
            scope.launch {
                listState.animateScrollToItem(lines.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linux Terminal Shell", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { lines.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalDarkBg,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TerminalDarkBg)
        ) {
            // Main terminal output area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        color = TerminalGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Accessory Keys Bar (ESC, TAB, CTRL, ALT, |, /, -, ~, Up, Down)
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E26))
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AccessoryKeyButton("ESC") { onSendInput("\u001b") }
                AccessoryKeyButton("TAB") { onSendInput("\t") }
                AccessoryKeyButton("CTRL-C") { onSendInput("\u0003") }
                AccessoryKeyButton("CTRL-D") { onSendInput("\u0004") }
                AccessoryKeyButton("CTRL-Z") { onSendInput("\u001a") }
                AccessoryKeyButton("|") { onSendInput("|") }
                AccessoryKeyButton("/") { onSendInput("/") }
                AccessoryKeyButton("~") { onSendInput("~") }
                AccessoryKeyButton("-") { onSendInput("-") }
                AccessoryKeyButton("▲") { onSendInput("\u001b[A") }
                AccessoryKeyButton("▼") { onSendInput("\u001b[B") }
                AccessoryKeyButton("◀") { onSendInput("\u001b[D") }
                AccessoryKeyButton("▶") { onSendInput("\u001b[C") }
            }

            // Command Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181820))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Enter command...", color = Color.Gray, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            onSendInput(inputText + "\n")
                            inputText = ""
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendInput(inputText + "\n")
                            inputText = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AccessoryKeyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2A2A36),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(text = label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
