package ras;

import org.joda.time.DateTime;

public interface AcquiredResource<T> {
    String getUserName();

    T getValue();

    ResourceAcquisitionState getState();

    DateTime getUtcTimeStamp();
}
