package com.vectras.vm.core;

/**
 * Thread-safe token bucket limiter for line-based flow control.
 */
public class TokenBucketRateLimiter {
    private final double refillTokensPerSecond;
    private final double maxCapacity;
    private double availableTokens;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(double refillTokensPerSecond, int burstCapacity) {
        this.refillTokensPerSecond = Math.max(0.1d, refillTokensPerSecond);
        this.maxCapacity = Math.max(1d, burstCapacity);
        this.availableTokens = this.maxCapacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens >= 1d) {
            availableTokens -= 1d;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = Math.max(0L, now - lastRefillNanos);
        double elapsedSeconds = elapsedNanos / 1_000_000_000d;
        availableTokens = Math.min(maxCapacity, availableTokens + (elapsedSeconds * refillTokensPerSecond));
        lastRefillNanos = now;
    }
}
