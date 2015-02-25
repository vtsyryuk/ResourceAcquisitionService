package ras;

public final class ResourceAcquisitionResponse {

    private final ResourceAcquisitionCommandResult commitResult;
    private final AcquiredResource acquiredResource;

    public ResourceAcquisitionResponse(final ResourceAcquisitionCommandResult commitResult,
                                       final AcquiredResource acquiredResource) {
        this.commitResult = commitResult;
        this.acquiredResource = acquiredResource;
    }

    public final ResourceAcquisitionCommandResult getCommitResult() {
        return commitResult;
    }

    public final AcquiredResource getResource() {
        return acquiredResource;
    }
}
