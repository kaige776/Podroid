/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 *
 * Picks the VM backend at service-construction time. On Pixel 8+ with
 * `adb pm grant` for MANAGE_VIRTUAL_MACHINE + USE_CUSTOM_VIRTUAL_MACHINE,
 * uses AVF (KVM). Otherwise falls back to QEMU/TCG. User can override
 * via Settings → Backend.
 */
package com.excp.podroid.engine

import android.content.Context
import com.excp.podroid.data.repository.SettingsRepository
import com.excp.podroid.engine.avf.AvfDiagnostics
import com.excp.podroid.engine.avf.AvfEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideVmEngine(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        qemuProvider: Provider<QemuEngine>,
        avfProvider: Provider<AvfEngine>,
    ): VmEngine {
        val selection = runBlocking { settings.getEngineSelectionSnapshot() }
        val probe = AvfDiagnostics.probe(context)
        val pick = when {
            selection == EngineSelection.QEMU -> qemuProvider.get()
            // AVF forced: no capability check on purpose. If the device can't run
            // AVF, AvfEngine will surface the error so the user knows; silent
            // fallback to QEMU would mask their explicit choice.
            selection == EngineSelection.AVF  -> avfProvider.get()
            probe.featureSupported &&
                probe.managePermissionGranted &&
                probe.customPermissionGranted -> avfProvider.get()
            else -> qemuProvider.get()
        }
        android.util.Log.i("EngineModule", "selected engine=${pick.backendId} selection=$selection feature=${probe.featureSupported} perms=${probe.managePermissionGranted}/${probe.customPermissionGranted}")
        return pick
    }
}
