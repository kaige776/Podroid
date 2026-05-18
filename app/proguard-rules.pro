# Podroid ProGuard rules

# TerminalView fields set directly from TerminalScreen to wire the session.
-keepclassmembers class com.termux.view.TerminalView {
    public com.termux.terminal.TerminalSession mTermSession;
    public com.termux.terminal.TerminalEmulator mEmulator;
}

# TerminalSession.mEmulator replaced via reflection in TerminalViewModel
# to install a no-op TerminalOutput (prevents CPR garbage in the VM shell).
-keepclassmembers class com.termux.terminal.TerminalSession {
    com.termux.terminal.TerminalEmulator mEmulator;
}

# java.net.UnixDomainSocketAddress (JDK 16) is present on Android API 34+ at
# runtime but absent from the compile-time SDK stubs. ConsoleFanout uses it
# only on AVF/API-34 devices and is guarded by @RequiresApi(34). Suppress the
# R8 missing-class error so release builds succeed.
-dontwarn java.net.UnixDomainSocketAddress
