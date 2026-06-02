package ras;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AcquiredResource implements Comparable<AcquiredResource> {

    private final String userName;
    private final ResourceAcquisitionState state;
    private final TimeSpan timestamp;
    private final TimeSpan timeout;

    private AcquiredResource(final String userName,
                             final ResourceAcquisitionState state,
                             final TimeSpan timeout,
                             final TimeSpan timestamp) {
        this.userName = Objects.requireNonNull(userName, "userName");
        this.state = Objects.requireNonNull(state, "state");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    public static AcquiredResource createNew(final String userName,
                                             final ResourceAcquisitionState state,
                                             final TimeSpan stateTimeout) {
        return createNew(userName, state, stateTimeout, Clock.systemUTC());
    }

    static AcquiredResource createNew(final String userName,
                                      final ResourceAcquisitionState state,
                                      final TimeSpan stateTimeout,
                                      final Clock clock) {
        final TimeSpan timestamp = new TimeSpan(clock.millis(), TimeUnit.MILLISECONDS);
        return new AcquiredResource(userName, state, stateTimeout, timestamp);
    }

    public final String getUserName() {
        return userName;
    }

    public final ResourceAcquisitionState getState() {
        return state;
    }

    public final TimeSpan getUtcTimeStamp() {
        return timestamp;
    }

    public final TimeSpan getStateTimeout() {
        return timeout;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof AcquiredResource that
                && state == that.state
                && userName.equals(that.userName));
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, state);
    }

    @Override
    public String toString() {
        long instant = timestamp.getUnit().toMillis(timestamp.getInterval());
        String printedTimestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(instant));
        return String.format("%s %s at %s", userName, state.name(), printedTimestamp);
    }

    @Override
    public int compareTo(AcquiredResource o) {
        int cmp = this.userName.compareToIgnoreCase(o.getUserName());
        return cmp == 0 ? this.state.compareTo(o.getState()) : cmp;
    }
}
