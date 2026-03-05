package com.vectras.vm.setupwizard

import android.app.Activity
import android.os.Build
import com.vectras.qemu.MainSettingsManager
import com.vectras.vm.utils.PermissionUtils

class FirstRunPermissionOrchestrator(private val activity: Activity) {

    enum class Capability {
        STORAGE_SAF,
        NOTIFICATIONS,
        BATTERY_OPTIMIZATION,
        OVERLAY,
        MEDIA_ACCESS
    }

    enum class StepState {
        PENDING,
        GRANTED,
        SKIPPED,
        FAILED
    }

    data class CapabilityStep(
        val capability: Capability,
        val required: Boolean,
        val state: StepState
    )

    private val orderedCapabilities = listOf(
        Capability.STORAGE_SAF,
        Capability.NOTIFICATIONS,
        Capability.BATTERY_OPTIMIZATION,
        Capability.OVERLAY,
        Capability.MEDIA_ACCESS
    )

    fun getSteps(): List<CapabilityStep> {
        return orderedCapabilities
            .filter { isApplicable(it) }
            .map {
                CapabilityStep(
                    capability = it,
                    required = isRequired(it),
                    state = resolveState(it)
                )
            }
    }

    fun getNextPendingRequired(): Capability? {
        return getSteps().firstOrNull { it.required && it.state == StepState.PENDING }?.capability
    }

    fun areRequiredCapabilitiesGranted(): Boolean {
        return getSteps().none { it.required && it.state != StepState.GRANTED }
    }

    fun refreshPersistedStates() {
        for (capability in orderedCapabilities) {
            if (!isApplicable(capability)) {
                persistState(capability, StepState.SKIPPED)
                continue
            }

            if (isCapabilityGranted(capability)) {
                persistState(capability, StepState.GRANTED)
            } else {
                val current = getPersistedState(capability)
                if (current != StepState.FAILED && current != StepState.SKIPPED) {
                    persistState(capability, StepState.PENDING)
                }
            }
        }
    }

    fun markSkipped(capability: Capability) {
        persistState(capability, StepState.SKIPPED)
    }

    fun markFailed(capability: Capability) {
        persistState(capability, StepState.FAILED)
    }

    fun markGranted(capability: Capability) {
        persistState(capability, StepState.GRANTED)
    }

    private fun resolveState(capability: Capability): StepState {
        if (isCapabilityGranted(capability)) {
            persistState(capability, StepState.GRANTED)
            return StepState.GRANTED
        }

        return getPersistedState(capability)
    }

    private fun isCapabilityGranted(capability: Capability): Boolean {
        return when (capability) {
            Capability.STORAGE_SAF -> PermissionUtils.hasStorageCapability(activity)
            Capability.NOTIFICATIONS -> PermissionUtils.hasNotificationCapability(activity)
            Capability.BATTERY_OPTIMIZATION -> PermissionUtils.isBatteryOptimizationIgnored(activity)
            Capability.OVERLAY -> PermissionUtils.hasOverlayCapability(activity)
            Capability.MEDIA_ACCESS -> PermissionUtils.hasMediaReadCapability(activity)
        }
    }

    private fun isRequired(capability: Capability): Boolean {
        return capability == Capability.STORAGE_SAF
    }

    private fun isApplicable(capability: Capability): Boolean {
        return when (capability) {
            Capability.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            Capability.MEDIA_ACCESS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            Capability.OVERLAY -> {
                val vmUi = MainSettingsManager.getVmUi(activity)
                vmUi.equals("X11", ignoreCase = true) || vmUi.equals("VNC", ignoreCase = true)
            }
            else -> true
        }
    }

    private fun keyFor(capability: Capability): String {
        return "firstRunPermissionState_${capability.name}"
    }

    private fun persistState(capability: Capability, state: StepState) {
        MainSettingsManager.setFirstRunPermissionState(activity, keyFor(capability), state.name)
    }

    private fun getPersistedState(capability: Capability): StepState {
        val persisted = MainSettingsManager.getFirstRunPermissionState(activity, keyFor(capability))
        return try {
            StepState.valueOf(persisted)
        } catch (_: IllegalArgumentException) {
            StepState.PENDING
        }
    }
