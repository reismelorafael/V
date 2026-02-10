package com.vectras.vm.audit;

import org.json.JSONException;
import org.json.JSONObject;

public class AuditEvent {
    public final long tsMono;
    public final long tsWall;
    public final String vmId;
    public final String stateFrom;
    public final String stateTo;
    public final String causeCode;
    public final int droppedLogs;
    public final long bytes;
    public final long stallMs;
    public final String actionTaken;

    public AuditEvent(long tsMono,
                      long tsWall,
                      String vmId,
                      String stateFrom,
                      String stateTo,
                      String causeCode,
                      int droppedLogs,
                      long bytes,
                      long stallMs,
                      String actionTaken) {
        this.tsMono = tsMono;
        this.tsWall = tsWall;
        this.vmId = vmId;
        this.stateFrom = stateFrom;
        this.stateTo = stateTo;
        this.causeCode = causeCode;
        this.droppedLogs = droppedLogs;
        this.bytes = bytes;
        this.stallMs = stallMs;
        this.actionTaken = actionTaken;
    }

    public String toJsonLine() {
        JSONObject object = new JSONObject();
        try {
            object.put("ts_mono", tsMono);
            object.put("ts_wall", tsWall);
            object.put("vm_id", vmId);
            object.put("state_from", stateFrom);
            object.put("state_to", stateTo);
            object.put("cause_code", causeCode);
            object.put("dropped_logs", droppedLogs);
            object.put("bytes", bytes);
            object.put("stall_ms", stallMs);
            object.put("action_taken", actionTaken);
        } catch (JSONException ignored) {
            // should never happen with primitive fields
        }
        return object.toString();
    }
}
