package ras;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.concurrent.ConcurrentSkipListMap;

public class TextResourceAcquisitionService implements ResourceAcquisitionService<String> {

    private final ConcurrentSkipListMap<String, AcquiredResource<String>> cache = new ConcurrentSkipListMap<>();

    private static final class TextResourceAcquisitionResponse implements ResourceAcquisitionResponse {

        private final ResourceAcquisitionCommandResult commitResult;
        private final AcquiredResource<String> acquiredResource;

        private TextResourceAcquisitionResponse(ResourceAcquisitionCommandResult commitResult, AcquiredResource<String> acquiredResource) {

            this.commitResult = commitResult;
            this.acquiredResource = acquiredResource;
        }

        public static TextResourceAcquisitionResponse createNew(ResourceAcquisitionCommandResult commitResult, AcquiredResource<String> acquiredResource) {
            return new TextResourceAcquisitionResponse(commitResult, acquiredResource);
        }

        @Override
        public ResourceAcquisitionCommandResult getCommitResult() {
            return commitResult;
        }

        @Override
        public AcquiredResource<String> getResource() {
            return acquiredResource;
        }
    }

    private static final class AcquiredTextResource implements AcquiredResource<String>, Comparable<AcquiredTextResource> {

        private final String userName;
        private final String resource;
        private final ResourceAcquisitionState state;
        private final DateTime timestamp;

        private AcquiredTextResource(String userName, String resource, ResourceAcquisitionState state, DateTime timestamp) {
            this.userName = userName;
            this.resource = resource;
            this.state = state;
            this.timestamp = timestamp;
        }

        public static AcquiredResource<String> createNew(String userName, String resource, ResourceAcquisitionState state) {
            return new AcquiredTextResource(userName, resource, state, DateTime.now(DateTimeZone.UTC));
        }

        @Override
        public String getUserName() {
            return userName;
        }

        @Override
        public String getValue() {
            return resource;
        }

        @Override
        public ResourceAcquisitionState getState() {
            return state;
        }

        @Override
        public DateTime getUtcTimeStamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AcquiredTextResource that = (AcquiredTextResource) o;
            return resource.equals(that.resource) && (state == that.state) && userName.equals(that.userName);

        }

        @Override
        public int hashCode() {
            int result = userName.hashCode();
            result = 31 * result + resource.hashCode();
            result = 31 * result + state.hashCode();
            return result;
        }

        @Override
        public int compareTo(AcquiredTextResource o) {
            int cmp = this.userName.compareToIgnoreCase(o.getUserName());
            return cmp == 0 ? this.state.compareTo(o.getState()) : cmp;
        }
    }

    private interface ResourceAcquisitionCommandExecutor {

        ResourceAcquisitionResponse commit(String userName, String resource);
    }

    private static final class ResourceLockExecutor implements ResourceAcquisitionCommandExecutor {

        private final ConcurrentSkipListMap<String, AcquiredResource<String>> repository;

        public ResourceLockExecutor(ConcurrentSkipListMap<String, AcquiredResource<String>> repository) {
            this.repository = repository;
        }

        @Override
        public ResourceAcquisitionResponse commit(String userName, String resource) {
            AcquiredResource<String> newItem = AcquiredTextResource.createNew(userName, resource, ResourceAcquisitionState.Locked);
            AcquiredResource<String> oldItem = repository.putIfAbsent(resource, newItem);
            return oldItem == null ?
                    TextResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.LockSucceeded, newItem) :
                    TextResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.LockFailed, oldItem);
        }
    }

    private static final class ResourceUnlockExecutor implements ResourceAcquisitionCommandExecutor {

        private final ConcurrentSkipListMap<String, AcquiredResource<String>> repository;

        private ResourceUnlockExecutor(ConcurrentSkipListMap<String, AcquiredResource<String>> repository) {
            this.repository = repository;
        }

        @Override
        public ResourceAcquisitionResponse commit(String userName, String resource) {
            AcquiredResource<String> lockedItem = AcquiredTextResource.createNew(userName, resource, ResourceAcquisitionState.Locked);
            AcquiredResource<String> unlockedItem = AcquiredTextResource.createNew(userName, resource, ResourceAcquisitionState.Unlocked);
            if (!repository.containsKey(resource)) {
                return TextResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.UnlockFailed, unlockedItem);
            }
            return repository.remove(resource, lockedItem) ?
                    TextResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.UnlockSucceeded, unlockedItem) :
                    TextResourceAcquisitionResponse.createNew(ResourceAcquisitionCommandResult.UnlockFailed, repository.get(resource));
        }
    }

    private static ResourceAcquisitionCommandExecutor createCommandExecutor(ResourceAcquisitionCommand command, ConcurrentSkipListMap<String, AcquiredResource<String>> cache) {
        switch (command) {
            case Lock:
                return new ResourceLockExecutor(cache);
            case Unlock:
                return new ResourceUnlockExecutor(cache);
        }
        throw new IndexOutOfBoundsException(String.format("%s command is not supported", command));
    }

    @Override
    public ResourceAcquisitionResponse commit(ResourceAcquisitionCommand command, String userName, String resource) {
        ResourceAcquisitionCommandExecutor commandExecutor = createCommandExecutor(command, cache);
        return commandExecutor.commit(userName, resource);
    }
}
