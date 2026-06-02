package ras;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.reactivex.rxjava3.schedulers.TestScheduler;

public class ResourceAcquisitionServiceTest {

    private static final class StaleAutoUnlockScheduler implements SimpleResourceAcquisitionService.AutoUnlockScheduler {

        private final List<Runnable> commands = new ArrayList<>();

        @Override
        public SimpleResourceAcquisitionService.Cancellable schedule(final Runnable command, final TimeSpan delay) {
            commands.add(command);
            return () -> {
            };
        }

        public void runCommand(final int index) {
            commands.get(index).run();
        }

        @Override
        public void close() {
            commands.clear();
        }
    }

    private SimpleResourceAcquisitionService<String> createService(final TestScheduler scheduler) {
        return new SimpleResourceAcquisitionService<>(
                new SimpleResourceAcquisitionService.RxAutoUnlockScheduler(scheduler),
                TimeSpan.Default,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                ResourceAcquisitionMetrics.createDefault());
    }

    @Test
    public void testUnlockFailedForResourceThatNeverBeenLocked() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(TimeSpan.Default);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");
        AcquiredResource resource = response.getResource();

        assertEquals(ResourceAcquisitionCommandResult.UnlockFailed, response.getCommitResult());
        assertEquals("User1", resource.getUserName());
        assertEquals(ResourceAcquisitionState.Unlocked, resource.getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
    }

    @Test
    public void testLockCommandSucceeds() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item2");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
    }

    @Test
    public void testResourceCanBeLockedTwice() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        TimeSpan u1LockTimestamp = response.getResource().getUtcTimeStamp();
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
        assertEquals(u1LockTimestamp, response.getResource().getUtcTimeStamp());
    }

    @Test
    public void testCanLockAgainAfterTimeoutExpired() {
        final TestScheduler testScheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = createService(testScheduler);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
    }

    @Test
    public void testResourceCanBeLockedAgainAfterLockUnlock() {
        TestScheduler testScheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = createService(testScheduler);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.UnlockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Unlocked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        TimeSpan stateTimeout = response.getResource().getStateTimeout();
        testScheduler.advanceTimeBy(stateTimeout.getInterval(), stateTimeout.getUnit());
    }

    @Test
    public void testStaleUnlockDoesNotRemoveManuallyUnlockedResource() {
        StaleAutoUnlockScheduler scheduler = new StaleAutoUnlockScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                ResourceAcquisitionMetrics.createDefault());

        service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");
        scheduler.runCommand(0);

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
    }

    @Test
    public void testStaleUnlockDoesNotRemoveRefreshedResource() {
        StaleAutoUnlockScheduler scheduler = new StaleAutoUnlockScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                ResourceAcquisitionMetrics.createDefault());

        service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        scheduler.runCommand(0);

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
    }

    @Test
    public void testCustomSchedulerClose() {
        StaleAutoUnlockScheduler scheduler = new StaleAutoUnlockScheduler();
        try (var service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                ResourceAcquisitionMetrics.createDefault())) {
            service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        }

        assertEquals(0, scheduler.commands.size());
    }

    @Test
    public void testOpenTelemetryMetricsArePublished() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        TestScheduler scheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                meterProvider.get(ResourceAcquisitionMetrics.METER_NAME));

        service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");

        Set<String> metricNames = metricReader.collectAllMetrics().stream()
                .map(MetricData::getName)
                .collect(Collectors.toSet());
        meterProvider.close();

        assertEquals(
                Set.of(
                        ResourceAcquisitionMetrics.COMMANDS_METRIC_NAME,
                        ResourceAcquisitionMetrics.RESULTS_METRIC_NAME,
                        ResourceAcquisitionMetrics.ACTIVE_LOCKS_METRIC_NAME),
                metricNames);
    }

    @Test
    public void testCommitCanBeDoneOnlyByOwner() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Unlock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.UnlockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
    }

    @Test
    public void testNonComparableResourcesCanBeLocked() {
        SimpleResourceAcquisitionService<Object> service = new SimpleResourceAcquisitionService<>();
        Object resource = new Object();

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", resource);

        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
    }

    @Test
    public void testSchedulerConstructorAndClose() {
        TestScheduler scheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(scheduler);

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        service.close();

        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        scheduler.advanceTimeBy(TimeSpan.Default.getInterval(), TimeSpan.Default.getUnit());
        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
    }

    @Test
    public void testSchedulerConstructorWithTimeoutAndMeter() {
        TestScheduler scheduler = new TestScheduler();
        TimeSpan timeout = new TimeSpan(5, TimeUnit.SECONDS);
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                timeout,
                io.opentelemetry.api.metrics.MeterProvider.noop().get(ResourceAcquisitionMetrics.METER_NAME));

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        service.close();

        assertEquals(timeout, response.getResource().getStateTimeout());
    }

    @Test
    public void testAcquiredResourceValueSemantics() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AcquiredResource locked = AcquiredResource.createNew("User1", ResourceAcquisitionState.Locked, TimeSpan.Default, clock);
        AcquiredResource same = AcquiredResource.createNew("User1", ResourceAcquisitionState.Locked, TimeSpan.Default, clock);
        AcquiredResource differentUser = AcquiredResource.createNew("User2", ResourceAcquisitionState.Locked, TimeSpan.Default, clock);
        AcquiredResource differentState = AcquiredResource.createNew("User1", ResourceAcquisitionState.Unlocked, TimeSpan.Default, clock);

        assertEquals(locked, locked);
        assertEquals(locked, same);
        assertEquals(locked.hashCode(), same.hashCode());
        assertNotEquals(locked, differentUser);
        assertNotEquals(locked, differentState);
        assertNotEquals(locked, null);
        assertNotEquals(locked, "not a resource");
        assertEquals("User1 Locked at 2026-01-01T00:00:00Z", locked.toString());
        assertTrue(locked.compareTo(differentUser) < 0);
        assertTrue(locked.compareTo(differentState) < 0);
    }

    @Test
    public void testAcquiredResourceFactoryUsesSystemClock() {
        AcquiredResource resource = AcquiredResource.createNew("User1", ResourceAcquisitionState.Locked, TimeSpan.Default);

        assertEquals("User1", resource.getUserName());
        assertEquals(ResourceAcquisitionState.Locked, resource.getState());
    }

    @Test
    public void testTimeSpanValueSemantics() {
        TimeSpan thirtySeconds = new TimeSpan(30, TimeUnit.SECONDS);
        TimeSpan same = new TimeSpan(30, TimeUnit.SECONDS);
        TimeSpan differentInterval = new TimeSpan(31, TimeUnit.SECONDS);
        TimeSpan differentUnit = new TimeSpan(30, TimeUnit.MILLISECONDS);

        assertEquals(thirtySeconds, thirtySeconds);
        assertEquals(thirtySeconds, same);
        assertEquals(thirtySeconds.hashCode(), same.hashCode());
        assertNotEquals(thirtySeconds, differentInterval);
        assertNotEquals(thirtySeconds, differentUnit);
        assertNotEquals(thirtySeconds, null);
        assertNotEquals(thirtySeconds, "not a time span");
        assertFalse(thirtySeconds.equals(differentInterval));
    }
}
