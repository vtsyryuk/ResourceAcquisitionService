package ras;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

public class ResourceAcquisitionServiceTest {

    @Test
    public void testUnlockFailedForResourceThatNeverBeenLocked() {
        ResourceAcquisitionService<String> service = new TextResourceAcquisitionService();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");
        AcquiredResource resource = response.getResource();

        Assert.assertEquals(ResourceAcquisitionCommandResult.UnlockFailed, response.getCommitResult());
        Assert.assertEquals("User1", resource.getUserName());
        Assert.assertEquals("Item1", resource.getValue());
        Assert.assertEquals(ResourceAcquisitionState.Unlocked, resource.getState());
        Assert.assertEquals(DateTimeZone.UTC, resource.getUtcTimeStamp().getZone());
        Assert.assertEquals(DateTime.now(DateTimeZone.UTC).getDayOfYear(), resource.getUtcTimeStamp().getDayOfYear());
    }

    @Test
    public void testLockCommandSucceeds() {
        ResourceAcquisitionService<String> service = new TextResourceAcquisitionService();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item2");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item2", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
    }

    @Test
    public void testResourceCannotBeLockedTwice() {
        ResourceAcquisitionService<String> service = new TextResourceAcquisitionService();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        DateTime timestamp = response.getResource().getUtcTimeStamp();
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        Assert.assertEquals(timestamp, response.getResource().getUtcTimeStamp());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        Assert.assertEquals(timestamp, response.getResource().getUtcTimeStamp());
    }

    @Test
    public void testResourceCanBeLockedAgainAfterLockUnlock() {
        ResourceAcquisitionService<String> service = new TextResourceAcquisitionService();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.UnlockSucceeded, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Unlocked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        Assert.assertEquals("User2", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
    }

    @Test
    public void testCommitCanBeDoneOnlyByOwner() {
        ResourceAcquisitionService<String> service = new TextResourceAcquisitionService();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Unlock, "User2", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.UnlockFailed, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        Assert.assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        Assert.assertEquals("User1", response.getResource().getUserName());
        Assert.assertEquals("Item1", response.getResource().getValue());
        Assert.assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
    }
}