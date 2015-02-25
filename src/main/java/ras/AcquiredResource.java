package ras;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

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
        this.userName = userName;
        this.state = state;
        this.timestamp = timestamp;
        this.timeout = timeout;
    }

    public static AcquiredResource createNew(final String userName,
                                             final ResourceAcquisitionState state,
                                             final TimeSpan stateTimeout) {
        final TimeSpan timestamp = new TimeSpan(DateTime.now(DateTimeZone.UTC).getMillis(), TimeUnit.MILLISECONDS);
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AcquiredResource that = (AcquiredResource) o;
        return (state == that.state) && (userName != null ? userName.equals(that.userName) : that.userName == null);
    }

    @Override
    public int hashCode() {
        int result = userName != null ? userName.hashCode() : 0;
        result = 31 * result + (state != null ? state.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        if (state != null && timestamp != null) {
            long instant = timestamp.getUnit().toMillis(timestamp.getInterval());
            String printedTimestamp = DateTimeFormat.shortDateTime().withZoneUTC().print(instant);
            return String.format("%s %s at %s", userName, state.name(), printedTimestamp);
        }
        return super.toString();
    }

    @Override
    public int compareTo(AcquiredResource o) {
        int cmp = this.userName.compareToIgnoreCase(o.getUserName());
        return cmp == 0 ? this.state.compareTo(o.getState()) : cmp;
    }
}
