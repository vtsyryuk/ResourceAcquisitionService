package ras;

public interface ResourceAcquisitionResponse {
    ResourceAcquisitionCommandResult getCommitResult();

    AcquiredResource getResource();
}
