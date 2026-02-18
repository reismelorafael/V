package com.vectras.vm.core;

/**
 * Estados canônicos de fluxo operacional da VM.
 */
public enum VmFlowState {
    IDLE,
    CREATING,
    READY,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    DEGRADED,
    ERROR
}
