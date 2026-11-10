package ras;

public enum ResourceAcquisitionCommandResult {
    LOCK_SUCCEEDED("LockSucceeded"),
    LOCK_FAILED("LockFailed"),
    UNLOCK_SUCCEEDED("UnlockSucceeded"),
    UNLOCK_FAILED("UnlockFailed");

    private final String value;

    ResourceAcquisitionCommandResult(String value) {
        this.value = value;
    }

    /** Stable value used in telemetry, demo responses and display text. */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
