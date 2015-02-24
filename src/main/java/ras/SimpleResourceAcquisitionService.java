package ras;

import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

import java.util.concurrent.ConcurrentSkipListMap;

public class SimpleResourceAcquisitionService<T> implements ResourceAcquisitionService<T> {

    private final Worker worker;
    private final Time unlockTimeout;
    private final ConcurrentSkipListMap<T, AutoUnlockableResource> repository = new ConcurrentSkipListMap<>();

    private static final class AutoUnlockableResource {

        private final Subscription unlockSubscription;
        private final AcquiredResource acquiredResource;

        private AutoUnlockableResource(final Subscription unlockSubscription, final AcquiredResource acquiredResource) {
            this.unlockSubscription = unlockSubscription;
            this.acquiredResource = acquiredResource;
        }

        public static AutoUnlockableResource createNew(final Subscription unlockSubscription, final AcquiredResource acquiredResource) {
            return new AutoUnlockableResource(unlockSubscription, acquiredResource);
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
                    return ResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.LockFailed, existingResource);
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

            final Subscription unlockSubscription = worker.schedule(unlockAction, unlockTimeout.getDelayTime(), unlockTimeout.getUnit());
            final AutoUnlockableResource unlockableResource = AutoUnlockableResource.createNew(unlockSubscription, newItem);
            repository.put(resource, unlockableResource);

            return ResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.LockSucceeded, newItem);
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
                    return ResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.UnlockFailed, acquiredResource);
                }

                existingItem.getUnlockSubscription().unsubscribe();
                repository.remove(resource);
                return ResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.UnlockSucceeded, unlockedItem);
            }
            return ResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.UnlockFailed, unlockedItem);
        }
    }

    private ResourceAcquisitionCommandProcessor createCommandProcessor(ResourceAcquisitionCommand command) {
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

    public SimpleResourceAcquisitionService(final Scheduler scheduler, final Time unlockTimeout) {
        this.worker = scheduler.createWorker();
        this.unlockTimeout = unlockTimeout;
    }

    public SimpleResourceAcquisitionService(final Scheduler scheduler) {
        this(scheduler, Time.getDefault());
    }

    public SimpleResourceAcquisitionService(final Time unlockTimeout) {
        this(Schedulers.computation(), unlockTimeout);
    }

    public SimpleResourceAcquisitionService() {
        this(Schedulers.computation(), Time.getDefault());
    }

    @Override
    public ResourceAcquisitionResponse commit(ResourceAcquisitionCommand command, String userName, T resource) {
        final ResourceAcquisitionCommandProcessor commandProcessor = createCommandProcessor(command);
        return commandProcessor.commit(userName, resource);
    }
}
