package com.vectras.vm.telemetry;

import java.io.File;

public interface TelemetrySink {
    void publish(TelemetryRecord record);

    File exportDeterministicBatch(int maxEvents);
}
