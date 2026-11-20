package com.cloudgame.dispatcher.fake;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/** Reloj que solo avanza con {@link #advanceSeconds}. */
public class MutableClock extends Clock {

    private final AtomicLong millis = new AtomicLong(1_800_000_000_000L);

    public void advanceSeconds(long s) {
        millis.addAndGet(s * 1000);
    }

    @Override
    public long millis() {
        return millis.get();
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
