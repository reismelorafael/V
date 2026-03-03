package com.vectras.qemu.utils;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public class QmpClientContractTest {

    @Test
    public void greetingAndCapabilitiesContract_isSatisfiedForValidExchange() {
        String response = "{\"QMP\":{\"version\":{\"qemu\":{\"major\":8,\"minor\":2,\"micro\":0},\"package\":\"\"},\"capabilities\":[]}}\n"
                + "{\"return\":{}}\n";

        Assert.assertTrue(QmpClient.isGreetingAndCapabilitiesContractSatisfied(response));
    }

    @Test
    public void greetingAndCapabilitiesContract_failsWithoutGreeting() {
        String response = "{\"return\":{}}\n";

        Assert.assertFalse(QmpClient.isGreetingAndCapabilitiesContractSatisfied(response));
    }

    @Test
    public void greetingAndCapabilitiesContract_failsWithoutCapabilitiesAck() {
        String response = "{\"QMP\":{\"version\":{\"qemu\":{\"major\":8,\"minor\":2,\"micro\":0},\"package\":\"\"},\"capabilities\":[]}}\n";

        Assert.assertFalse(QmpClient.isGreetingAndCapabilitiesContractSatisfied(response));
    }

    @Test
    public void saveSnapshot_buildsValidJsonWithEscapedName() throws Exception {
        String snapshotName = "snap\"name";
        String payload = QmpClient.save_snapshot(snapshotName);

        JSONObject parsed = new JSONObject(payload);
        Assert.assertEquals("snapshot-create", parsed.getString("execute"));
        Assert.assertEquals(snapshotName, parsed.getJSONObject("arguments").getString("name"));
    }
    @Test
    public void sanitizeRequestForLogs_masksSensitiveFieldsRecursivelyAndCaseInsensitive() throws Exception {
        String request = "{"
                + "\"execute\":\"set-password\","
                + "\"arguments\":{"
                + "\"password\":\"root-pass\","
                + "\"nested\":{"
                + "\"SeCrEt\":\"secret-value\","
                + "\"auth\":\"auth-value\"},"
                + "\"list\":[{"
                + "\"token\":\"token-value\"},{"
                + "\"Authorization\":\"Bearer abc\"},{"
                + "\"other\":\"ok\",\"ARG\":\"cli-arg\"}],"
                + "\"safeField\":\"safe\"},"
                + "\"PASSWD\":\"sys-passwd\"}";

        Method sanitizeMethod = QmpClient.class.getDeclaredMethod("sanitizeRequestForLogs", String.class);
        sanitizeMethod.setAccessible(true);
        String sanitized = (String) sanitizeMethod.invoke(null, request);

        Assert.assertNotNull(sanitized);
        Assert.assertFalse(sanitized.contains("root-pass"));
        Assert.assertFalse(sanitized.contains("secret-value"));
        Assert.assertFalse(sanitized.contains("auth-value"));
        Assert.assertFalse(sanitized.contains("token-value"));
        Assert.assertFalse(sanitized.contains("Bearer abc"));
        Assert.assertFalse(sanitized.contains("cli-arg"));
        Assert.assertFalse(sanitized.contains("sys-passwd"));

        JSONObject parsed = new JSONObject(sanitized);
        JSONObject arguments = parsed.getJSONObject("arguments");
        Assert.assertEquals("***", arguments.getString("password"));
        Assert.assertEquals("***", arguments.getJSONObject("nested").getString("SeCrEt"));
        Assert.assertEquals("***", arguments.getJSONObject("nested").getString("auth"));
        Assert.assertEquals("***", arguments.getJSONArray("list").getJSONObject(0).getString("token"));
        Assert.assertEquals("***", arguments.getJSONArray("list").getJSONObject(1).getString("Authorization"));
        Assert.assertEquals("***", arguments.getJSONArray("list").getJSONObject(2).getString("ARG"));
        Assert.assertEquals("***", parsed.getString("PASSWD"));
        Assert.assertEquals("safe", arguments.getString("safeField"));
    }

}
