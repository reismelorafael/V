package com.vectras.qemu.utils;

import org.junit.Assert;
import org.junit.Test;

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
}
