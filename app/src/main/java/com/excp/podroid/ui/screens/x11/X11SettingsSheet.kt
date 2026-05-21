/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 *
 * Bottom sheet for X11 viewer settings: resolution mode/preset/custom
 * and rotation lock. TOUCH (Phase 4) and DISPLAY (Phase 5) sections
 * will be appended to the Column below the rotation section.
 */
package com.excp.podroid.ui.screens.x11

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.excp.podroid.ui.components.PodroidChipColors
import com.excp.podroid.ui.components.PodroidSectionLabel
import com.excp.podroid.ui.components.PodroidSwitch
import com.excp.podroid.ui.theme.PodroidTokens
import com.excp.podroid.x11.ResolutionMode
import com.excp.podroid.x11.ResolutionPreset
import com.excp.podroid.x11.RotationLock
import com.excp.podroid.x11.TouchMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun X11SettingsSheet(viewModel: X11ViewModel, onDismiss: () -> Unit) {
    val s by viewModel.x11Settings.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = PodroidTokens.Spacing.XL)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── RESOLUTION ────────────────────────────────────────────
            PodroidSectionLabel("Resolution")

            Row(
                horizontalArrangement = Arrangement.spacedBy(PodroidTokens.Spacing.SM),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ResolutionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = s.resolutionMode == mode,
                        onClick = { viewModel.setResolutionMode(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ResolutionMode.MATCH  -> "Match viewport"
                                    ResolutionMode.PRESET -> "Preset"
                                    ResolutionMode.CUSTOM -> "Custom"
                                }
                            )
                        },
                        shape = RoundedCornerShape(PodroidTokens.Radius.Chip),
                        colors = PodroidChipColors(),
                    )
                }
            }

            if (s.resolutionMode == ResolutionMode.PRESET) {
                Spacer(Modifier.height(PodroidTokens.Spacing.SM))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PodroidTokens.Spacing.SM),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ResolutionPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = s.preset == preset,
                            onClick = { viewModel.setPreset(preset) },
                            label = {
                                Text(
                                    when (preset) {
                                        ResolutionPreset.R720P  -> "720p"
                                        ResolutionPreset.R900P  -> "900p"
                                        ResolutionPreset.R1080P -> "1080p"
                                        ResolutionPreset.R1440P -> "1440p"
                                    }
                                )
                            },
                            shape = RoundedCornerShape(PodroidTokens.Radius.Chip),
                            colors = PodroidChipColors(),
                        )
                    }
                }
            }

            if (s.resolutionMode == ResolutionMode.CUSTOM) {
                Spacer(Modifier.height(PodroidTokens.Spacing.SM))
                CustomResolutionFields(
                    initialW = s.customW,
                    initialH = s.customH,
                    onCommit = { w, h -> viewModel.setCustom(w, h) },
                )
            }

            // ── ROTATION ──────────────────────────────────────────────
            PodroidSectionLabel("Rotation")

            Row(
                horizontalArrangement = Arrangement.spacedBy(PodroidTokens.Spacing.SM),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RotationLock.entries.forEach { lock ->
                    FilterChip(
                        selected = s.rotationLock == lock,
                        onClick = { viewModel.setRotation(lock) },
                        label = {
                            Text(
                                when (lock) {
                                    RotationLock.AUTO      -> "Auto"
                                    RotationLock.LANDSCAPE -> "Landscape"
                                    RotationLock.PORTRAIT  -> "Portrait"
                                }
                            )
                        },
                        shape = RoundedCornerShape(PodroidTokens.Radius.Chip),
                        colors = PodroidChipColors(),
                    )
                }
            }

            // ── TOUCH ─────────────────────────────────────────────────
            PodroidSectionLabel("Touch")

            Row(
                horizontalArrangement = Arrangement.spacedBy(PodroidTokens.Spacing.SM),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TouchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = s.touchMode == mode,
                        onClick = { viewModel.setTouchMode(mode) },
                        label = { Text(if (mode == TouchMode.DIRECT) "Direct touch" else "Trackpad") },
                        shape = RoundedCornerShape(PodroidTokens.Radius.Chip),
                        colors = PodroidChipColors(),
                    )
                }
            }

            Spacer(Modifier.height(PodroidTokens.Spacing.SM))
            Text(
                "Pointer speed: ${"%.1f".format(s.trackpadSensitivity)}x",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = s.trackpadSensitivity,
                onValueChange = { viewModel.setTrackpadSensitivity(it) },
                valueRange = 0.5f..3.0f,
                enabled = s.touchMode == TouchMode.TRACKPAD,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pointer acceleration", color = MaterialTheme.colorScheme.onSurface)
                PodroidSwitch(
                    checked = s.trackpadAccel,
                    onCheckedChange = { viewModel.setTrackpadAccel(it) },
                    enabled = s.touchMode == TouchMode.TRACKPAD,
                )
            }

            // ── DISPLAY ───────────────────────────────────────────────
            PodroidSectionLabel("Display")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show extra keys", color = MaterialTheme.colorScheme.onSurface)
                PodroidSwitch(
                    checked = s.showExtraKeys,
                    onCheckedChange = { viewModel.setShowExtraKeys(it) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Start in fullscreen", color = MaterialTheme.colorScheme.onSurface)
                PodroidSwitch(
                    checked = s.fullscreenDefault,
                    onCheckedChange = { viewModel.setFullscreenDefault(it) },
                )
            }

            Spacer(Modifier.height(PodroidTokens.Spacing.SM))
            Text("Server DPI", color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(PodroidTokens.Spacing.XS))
            Row(
                horizontalArrangement = Arrangement.spacedBy(PodroidTokens.Spacing.SM),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(96, 120, 144, 168, 192).forEach { dpi ->
                    FilterChip(
                        selected = s.dpi == dpi,
                        onClick = { viewModel.setDpi(dpi) },
                        label = { Text("${dpi}") },
                        shape = RoundedCornerShape(PodroidTokens.Radius.Chip),
                        colors = PodroidChipColors(),
                    )
                }
            }
            Text(
                "Applies on next VM start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(PodroidTokens.Spacing.XL2))
        }
    }
}

@Composable
private fun CustomResolutionFields(
    initialW: Int,
    initialH: Int,
    onCommit: (Int, Int) -> Unit,
) {
    var wText by remember(initialW) { mutableStateOf(initialW.toString()) }
    var hText by remember(initialH) { mutableStateOf(initialH.toString()) }

    // Commit only on IME Done or focus loss, not on every keystroke. Typing
    // "1920" used to fire four SetDesktopSize renegotiations (1, 19, 192, 1920);
    // a partial value also produced a tiny intermediate desktop. The downstream
    // setter clamps width/height (sane max and <= 0xFFFF) so a wrap is impossible.
    fun commit() {
        val w = wText.toIntOrNull() ?: return
        val h = hText.toIntOrNull() ?: return
        onCommit(w, h)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(PodroidTokens.Spacing.MD),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = wText,
            onValueChange = { wText = it },
            label = { Text("Width") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused) commit() },
        )
        OutlinedTextField(
            value = hText,
            onValueChange = { hText = it },
            label = { Text("Height") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused) commit() },
        )
    }
}
