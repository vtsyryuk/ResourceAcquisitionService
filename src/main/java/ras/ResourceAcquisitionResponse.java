package ras;

public final class ResourceAcquisitionResponse {
    
    private final ResourceAcquisitionCommandResult commitResult;
    private final AcquiredResource acquiredResource;

    private ResourceAcquisitionResponse(ResourceAcquisitionCommandResult commitResult, AcquiredResource acquiredResource) {
        this.commitResult = commitResult;
        this.acquiredResource = acquiredResource;
    }

    public static ResourceAcquisitionResponse createNew(ResourceAcquisitionCommandResult commitResult, AcquiredResource acquiredResource) {
        return new ResourceAcquisitionResponse(commitResult, acquiredResource);
    }

    public ResourceAcquisitionCommandResult getCommitResult() {
        return commitResult;
    }

    public AcquiredResource getResource() {
        return acquiredResource;
    }
}
