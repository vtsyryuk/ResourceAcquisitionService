package ras;

import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

import java.util.concurrent.ConcurrentSkipListMap;

public class TextResourceAcquisitionService implements ResourceAcquisitionService<String> {

    private final Worker worker;
    private final Time unlockTimeout;
    private final ConcurrentSkipListMap<String, AutoUnlockableResource> repository = new ConcurrentSkipListMap<>();
	
    private static final class AutoUnlockableResource {

		private Subscription unlockSubscription;
		private AcquiredResource acquiredResource;
		
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
    
	private interface ResourceAcquisitionCommandProcessor {
		ResourceAcquisitionResponse commit(String userName, String resource);
    }

    private final class ResourceLockCommandProcessor implements ResourceAcquisitionCommandProcessor {

		public ResourceLockCommandProcessor() {
		}

		@SuppressWarnings("synthetic-access")
		@Override
        public ResourceAcquisitionResponse commit(String userName, final String resource) {
			
			AutoUnlockableResource existingItem = repository.get(resource);
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

			Subscription unlockSubscription = worker.schedule(unlockAction, unlockTimeout.getDelayTime(), unlockTimeout.getUnit());
			AutoUnlockableResource unlockableResource = AutoUnlockableResource.createNew(unlockSubscription, newItem);
			repository.put(resource, unlockableResource);
            
			return ResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.LockSucceeded, newItem);
        }
    }

    private final class ResourceUnlockCommandProcessor implements ResourceAcquisitionCommandProcessor {

        public ResourceUnlockCommandProcessor() {
		}

		@SuppressWarnings("synthetic-access")
		@Override
        public ResourceAcquisitionResponse commit(String userName, String resource) {
			AutoUnlockableResource existingItem = repository.get(resource);
			AcquiredResource unlockedItem = AcquiredResource.createNew(userName, ResourceAcquisitionState.Unlocked, unlockTimeout);
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

    public TextResourceAcquisitionService(final Scheduler scheduler, final Time unlockTimeout) {
		this.worker = scheduler.createWorker();
		this.unlockTimeout =  unlockTimeout;
	}
    
    public TextResourceAcquisitionService(final Scheduler scheduler) {
    	this(scheduler, Time.getDefault());
	}

    public TextResourceAcquisitionService(final Time unlockTimeout) {
    	this(Schedulers.computation(), unlockTimeout);
	}

    public TextResourceAcquisitionService() {
    	this(Schedulers.computation(), Time.getDefault());
	}

    @Override
    public ResourceAcquisitionResponse commit(ResourceAcquisitionCommand command, String userName, String resource) {
        ResourceAcquisitionCommandProcessor commandProcessor = createCommandProcessor(command);
        return commandProcessor.commit(userName, resource);
    }
}
