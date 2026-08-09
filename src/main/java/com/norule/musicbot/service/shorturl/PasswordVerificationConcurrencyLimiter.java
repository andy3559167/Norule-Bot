package com.norule.musicbot.service.shorturl;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class PasswordVerificationConcurrencyLimiter {
    private final Semaphore permits;
    private final int maximumConcurrency;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    public PasswordVerificationConcurrencyLimiter(int maximumConcurrency) {
        this.maximumConcurrency = Math.max(1, maximumConcurrency);
        this.permits = new Semaphore(this.maximumConcurrency, true);
    }

    public Permit tryAcquire() {
        if (!permits.tryAcquire()) {
            return null;
        }
        int current = active.incrementAndGet();
        peak.accumulateAndGet(current, Math::max);
        return new Permit();
    }

    public int maximumConcurrency() {
        return maximumConcurrency;
    }

    public int activeVerifications() {
        return active.get();
    }

    public int peakVerifications() {
        return peak.get();
    }

    public final class Permit implements AutoCloseable {
        private boolean closed;

        private Permit() {
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            active.decrementAndGet();
            permits.release();
        }
    }
}
