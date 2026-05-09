/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 */
package com.excp.podroid.ui.screens.x11

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.excp.podroid.x11.X11Constants
import kotlinx.coroutines.delay

// X11 keysyms used outside the label table.
private const val XK_BackSpace = 0xFF08
private const val XK_Tab       = 0xFF09
private const val XK_Return    = 0xFF0D
private const val XK_Escape    = 0xFF1B
private const val XK_Left      = 0xFF51
private const val XK_Up        = 0xFF52
private const val XK_Right     = 0xFF53
private const val XK_Down      = 0xFF54
private const val XK_Control_L = 0xFFE3
private const val XK_Alt_L     = 0xFFE9

/**
 * Maps the human-readable label used by [X11ExtraKeysRow] (matching the
 * terminal's ExtraKeysRow vocabulary) to an X11 keysym. Returns null for
 * pure modifier labels (CTRL/ALT) — those are handled as toggles.
 */
private fun labelToKeysym(label: String): Int? = when (label) {
    "ESC"     -> XK_Escape
    "TAB"     -> XK_Tab
    "LEFT"    -> XK_Left
    "RIGHT"   -> XK_Right
    "UP"      -> XK_Up
    "DOWN"    -> XK_Down
    "HOME"    -> 0xFF50
    "END"     -> 0xFF57
    "PGUP"    -> 0xFF55
    "PGDN"    -> 0xFF56
    "F1"      -> 0xFFBE
    "F2"      -> 0xFFBF
    "F3"      -> 0xFFC0
    "F4"      -> 0xFFC1
    "F5"      -> 0xFFC2
    "F6"      -> 0xFFC3
    "F7"      -> 0xFFC4
    "F8"      -> 0xFFC5
    "F9"      -> 0xFFC6
    "F10"     -> 0xFFC7
    "F11"     -> 0xFFC8
    "F12"     -> 0xFFC9
    "-"       -> 0x2D
    "/"       -> 0x2F
    "|"       -> 0x7C
    else      -> null   // CTRL / ALT handled by toggles
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalComposeUiApi::class,
)
@Composable
fun X11Screen(
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    viewModel: X11ViewModel = hiltViewModel(),
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val frameCount by viewModel.frameCounter.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.connect() }

    val bitmap = remember {
        Bitmap.createBitmap(X11Constants.FB_WIDTH, X11Constants.FB_HEIGHT, Bitmap.Config.ARGB_8888)
    }

    var svWidth  by remember { mutableStateOf(1) }
    var svHeight by remember { mutableStateOf(1) }

    // Letterbox / pillarbox dst rect, pinned to top so the soft keyboard
    // (and the extra-keys row) live in the empty bottom strip.
    val (dstX, dstY, dstW, dstH) = remember(svWidth, svHeight) {
        val fbW = X11Constants.FB_WIDTH.toFloat()
        val fbH = X11Constants.FB_HEIGHT.toFloat()
        val viewW = svWidth.toFloat().coerceAtLeast(1f)
        val viewH = svHeight.toFloat().coerceAtLeast(1f)
        val scale = minOf(viewW / fbW, viewH / fbH)
        val dW = (fbW * scale).toInt().coerceAtLeast(1)
        val dH = (fbH * scale).toInt().coerceAtLeast(1)
        val dX = ((viewW - dW) / 2f).toInt()
        val dY = 0
        IntArray4(dX, dY, dW, dH)
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var imeBuf by remember { mutableStateOf(TextFieldValue("")) }

    // Sticky modifier state — tap CTRL once, the next key is sent with
    // Control_L held; the modifier auto-clears after that one keypress
    // (one-shot semantics, matches Termux convention).
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive  by remember { mutableStateOf(false) }

    fun sendWithModifiers(keysym: Int) {
        if (ctrlActive) viewModel.sendKey(XK_Control_L, down = true)
        if (altActive)  viewModel.sendKey(XK_Alt_L,     down = true)
        viewModel.sendKey(keysym, down = true)
        viewModel.sendKey(keysym, down = false)
        if (altActive)  viewModel.sendKey(XK_Alt_L,     down = false)
        if (ctrlActive) viewModel.sendKey(XK_Control_L, down = false)
        // One-shot: clear modifiers after one press.
        ctrlActive = false
        altActive  = false
    }

    fun onExtraKey(label: String) {
        when (label) {
            "CTRL" -> ctrlActive = !ctrlActive
            "ALT"  -> altActive  = !altActive
            else -> labelToKeysym(label)?.let(::sendWithModifiers)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("X11") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                }
                IconButton(onClick = onNavigateToTerminal) {
                    Icon(
                        Icons.Default.DesktopWindows,
                        contentDescription = "Terminal",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        when (val state = connection) {
            X11ConnectionState.Connecting,
            X11ConnectionState.Disconnected -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Connecting to X11 server...",
                        modifier = Modifier.padding(top = 80.dp),
                        color = Color.White,
                    )
                }
            }
            is X11ConnectionState.Failed -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "X11 server not ready — VM still booting?\n${state.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            X11ConnectionState.Connected -> {
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInteropFilter { ev ->
                            val w = dstW.coerceAtLeast(1)
                            val h = dstH.coerceAtLeast(1)
                            val sx = ((ev.x - dstX) / w * X11Constants.FB_WIDTH)
                                .toInt().coerceIn(0, X11Constants.FB_WIDTH - 1)
                            val sy = ((ev.y - dstY) / h * X11Constants.FB_HEIGHT)
                                .toInt().coerceIn(0, X11Constants.FB_HEIGHT - 1)
                            val mask = when (ev.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1
                                else -> 0
                            }
                            viewModel.sendPointer(sx, sy, mask)
                            true
                        },
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(h: SurfaceHolder) {}
                                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
                                    svWidth = w
                                    svHeight = hh
                                }
                                override fun surfaceDestroyed(h: SurfaceHolder) {}
                            })
                        }
                    },
                    update = { sv ->
                        @Suppress("UNUSED_EXPRESSION")
                        frameCount
                        bitmap.setPixels(
                            viewModel.framebuffer, 0,
                            X11Constants.FB_WIDTH,
                            0, 0,
                            X11Constants.FB_WIDTH, X11Constants.FB_HEIGHT,
                        )
                        val holder = sv.holder
                        val canvas = holder.lockCanvas() ?: return@AndroidView
                        try {
                            canvas.drawColor(android.graphics.Color.BLACK)
                            val dst = Rect(dstX, dstY, dstX + dstW, dstY + dstH)
                            canvas.drawBitmap(bitmap, null, dst, null)
                        } finally {
                            holder.unlockCanvasAndPost(canvas)
                        }
                    },
                )

                X11ExtraKeysRow(
                    onKey = ::onExtraKey,
                    ctrlActive = ctrlActive,
                    altActive  = altActive,
                )

                // Hidden IME hook (must stay in the layout while connected so
                // the requestFocus/show sequence has a target).
                BasicTextField(
                    value = imeBuf,
                    onValueChange = { new ->
                        forwardImeDiff(imeBuf.text, new.text, viewModel)
                        imeBuf = TextFieldValue("")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            viewModel.sendKey(XK_Return, down = true)
                            viewModel.sendKey(XK_Return, down = false)
                        },
                    ),
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val keysym = when (ev.key) {
                                Key.Backspace      -> XK_BackSpace
                                Key.Enter,
                                Key.NumPadEnter    -> XK_Return
                                Key.Tab            -> XK_Tab
                                Key.Escape         -> XK_Escape
                                Key.DirectionLeft  -> XK_Left
                                Key.DirectionRight -> XK_Right
                                Key.DirectionUp    -> XK_Up
                                Key.DirectionDown  -> XK_Down
                                else -> return@onPreviewKeyEvent false
                            }
                            viewModel.sendKey(keysym, down = true)
                            viewModel.sendKey(keysym, down = false)
                            true
                        },
                )
            }
        }
    }
}

/**
 * Same vocabulary as the terminal's ExtraKeysRow so muscle memory carries
 * over: ESC, TAB, CTRL, arrows, ALT, punctuation, HOME/END, PGUP/PGDN, F1–F12.
 * CTRL and ALT are sticky one-shot modifiers (highlighted while active).
 */
@Composable
private fun X11ExtraKeysRow(
    onKey: (String) -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        X11KeyButton("ESC", onKey)
        X11KeyButton("TAB", onKey)
        X11KeyButton("CTRL", onKey, isActive = ctrlActive)
        X11KeyButton("←", onKey, sendKey = "LEFT",  repeatable = true)
        X11KeyButton("↑", onKey, sendKey = "UP",    repeatable = true)
        X11KeyButton("↓", onKey, sendKey = "DOWN",  repeatable = true)
        X11KeyButton("→", onKey, sendKey = "RIGHT", repeatable = true)
        X11KeyButton("ALT", onKey, isActive = altActive)
        X11KeyButton("-", onKey)
        X11KeyButton("/", onKey)
        X11KeyButton("|", onKey)
        X11KeyButton("HOME", onKey)
        X11KeyButton("END", onKey)
        X11KeyButton("PGUP", onKey)
        X11KeyButton("PGDN", onKey)
        X11KeyButton("F1", onKey)
        X11KeyButton("F2", onKey)
        X11KeyButton("F3", onKey)
        X11KeyButton("F4", onKey)
        X11KeyButton("F5", onKey)
        X11KeyButton("F6", onKey)
        X11KeyButton("F7", onKey)
        X11KeyButton("F8", onKey)
        X11KeyButton("F9", onKey)
        X11KeyButton("F10", onKey)
        X11KeyButton("F11", onKey)
        X11KeyButton("F12", onKey)
    }
}

@Composable
private fun X11KeyButton(
    label: String,
    onKey: (String) -> Unit,
    sendKey: String = label,
    isActive: Boolean = false,
    repeatable: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, sendKey, repeatable) {
        if (!repeatable || !pressed) return@LaunchedEffect
        delay(400L)
        var interval = 70L
        while (pressed) {
            onKey(sendKey)
            delay(interval)
            if (interval > 30L) interval -= 3L
        }
    }
    val tapModifier = if (repeatable) {
        Modifier.pointerInput(sendKey) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onKey(sendKey)
                pressed = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    pressed = false
                }
            }
        }
    } else {
        Modifier.clickable { onKey(sendKey) }
    }
    Text(
        text = label,
        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .then(tapModifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * Compares old vs new IME buffer content, fires synthetic X11 key events
 * for the diff. Printable characters use their ASCII code as the keysym
 * (X11 keysyms 0x20–0x7E match ASCII verbatim).
 */
private fun forwardImeDiff(old: String, new: String, vm: X11ViewModel) {
    if (new.length > old.length) {
        new.substring(old.length).forEach { ch ->
            val keysym = ch.code
            vm.sendKey(keysym, down = true)
            vm.sendKey(keysym, down = false)
        }
    } else if (new.length < old.length) {
        repeat(old.length - new.length) {
            vm.sendKey(XK_BackSpace, down = true)
            vm.sendKey(XK_BackSpace, down = false)
        }
    }
}

private data class IntArray4(val a: Int, val b: Int, val c: Int, val d: Int)
