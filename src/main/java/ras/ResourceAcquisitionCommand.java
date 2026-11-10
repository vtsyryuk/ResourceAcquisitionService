package ras;

public enum ResourceAcquisitionCommand {
    LOCK("Lock"),
    UNLOCK("Unlock");

    private final String value;

    ResourceAcquisitionCommand(String value) {
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
