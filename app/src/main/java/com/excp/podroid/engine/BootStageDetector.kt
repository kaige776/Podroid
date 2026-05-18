/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 */
package com.excp.podroid.engine

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Engine-agnostic boot-progress detector. Both engines feed it raw guest
 * console bytes; it sets [bootStage] flow markers it observes and flips
 * [state] to Running when "Ready!" appears. Scans only the last 1024 chars
 * of an accumulating buffer to survive multi-byte split reads on fast
 * devices (see the history of detectBootStage in PodroidQemu pre-refactor).
 * One-shot: stops scanning after the first "Ready!" to keep [onReady] idempotent.
 */
class BootStageDetector(
    private val bootStage: MutableStateFlow<String>,
    private val state: MutableStateFlow<VmState>,
    private val onReady: () -> Unit,
) {
    private val buf = StringBuilder()
    private val maxScan = 1024
    private val maxKeep = 4096
    private var ready = false

    /**
     * Reset the one-shot guard + buffer. Called by engines at the start of
     * each VM run so a Stop → Start cycle's second boot can re-fire onReady.
     * Without this, the detector silently ignores the new boot stream because
     * `ready=true` from the previous run, and state stays Starting forever.
     */
    fun reset() {
        ready = false
        buf.clear()
    }

    fun feed(bytes: ByteArray, len: Int) {
        if (ready) return
        // Latin-1 decode is byte-safe (1 byte → 1 char) and the ASCII subset
        // matches UTF-8 exactly, so our pure-ASCII markers still match.
        buf.append(String(bytes, 0, len, Charsets.ISO_8859_1))
        if (buf.length > maxKeep) buf.delete(0, buf.length - maxKeep)
        val tail = if (buf.length > maxScan) buf.substring(buf.length - maxScan) else buf.toString()
        when {
            tail.contains("Ready!")                 -> { ready = true; bootStage.value = "Ready"; state.value = VmState.Running; onReady() }
            tail.contains("Almost ready")           -> bootStage.value = "Almost ready..."
            tail.contains("Starting SSH")           -> bootStage.value = "Starting SSH..."
            tail.contains("Configuring containers") -> bootStage.value = "Configuring containers..."
            tail.contains("Network found")          -> bootStage.value = "Network found"
            tail.contains("Loading kernel modules") -> bootStage.value = "Loading kernel modules..."
            tail.contains("Mounting storage")       -> bootStage.value = "Mounting storage..."
            tail.contains("Booting kernel")         -> bootStage.value = "Booting kernel..."
        }
    }
}
