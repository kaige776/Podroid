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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DesktopWindows
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.excp.podroid.x11.X11Constants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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

    // SurfaceView size — captured in surfaceChanged so the touch-coord scaler
    // uses the real on-screen pixel size of the view, not a MotionEvent device
    // axis range (which doesn't necessarily match the view bounds).
    var svWidth by remember { mutableStateOf(1) }
    var svHeight by remember { mutableStateOf(1) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("X11") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onNavigateToTerminal) {
                    Icon(
                        Icons.Default.DesktopWindows,
                        contentDescription = "Terminal",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val state = connection) {
                X11ConnectionState.Connecting,
                X11ConnectionState.Disconnected -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Connecting to X11 server...",
                        modifier = Modifier.padding(top = 80.dp),
                        color = Color.White,
                    )
                }
                is X11ConnectionState.Failed -> {
                    Text(
                        "X11 server not ready — VM still booting?\n${state.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                X11ConnectionState.Connected -> {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInteropFilter { ev ->
                                val sx = (ev.x / svWidth.coerceAtLeast(1).toFloat() *
                                    X11Constants.FB_WIDTH).toInt()
                                    .coerceIn(0, X11Constants.FB_WIDTH - 1)
                                val sy = (ev.y / svHeight.coerceAtLeast(1).toFloat() *
                                    X11Constants.FB_HEIGHT).toInt()
                                    .coerceIn(0, X11Constants.FB_HEIGHT - 1)
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
                            // Re-blit on every frameCounter tick.
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
                                val dst = Rect(0, 0, sv.width, sv.height)
                                canvas.drawBitmap(bitmap, null, dst, null)
                            } finally {
                                holder.unlockCanvasAndPost(canvas)
                            }
                        },
                    )
                }
            }
        }
    }
}
