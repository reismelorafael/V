package com.vectras.vm.core;

import org.junit.Assert;
import org.junit.Test;

public class TokenBucketRateLimiterTest {
    @Test
    public void shouldDropThenRecoverAfterRefill() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2.0, 2);
        Assert.assertTrue(limiter.tryAcquire());
        Assert.assertTrue(limiter.tryAcquire());
        Assert.assertFalse(limiter.tryAcquire());

        Thread.sleep(600);
        Assert.assertTrue(limiter.tryAcquire());
    }
}
