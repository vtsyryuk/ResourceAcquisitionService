package ras;

import io.opentelemetry.api.metrics.Meter;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SimpleResourceAcquisitionService<T> implements ResourceAcquisitionService<T>, AutoCloseable {

    private final AutoUnlockScheduler scheduler;
    private final TimeSpan unlockTimeout;
    private final Clock clock;
    private final ResourceAcquisitionMetrics metrics;
    private final Map<T, AutoUnlockableResource> repository = new HashMap<>();

    interface AutoUnlockScheduler extends AutoCloseable {
        Cancellable schedule(Runnable command, TimeSpan delay);

        @Override
        void close();
    }

    interface Cancellable {
        void cancel();
    }

    static final class RxAutoUnlockScheduler implements AutoUnlockScheduler, AutoCloseable {

        private final Scheduler.Worker worker;

        RxAutoUnlockScheduler(final Scheduler scheduler) {
            this.worker = Objects.requireNonNull(scheduler, "scheduler").createWorker();
        }

        @Override
        public Cancellable schedule(final Runnable command, final TimeSpan delay) {
            final Disposable disposable = worker.schedule(command, delay.getInterval(), delay.getUnit());
            return disposable::dispose;
        }

        @Override
        public void close() {
            worker.dispose();
        }
    }

    private static final class AutoUnlockableResource {

        private final AcquiredResource acquiredResource;
        private final Cancellable unlockSubscription;

        public AutoUnlockableResource(final Cancellable unlockSubscription, final AcquiredResource acquiredResource) {
            this.unlockSubscription = Objects.requireNonNull(unlockSubscription, "unlockSubscription");
            this.acquiredResource = acquiredResource;
        }

        public void cancelUnlock() {
            unlockSubscription.cancel();
        }

        public AcquiredResource getAcquiredResource() {
            return acquiredResource;
        }
    }

    private interface ResourceAcquisitionCommandProcessor<T> {
        ResourceAcquisitionResponse commit(String userName, T resource);
    }

    private final class ResourceLockCommandProcessor implements ResourceAcquisitionCommandProcessor<T> {

        @Override
        public ResourceAcquisitionResponse commit(String userName, final T resource) {

            final AutoUnlockableResource existingItem = repository.get(resource);
            if (existingItem != null) {
                final AcquiredResource existingResource = existingItem.getAcquiredResource();
                if (!existingResource.getUserName().equalsIgnoreCase(userName)) {
                    return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.LockFailed, existingResource);
                }
                existingItem.cancelUnlock();
            }

            final AcquiredResource newItem = AcquiredResource.createNew(userName, ResourceAcquisitionState.Locked, unlockTimeout, clock);
            final Cancellable unlockSubscription = scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (SimpleResourceAcquisitionService.this) {
                        AutoUnlockableResource lockedItem = repository.get(resource);
                        if (lockedItem != null && newItem == lockedItem.getAcquiredResource()) {
                            repository.remove(resource);
                            metrics.recordLockReleased();
                        }
                    }
                }
            }, unlockTimeout);
            final AutoUnlockableResource unlockableResource = new AutoUnlockableResource(unlockSubscription, newItem);
            repository.put(resource, unlockableResource);

            if (existingItem == null) {
                metrics.recordLockAcquired();
            }
            return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.LockSucceeded, newItem);
        }
    }

    private final class ResourceUnlockCommandProcessor implements ResourceAcquisitionCommandProcessor<T> {

        @Override
        public ResourceAcquisitionResponse commit(String userName, T resource) {
            final AutoUnlockableResource existingItem = repository.get(resource);
            final AcquiredResource unlockedItem = AcquiredResource.createNew(userName, ResourceAcquisitionState.Unlocked, unlockTimeout, clock);
            if (existingItem != null) {
                final AcquiredResource acquiredResource = existingItem.getAcquiredResource();
                if (!acquiredResource.getUserName().equalsIgnoreCase(userName)) {
                    return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.UnlockFailed, acquiredResource);
                }

                existingItem.cancelUnlock();
                repository.remove(resource);
                metrics.recordLockReleased();
                return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.UnlockSucceeded, unlockedItem);
            }
            return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.UnlockFailed, unlockedItem);
        }
    }

    private ResourceAcquisitionCommandProcessor<T> createCommandProcessor(ResourceAcquisitionCommand command) {
        return switch (command) {
            case Lock -> new ResourceLockCommandProcessor();
            case Unlock -> new ResourceUnlockCommandProcessor();
        };
    }

    public SimpleResourceAcquisitionService(final Scheduler scheduler, final TimeSpan unlockTimeout, final Meter meter) {
        this(new RxAutoUnlockScheduler(scheduler), unlockTimeout, Clock.systemUTC(), new ResourceAcquisitionMetrics(meter));
    }

    public SimpleResourceAcquisitionService(final Scheduler scheduler, final TimeSpan unlockTimeout) {
        this(new RxAutoUnlockScheduler(scheduler), unlockTimeout, Clock.systemUTC(), ResourceAcquisitionMetrics.createDefault());
    }

    SimpleResourceAcquisitionService(final AutoUnlockScheduler scheduler,
                                     final TimeSpan unlockTimeout,
                                     final Clock clock,
                                     final ResourceAcquisitionMetrics metrics) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.unlockTimeout = Objects.requireNonNull(unlockTimeout, "unlockTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public SimpleResourceAcquisitionService(final Scheduler scheduler) {
        this(scheduler, TimeSpan.Default);
    }

    public SimpleResourceAcquisitionService(final TimeSpan unlockTimeout) {
        this(Schedulers.computation(), unlockTimeout);
    }

    public SimpleResourceAcquisitionService() {
        this(TimeSpan.Default);
    }

    @Override
    public synchronized ResourceAcquisitionResponse commit(ResourceAcquisitionCommand command, String userName, T resource) {
        metrics.recordCommand(command);
        final ResourceAcquisitionCommandProcessor<T> commandProcessor = createCommandProcessor(command);
        final ResourceAcquisitionResponse response = commandProcessor.commit(userName, resource);
        metrics.recordResult(response.getCommitResult());
        return response;
    }

    @Override
    public void close() {
        scheduler.close();
    }
}
