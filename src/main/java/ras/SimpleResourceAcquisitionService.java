package ras;

import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

import java.util.concurrent.ConcurrentSkipListMap;

public class SimpleResourceAcquisitionService<T> implements ResourceAcquisitionService<T> {

    private final Worker worker;
    private final TimeSpan unlockTimeout;
    private final ConcurrentSkipListMap<T, AutoUnlockableResource> repository = new ConcurrentSkipListMap<>();

    private static final class AutoUnlockableResource {

        private final Subscription unlockSubscription;
        private final AcquiredResource acquiredResource;

        public AutoUnlockableResource(final Subscription unlockSubscription, final AcquiredResource acquiredResource) {
            this.unlockSubscription = unlockSubscription;
            this.acquiredResource = acquiredResource;
        }

        public Subscription getUnlockSubscription() {
            return unlockSubscription;
        }

        public AcquiredResource getAcquiredResource() {
            return acquiredResource;
        }
    }

    private interface ResourceAcquisitionCommandProcessor<T> {
        ResourceAcquisitionResponse commit(String userName, T resource);
    }

    private final class ResourceLockCommandProcessor implements ResourceAcquisitionCommandProcessor<T> {

        @SuppressWarnings("synthetic-access")
        @Override
        public ResourceAcquisitionResponse commit(String userName, final T resource) {

            final AutoUnlockableResource existingItem = repository.get(resource);
            if (existingItem != null) {
                final AcquiredResource existingResource = existingItem.getAcquiredResource();
                if (!existingResource.getUserName().equalsIgnoreCase(userName)) {
                    return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.LockFailed, existingResource);
                } else {
                    existingItem.getUnlockSubscription().unsubscribe();
                }
            }

            final AcquiredResource newItem = AcquiredResource.createNew(userName, ResourceAcquisitionState.Locked, unlockTimeout);
            Action0 unlockAction = new Action0() {
                @Override
                public void call() {
                    AcquiredResource lockedItem = repository.get(resource).getAcquiredResource();
                    // NOTE: reference equality required to make sure the same item will be removed
                    // in case of delayed execution of unsubscribe
                    if (newItem == lockedItem) {
                        repository.remove(resource);
                    }
                }
            };

            final Subscription unlockSubscription = worker.schedule(unlockAction, unlockTimeout.getInterval(), unlockTimeout.getUnit());
            final AutoUnlockableResource unlockableResource = new AutoUnlockableResource(unlockSubscription, newItem);
            repository.put(resource, unlockableResource);

            return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.LockSucceeded, newItem);
        }
    }

    private final class ResourceUnlockCommandProcessor implements ResourceAcquisitionCommandProcessor<T> {

        @SuppressWarnings("synthetic-access")
        @Override
        public ResourceAcquisitionResponse commit(String userName, T resource) {
            final AutoUnlockableResource existingItem = repository.get(resource);
            final AcquiredResource unlockedItem = AcquiredResource.createNew(userName, ResourceAcquisitionState.Unlocked, unlockTimeout);
            if (existingItem != null) {
                final AcquiredResource acquiredResource = existingItem.getAcquiredResource();
                if (!acquiredResource.getUserName().equalsIgnoreCase(userName)) {
                    return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.UnlockFailed, acquiredResource);
                }

                existingItem.getUnlockSubscription().unsubscribe();
                repository.remove(resource);
                return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.UnlockSucceeded, unlockedItem);
            }
            return new ResourceAcquisitionResponse(ResourceAcquisitionCommandResult.UnlockFailed, unlockedItem);
        }
    }

    private ResourceAcquisitionCommandProcessor<T> createCommandProcessor(ResourceAcquisitionCommand command) {
        switch (command) {
            case Lock:
                return new ResourceLockCommandProcessor();
            case Unlock:
                return new ResourceUnlockCommandProcessor();
            default:
                break;
        }
        throw new IndexOutOfBoundsException(String.format("%s command is not supported", command));
    }

    public SimpleResourceAcquisitionService(final Scheduler scheduler, final TimeSpan unlockTimeout) {
        this.worker = scheduler.createWorker();
        this.unlockTimeout = unlockTimeout;
    }

    public SimpleResourceAcquisitionService(final Scheduler scheduler) {
        this(scheduler, TimeSpan.Default);
    }

    public SimpleResourceAcquisitionService(final TimeSpan unlockTimeout) {
        this(Schedulers.computation(), unlockTimeout);
    }

    public SimpleResourceAcquisitionService() {
        this(Schedulers.computation(), TimeSpan.Default);
    }

    @Override
    public ResourceAcquisitionResponse commit(ResourceAcquisitionCommand command, String userName, T resource) {
        final ResourceAcquisitionCommandProcessor<T> commandProcessor = createCommandProcessor(command);
        return commandProcessor.commit(userName, resource);
    }
}
