package ras;

import java.util.concurrent.TimeUnit;

public final class Time {
    private final long delayTime;
    private final TimeUnit unit;

    private Time(long delayTime, TimeUnit unit) {
        this.delayTime = delayTime;
        this.unit = unit;
    }

    public static Time createNew(final long delayTime, TimeUnit unit) {
        return new Time(delayTime, unit);
    }

    public static Time getDefault() {
        return new Time(30L, TimeUnit.SECONDS);
    }

    public long getDelayTime() {
        return delayTime;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Time time = (Time) o;
        return (delayTime == time.delayTime) && (unit == time.unit);
    }

    @Override
    public int hashCode() {
        int result = (int) (delayTime ^ (delayTime >>> 32));
        result = 31 * result + unit.hashCode();
        return result;
    }
}
