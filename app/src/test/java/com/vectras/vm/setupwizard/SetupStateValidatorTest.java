package com.vectras.vm.setupwizard;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SetupStateValidatorTest {

    @Test
    public void isValidStateJson_acceptsValidPayload() {
        String json = "{"
                + "\"version\":1,"
                + "\"timestamp\":\"2026-03-11T10:00:00Z\","
                + "\"phase\":\"PREPARE\","
                + "\"stage_dir\":\"/root/.vectras-staging/run-1\","
                + "\"message\":\"ok\""
                + "}";

        assertTrue(SetupStateValidator.isValidStateJson(json));
    }

    @Test
    public void isValidStateJson_rejectsWrongVersionType() {
        String json = "{"
                + "\"version\":\"1\","
                + "\"timestamp\":\"2026-03-11T10:00:00Z\","
                + "\"phase\":\"PREPARE\","
                + "\"stage_dir\":\"/root/.vectras-staging/run-1\","
                + "\"message\":\"ok\""
                + "}";

        assertFalse(SetupStateValidator.isValidStateJson(json));
    }

    @Test
    public void isValidStateJson_rejectsInvalidStageDirPrefix() {
        String json = "{"
                + "\"version\":1,"
                + "\"timestamp\":\"2026-03-11T10:00:00Z\","
                + "\"phase\":\"PREPARE\","
                + "\"stage_dir\":\"/tmp/other\","
                + "\"message\":\"ok\""
                + "}";

        assertFalse(SetupStateValidator.isValidStateJson(json));
    }

    @Test
    public void isValidStateMap_acceptsValidMap() {
        Map<String, Object> state = new HashMap<>();
        state.put("version", 1);
        state.put("timestamp", "2026-03-11T10:00:00Z");
        state.put("phase", SetupStateValidator.PHASE_STAGE_OK);
        state.put("stage_dir", "/root/.vectras-staging/run-2");
        state.put("message", "stable");

        assertTrue(SetupStateValidator.isValidStateMap(state));
    }
}
