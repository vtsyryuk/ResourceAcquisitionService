package ras;

import java.util.concurrent.TimeUnit;

public final class TimeSpan {
    private final long interval;
    private final TimeUnit unit;

    public static final TimeSpan Default = new TimeSpan(30L, TimeUnit.SECONDS);

    public TimeSpan(final long interval, final TimeUnit unit) {
        this.interval = interval;
        this.unit = unit;
    }

    public long getInterval() {
        return interval;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        TimeSpan time = (TimeSpan) o;
        return (interval == time.interval) && (unit == time.unit);
    }

    @Override
    public int hashCode() {
        int result = (int) (interval ^ (interval >>> 32));
        result = 31 * result + unit.hashCode();
        return result;
    }
}