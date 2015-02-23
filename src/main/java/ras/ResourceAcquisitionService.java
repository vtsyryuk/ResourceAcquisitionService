package ras;

public interface ResourceAcquisitionService<T> {
    ResourceAcquisitionResponse commit(ResourceAcquisitionCommand command, String userName, T resource);
}
