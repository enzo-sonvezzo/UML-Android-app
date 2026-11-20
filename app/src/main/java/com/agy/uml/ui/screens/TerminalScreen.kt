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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.regex.Matcher
import java.util.regex.Pattern

// Polished One Dark / Modern terminal color palette
private val TermBgColor = Color(0xFF1E1E24)
private val TermHeaderColor = Color(0xFF16161C)
private val TermInputBgColor = Color(0xFF252530)
private val TermDefaultText = Color(0xFFDCDFE4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    outputFlow: SharedFlow<String>,
    onSendInput: (String) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<AnnotatedString>(
        parseAnsiToAnnotated("\u001b[1;36m=== User-Mode Linux ARM64 Terminal Console ===\u001b[0m")
    ) }
    var inputText by remember { mutableStateOf("") }
    var pendingBuffer by remember { mutableStateOf("") }
    var autoScrollEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(outputFlow) {
        outputFlow.collect { rawChunk ->
            val cleaned = cleanAnsi(rawChunk)
            val combined = pendingBuffer + cleaned
            val split = combined.split("\r\n", "\n")

            if (split.size > 1) {
                for (i in 0 until split.size - 1) {
                    val rawLine = split[i]
                    // If line had standalone \r (progress bar or prompt redraw), take the last segment
                    val finalLine = if (rawLine.contains('\r')) {
                        rawLine.substringAfterLast('\r')
                    } else {
                        rawLine
                    }
                    if (lines.size > 2500) {
                        lines.removeRange(0, 150)
                    }
                    lines.add(parseAnsiToAnnotated(finalLine))
                }
                pendingBuffer = split.last()
            } else {
                pendingBuffer = combined
            }

            if (autoScrollEnabled) {
                scope.launch {
                    val target = (lines.size + (if (pendingBuffer.isNotEmpty()) 1 else 0) - 1).coerceAtLeast(0)
                    listState.scrollToItem(target)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF2E7D32), RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Linux Terminal Shell (bash)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { autoScrollEnabled = !autoScrollEnabled }) {
                        Icon(
                            imageVector = if (autoScrollEnabled) Icons.Default.VerticalAlignBottom else Icons.Default.Pause,
                            contentDescription = "Auto Scroll",
                            tint = if (autoScrollEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    IconButton(onClick = {
                        lines.clear()
                        pendingBuffer = ""
                        lines.add(parseAnsiToAnnotated("\u001b[1;33m[Console Cleared]\u001b[0m"))
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TermHeaderColor,
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
                .background(TermBgColor)
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                }

                // If there is an active prompt or command line being typed without trailing newline
                val displayPending = if (pendingBuffer.contains('\r')) pendingBuffer.substringAfterLast('\r') else pendingBuffer
                if (displayPending.isNotEmpty()) {
                    item {
                        Text(
                            text = parseAnsiToAnnotated(displayPending),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Polished Accessory Keys Bar with Command Navigation Arrows
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TermHeaderColor)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AccessoryKeyButton("ESC") { onSendInput("\u001b") }
                AccessoryKeyButton("TAB") { onSendInput("\t") }
                AccessoryKeyButton("CTRL-C") { onSendInput("\u0003") }
                AccessoryKeyButton("CTRL-D") { onSendInput("\u0004") }
                AccessoryKeyButton("CTRL-Z") { onSendInput("\u001a") }
                AccessoryKeyButton("CTRL-L") { onSendInput("\u000c") }
                AccessoryKeyButton("▲ (Up)") { onSendInput("\u001b[A") }
                AccessoryKeyButton("▼ (Down)") { onSendInput("\u001b[B") }
                AccessoryKeyButton("◀") { onSendInput("\u001b[D") }
                AccessoryKeyButton("▶") { onSendInput("\u001b[C") }
                AccessoryKeyButton("|") { onSendInput("|") }
                AccessoryKeyButton("/") { onSendInput("/") }
                AccessoryKeyButton("~") { onSendInput("~") }
                AccessoryKeyButton("-") { onSendInput("-") }
                AccessoryKeyButton("$") { onSendInput("$") }
            }

            // Command Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TermInputBgColor)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type Linux command...", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF3F3F50),
                        focusedContainerColor = Color(0xFF1E1E28),
                        unfocusedContainerColor = Color(0xFF1E1E28)
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
            containerColor = Color(0xFF2C2C3A),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text = label, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

/**
 * Robust ANSI / VT100 Cleaner:
 * Keeps SGR color codes ('m') while safely removing non-color CSI controls
 * (cursor moves, bracketed paste ?2004h/l, erase lines, etc.) and OSC window sequences.
 */
private val CSI_PATTERN: Pattern = Pattern.compile("\u001b\\[([?0-9;]*)([a-zA-Z])")
private val OSC_PATTERN: Pattern = Pattern.compile("\u001b\\][^\u0007\u001b]*(\u0007|\u001b\\\\)")
private val CHARSET_PATTERN: Pattern = Pattern.compile("\u001b[()][0-9A-Za-z]")

fun cleanAnsi(input: String): String {
    // 1. Strip OSC sequences
    var s = OSC_PATTERN.matcher(input).replaceAll("")
    // 2. Strip charset shifts
    s = CHARSET_PATTERN.matcher(s).replaceAll("")
    // 3. Strip bells, nulls
    s = s.replace("\u0007", "").replace("\u0000", "")

    // 4. Filter CSI sequences: keep 'm' (colors/styling), discard cursor/mode commands
    val matcher = CSI_PATTERN.matcher(s)
    val sb = StringBuffer()
    while (matcher.find()) {
        val cmd = matcher.group(2)
        if (cmd == "m") {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0) ?: ""))
        } else {
            matcher.appendReplacement(sb, "")
        }
    }
    matcher.appendTail(sb)
    return sb.toString()
}

/**
 * ANSI Color Parser converting SGR color sequences into a styled Compose AnnotatedString
 */
private val ANSI_COLOR_PATTERN: Pattern = Pattern.compile("\u001b\\[([0-9;]*)m")

fun parseAnsiToAnnotated(raw: String): AnnotatedString {
    return buildAnnotatedString {
        val matcher = ANSI_COLOR_PATTERN.matcher(raw)
        var lastEnd = 0

        var currentColor = TermDefaultText
        var isBold = false

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            if (start > lastEnd) {
                val plainText = raw.substring(lastEnd, start)
                val textStart = length
                append(plainText)
                addStyle(
                    SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    ),
                    textStart,
                    length
                )
            }

            val codes = matcher.group(1)?.split(";") ?: emptyList()
            for (code in codes) {
                when (code) {
                    "", "0" -> {
                        currentColor = TermDefaultText
                        isBold = false
                    }
                    "1" -> isBold = true
                    "22" -> isBold = false
                    "30" -> currentColor = Color(0xFF5C6370) // Black / Gray
                    "31" -> currentColor = Color(0xFFE06C75) // Red
                    "32" -> currentColor = Color(0xFF98C379) // Green
                    "33" -> currentColor = Color(0xFFE5C07B) // Yellow
                    "34" -> currentColor = Color(0xFF61AFEF) // Blue
                    "35" -> currentColor = Color(0xFFC678DD) // Magenta
                    "36" -> currentColor = Color(0xFF56B6C2) // Cyan
                    "37" -> currentColor = Color(0xFFDCDFE4) // White
                    "90" -> currentColor = Color(0xFF7F848E) // Bright black
                    "91" -> currentColor = Color(0xFFFF6B6B) // Bright red
                    "92" -> currentColor = Color(0xFF69DB7C) // Bright green
                    "93" -> currentColor = Color(0xFFFFD43B) // Bright yellow
                    "94" -> currentColor = Color(0xFF4DABF7) // Bright blue
                    "95" -> currentColor = Color(0xFFDA77F2) // Bright magenta
                    "96" -> currentColor = Color(0xFF3BC9DB) // Bright cyan
                    "97" -> currentColor = Color(0xFFFFFFFF) // Bright white
                }
            }

            lastEnd = end
        }

        if (lastEnd < raw.length) {
            val plainText = raw.substring(lastEnd)
            val textStart = length
            append(plainText)
            addStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                ),
                textStart,
                length
            )
        }
    }
}
