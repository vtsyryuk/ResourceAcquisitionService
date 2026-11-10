package ras;

import java.util.concurrent.TimeUnit;

public record TimeSpan(long interval, TimeUnit unit) {
    public static final TimeSpan Default = new TimeSpan(30L, TimeUnit.SECONDS);

    public long getInterval() {
        return interval;
    }

    public TimeUnit getUnit() {
        return unit;
    }
}
