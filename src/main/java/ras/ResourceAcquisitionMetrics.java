package ras;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Objects;

public final class ResourceAcquisitionMetrics {

    static final String METER_NAME = "ras.resource-acquisition-service";
    static final String COMMANDS_METRIC_NAME = "ras.resource_acquisition.commands";
    static final String RESULTS_METRIC_NAME = "ras.resource_acquisition.results";
    static final String ACTIVE_LOCKS_METRIC_NAME = "ras.resource_acquisition.active_locks";

    private static final AttributeKey<String> COMMAND_ATTRIBUTE = AttributeKey.stringKey("command");
    private static final AttributeKey<String> RESULT_ATTRIBUTE = AttributeKey.stringKey("result");

    private final LongCounter commandCounter;
    private final LongCounter resultCounter;
    private final LongUpDownCounter activeLocksCounter;

    public ResourceAcquisitionMetrics(final Meter meter) {
        Objects.requireNonNull(meter, "meter");
        this.commandCounter = meter.counterBuilder(COMMANDS_METRIC_NAME)
                .setDescription("Number of resource acquisition commands received.")
                .setUnit("{command}")
                .build();
        this.resultCounter = meter.counterBuilder(RESULTS_METRIC_NAME)
                .setDescription("Number of resource acquisition command results produced.")
                .setUnit("{result}")
                .build();
        this.activeLocksCounter = meter.upDownCounterBuilder(ACTIVE_LOCKS_METRIC_NAME)
                .setDescription("Current number of resources held by the acquisition service.")
                .setUnit("{lock}")
                .build();
    }

    public static ResourceAcquisitionMetrics createDefault() {
        return new ResourceAcquisitionMetrics(GlobalOpenTelemetry.getMeter(METER_NAME));
    }

    public void recordCommand(final ResourceAcquisitionCommand command) {
        commandCounter.add(1, Attributes.of(COMMAND_ATTRIBUTE, command.name()));
    }

    public void recordResult(final ResourceAcquisitionCommandResult result) {
        resultCounter.add(1, Attributes.of(RESULT_ATTRIBUTE, result.name()));
    }

    public void recordLockAcquired() {
        activeLocksCounter.add(1);
    }

    public void recordLockReleased() {
        activeLocksCounter.add(-1);
    }
}
