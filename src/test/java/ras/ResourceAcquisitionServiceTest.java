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

class ResourceAcquisitionServiceTest {

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
    void testUnlockFailedForResourceThatNeverBeenLocked() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(TimeSpan.Default);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.UNLOCK, "User1", "Item1");
        AcquiredResource resource = response.getResource();

        assertEquals(ResourceAcquisitionCommandResult.UNLOCK_FAILED, response.getCommitResult());
        assertEquals("User1", resource.getUserName());
        assertEquals(ResourceAcquisitionState.UNLOCKED, resource.getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
    }

    @Test
    void testLockCommandSucceeds() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item2");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
    }

    @Test
    void testResourceCanBeLockedTwice() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        TimeSpan u1LockTimestamp = response.getResource().getUtcTimeStamp();
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_FAILED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
        assertEquals(u1LockTimestamp, response.getResource().getUtcTimeStamp());
    }

    @Test
    void testCanLockAgainAfterTimeoutExpired() {
        final TestScheduler testScheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = createService(testScheduler);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_FAILED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_FAILED, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
        assertEquals(TimeSpan.Default, response.getResource().getStateTimeout());
    }

    @Test
    void testResourceCanBeLockedAgainAfterLockUnlock() {
        TestScheduler testScheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = createService(testScheduler);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.UNLOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.UNLOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.UNLOCKED, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());

        TimeSpan stateTimeout = response.getResource().getStateTimeout();
        testScheduler.advanceTimeBy(stateTimeout.getInterval(), stateTimeout.getUnit());
    }

    @Test
    void testStaleUnlockDoesNotRemoveManuallyUnlockedResource() {
        StaleAutoUnlockScheduler scheduler = new StaleAutoUnlockScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                ResourceAcquisitionMetrics.createDefault());

        service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        service.commit(ResourceAcquisitionCommand.UNLOCK, "User1", "Item1");
        scheduler.runCommand(0);

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
    }

    @Test
    void testStaleUnlockDoesNotRemoveRefreshedResource() {
        StaleAutoUnlockScheduler scheduler = new StaleAutoUnlockScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                ResourceAcquisitionMetrics.createDefault());

        service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        scheduler.runCommand(0);

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_FAILED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
    }

    @Test
    void testCustomSchedulerClose() {
        StaleAutoUnlockScheduler scheduler = new StaleAutoUnlockScheduler();
        try (var service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                ResourceAcquisitionMetrics.createDefault())) {
            service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        }

        assertEquals(0, scheduler.commands.size());
    }

    @Test
    void testOpenTelemetryMetricsArePublished() {
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        TestScheduler scheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                TimeSpan.Default,
                meterProvider.get(ResourceAcquisitionMetrics.METER_NAME));

        service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        service.commit(ResourceAcquisitionCommand.UNLOCK, "User1", "Item1");

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
    void testCommitCanBeDoneOnlyByOwner() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.UNLOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.UNLOCK_FAILED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_FAILED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
    }

    @Test
    void testNonComparableResourcesCanBeLocked() {
        SimpleResourceAcquisitionService<Object> service = new SimpleResourceAcquisitionService<>();
        Object resource = new Object();

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", resource);

        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, response.getResource().getState());
    }

    @Test
    void testSchedulerConstructorAndClose() {
        TestScheduler scheduler = new TestScheduler();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(scheduler);

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        service.close();

        assertEquals(ResourceAcquisitionCommandResult.LOCK_SUCCEEDED, response.getCommitResult());
        scheduler.advanceTimeBy(TimeSpan.Default.getInterval(), TimeSpan.Default.getUnit());
        response = service.commit(ResourceAcquisitionCommand.LOCK, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LOCK_FAILED, response.getCommitResult());
    }

    @Test
    void testSchedulerConstructorWithTimeoutAndMeter() {
        TestScheduler scheduler = new TestScheduler();
        TimeSpan timeout = new TimeSpan(5, TimeUnit.SECONDS);
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(
                scheduler,
                timeout,
                io.opentelemetry.api.metrics.MeterProvider.noop().get(ResourceAcquisitionMetrics.METER_NAME));

        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.LOCK, "User1", "Item1");
        service.close();

        assertEquals(timeout, response.getResource().getStateTimeout());
    }

    @Test
    void testAcquiredResourceValueSemantics() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AcquiredResource locked = AcquiredResource.createNew("User1", ResourceAcquisitionState.LOCKED, TimeSpan.Default, clock);
        AcquiredResource same = AcquiredResource.createNew("User1", ResourceAcquisitionState.LOCKED, TimeSpan.Default, clock);
        AcquiredResource differentUser = AcquiredResource.createNew("User2", ResourceAcquisitionState.LOCKED, TimeSpan.Default, clock);
        AcquiredResource differentState = AcquiredResource.createNew("User1", ResourceAcquisitionState.UNLOCKED, TimeSpan.Default, clock);

        // Exercise equals itself: assertion helpers may short-circuit identical references.
        boolean reflexiveEquality = locked.equals(locked);
        assertTrue(reflexiveEquality);
        assertEquals(locked, same);
        assertEquals(locked.hashCode(), same.hashCode());
        assertNotEquals(locked, differentUser);
        assertNotEquals(locked, differentState);
        boolean equalsNull = locked.equals(null);
        boolean equalsUnrelatedType = locked.equals(new Object());
        assertFalse(equalsNull);
        assertFalse(equalsUnrelatedType);
        assertEquals("User1 Locked at 2026-01-01T00:00:00Z", locked.toString());
        assertTrue(locked.compareTo(differentUser) < 0);
        assertTrue(locked.compareTo(differentState) < 0);
    }

    @Test
    void testAcquiredResourceFactoryUsesSystemClock() {
        AcquiredResource resource = AcquiredResource.createNew("User1", ResourceAcquisitionState.LOCKED, TimeSpan.Default);

        assertEquals("User1", resource.getUserName());
        assertEquals(ResourceAcquisitionState.LOCKED, resource.getState());
    }

    @Test
    void testEnumValuesKeepTheirExternalNames() {
        assertEquals(List.of("Lock", "Unlock"),
                java.util.Arrays.stream(ResourceAcquisitionCommand.values()).map(ResourceAcquisitionCommand::getValue).toList());
        assertEquals(List.of("LockSucceeded", "LockFailed", "UnlockSucceeded", "UnlockFailed"),
                java.util.Arrays.stream(ResourceAcquisitionCommandResult.values()).map(ResourceAcquisitionCommandResult::getValue).toList());
        assertEquals(List.of("Locked", "Unlocked"),
                java.util.Arrays.stream(ResourceAcquisitionState.values()).map(ResourceAcquisitionState::getValue).toList());
        assertEquals(List.of("Lock", "Unlock"),
                java.util.Arrays.stream(ResourceAcquisitionCommand.values()).map(ResourceAcquisitionCommand::toString).toList());
        assertEquals(List.of("LockSucceeded", "LockFailed", "UnlockSucceeded", "UnlockFailed"),
                java.util.Arrays.stream(ResourceAcquisitionCommandResult.values()).map(ResourceAcquisitionCommandResult::toString).toList());
        assertEquals(List.of("Locked", "Unlocked"),
                java.util.Arrays.stream(ResourceAcquisitionState.values()).map(ResourceAcquisitionState::toString).toList());
        assertEquals(List.of("LOCK", "UNLOCK"),
                java.util.Arrays.stream(ResourceAcquisitionCommand.values()).map(ResourceAcquisitionCommand::name).toList());
    }

    @Test
    void testTimeSpanValueSemantics() {
        TimeSpan thirtySeconds = new TimeSpan(30, TimeUnit.SECONDS);
        TimeSpan same = new TimeSpan(30, TimeUnit.SECONDS);
        TimeSpan differentInterval = new TimeSpan(31, TimeUnit.SECONDS);
        TimeSpan differentUnit = new TimeSpan(30, TimeUnit.MILLISECONDS);

        assertEquals(thirtySeconds, same);
        assertEquals(thirtySeconds.hashCode(), same.hashCode());
        assertNotEquals(thirtySeconds, differentInterval);
        assertNotEquals(thirtySeconds, differentUnit);
        assertNotEquals(null, thirtySeconds);
    }
}
