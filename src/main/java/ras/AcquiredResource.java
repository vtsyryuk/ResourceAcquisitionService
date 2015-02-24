package ras;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.concurrent.TimeUnit;

public final class AcquiredResource implements Comparable<AcquiredResource> {

    private final String userName;
    private final ResourceAcquisitionState state;
    private final Time timestamp;
    private final Time timeout;

    protected AcquiredResource(String userName, ResourceAcquisitionState state, Time timeout, Time timestamp) {
        this.userName = userName;
        this.state = state;
        this.timestamp = timestamp;
        this.timeout = timeout;        
    }

    public static AcquiredResource createNew(String userName, ResourceAcquisitionState state, Time stateTimeout) {
        Time timestamp = Time.createNew(DateTime.now(DateTimeZone.UTC).getMillis(), TimeUnit.MILLISECONDS);
        return new AcquiredResource(userName, state, stateTimeout, timestamp);
    }

    public String getUserName() {
        return userName;
    }

    public ResourceAcquisitionState getState() {
        return state;
    }

    public Time getUtcTimeStamp() {
        return timestamp;
    }

    public Time getStateTimeout() {
        return timeout;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AcquiredResource that = (AcquiredResource) o;
        return (state == that.state) && userName.equals(that.userName);

    }

    @Override
    public int hashCode() {
        int result = userName.hashCode();
        result = 31 * result + state.hashCode();
        return result;
    }

    @Override
    public int compareTo(AcquiredResource o) {
        int cmp = this.userName.compareToIgnoreCase(o.getUserName());
        return cmp == 0 ? this.state.compareTo(o.getState()) : cmp;
    }
}
