package ras;

public enum ResourceAcquisitionState {
    LOCKED("Locked"),
    UNLOCKED("Unlocked");

    private final String value;

    ResourceAcquisitionState(String value) {
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
