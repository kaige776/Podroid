/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 */
package com.excp.podroid.engine.avf

import android.annotation.SuppressLint
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import com.excp.podroid.engine.BootStageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.InputStream
import java.io.OutputStream

/**
 * Bridges AVF's console streams ↔ a filesystem unix-domain socket so the
 * existing `libpodroid-bridge.so` subprocess can splice PTY ↔ that socket
 * unchanged. Uses `android.system.Os` directly because Android 14's
 * ServerSocketChannel doesn't expose the ProtocolFamily factory needed for
 * NIO unix sockets, but Os.bind() accepts UnixDomainSocketAddress.
 *
 * AVF requires Android 14+ (Pixel 8+) so the @RequiresApi(34) on this class
 * is never violated at runtime — AvfEngine only constructs ConsoleFanout
 * when AvfDiagnostics says the framework is available.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ConsoleFanout(
    private val consoleOutput: InputStream,
    private val consoleInput: OutputStream,
    private val socketPath: String,
    private val detector: BootStageDetector,
    private val scope: CoroutineScope,
) {
    companion object { private const val TAG = "ConsoleFanout" }

    private var serverFd: FileDescriptor? = null
    private var clientFd: FileDescriptor? = null
    private val jobs = mutableListOf<Job>()
    @Volatile private var closed = false

    @SuppressLint("BlockedPrivateApi") // android.system.UnixSocketAddress is exempt via HiddenApiBypass
    fun start() {
        File(socketPath).delete()  // stale socket blocks bind

        val fd = Os.socket(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0)
        val addrCls = Class.forName("android.system.UnixSocketAddress")
        val addr = addrCls.getDeclaredMethod("createFileSystem", String::class.java)
            .apply { isAccessible = true }
            .invoke(null, socketPath) as java.net.SocketAddress
        Os.bind(fd, addr)
        Os.listen(fd, 1)
        serverFd = fd

        jobs += scope.launch(Dispatchers.IO) {
            try {
                val client = Os.accept(fd, null /* peerAddress out-param */)
                Log.d(TAG, "bridge connected at $socketPath")
                clientFd = client
                startPumps(client)
            } catch (e: Exception) {
                if (!closed) Log.w(TAG, "accept failed: ${e.message}")
            }
        }
    }

    private fun startPumps(client: FileDescriptor) {
        // VM → (boot detector + bridge socket)
        jobs += scope.launch(Dispatchers.IO) {
            val buf = ByteArray(8192)
            try {
                while (true) {
                    val n = consoleOutput.read(buf)
                    if (n <= 0) break
                    detector.feed(buf, n)
                    var off = 0
                    while (off < n) {
                        val w = Os.write(client, buf, off, n - off)
                        if (w <= 0) break
                        off += w
                    }
                }
            } catch (e: Exception) {
                if (!closed) Log.d(TAG, "vm→bridge pump ended: ${e.message}")
            } finally { close() }
        }

        // Bridge socket → VM
        jobs += scope.launch(Dispatchers.IO) {
            val buf = ByteArray(8192)
            try {
                while (true) {
                    val n = Os.read(client, buf, 0, buf.size)
                    if (n <= 0) break
                    consoleInput.write(buf, 0, n)
                    consoleInput.flush()
                }
            } catch (e: Exception) {
                if (!closed) Log.d(TAG, "bridge→vm pump ended: ${e.message}")
            } finally { close() }
        }
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        jobs.forEach { runCatching { it.cancel() } }
        jobs.clear()
        runCatching { clientFd?.let { Os.close(it) } }
        runCatching { serverFd?.let { Os.close(it) } }
        runCatching { consoleOutput.close() }
        runCatching { consoleInput.close() }
        runCatching { File(socketPath).delete() }
    }
}
