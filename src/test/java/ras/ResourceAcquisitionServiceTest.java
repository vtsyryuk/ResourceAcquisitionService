package ras;

import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ResourceAcquisitionServiceTest {

    @Test
    @SuppressWarnings("static-method")
    public void testUnlockFailedForResourceThatNeverBeenLocked() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(Time.getDefault());
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");
        AcquiredResource resource = response.getResource();

        assertEquals(ResourceAcquisitionCommandResult.UnlockFailed, response.getCommitResult());
        assertEquals("User1", resource.getUserName());
        assertEquals(ResourceAcquisitionState.Unlocked, resource.getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());
    }

    @Test
    @SuppressWarnings("static-method")
    public void testLockCommandSucceeds() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item2");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());
    }

    @Test
    @SuppressWarnings("static-method")
    public void testResourceCanBeLockedTwice() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        Time u1LockTimestamp = response.getResource().getUtcTimeStamp();
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());
        assertEquals(u1LockTimestamp, response.getResource().getUtcTimeStamp());
    }

    @Test
    @SuppressWarnings("static-method")
    public void testCanLockAgainAfterTimeoutExpired() {
        final TestScheduler testScheduler = Schedulers.test();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(testScheduler);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());

        testScheduler.advanceTimeBy(15, TimeUnit.SECONDS);

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
        assertEquals(Time.getDefault(), response.getResource().getStateTimeout());
    }

    @Test
    @SuppressWarnings("static-method")
    public void testResourceCanBeLockedAgainAfterLockUnlock() {
        TestScheduler testScheduler = Schedulers.test();
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>(testScheduler);
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Unlock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.UnlockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Unlocked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User2", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        Time stateTimeout = response.getResource().getStateTimeout();
        testScheduler.advanceTimeBy(stateTimeout.getDelayTime(), stateTimeout.getUnit());
    }

    @Test
    @SuppressWarnings("static-method")
    public void testCommitCanBeDoneOnlyByOwner() {
        SimpleResourceAcquisitionService<String> service = new SimpleResourceAcquisitionService<>();
        ResourceAcquisitionResponse response = service.commit(ResourceAcquisitionCommand.Lock, "User1", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockSucceeded, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Unlock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.UnlockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());

        response = service.commit(ResourceAcquisitionCommand.Lock, "User2", "Item1");
        assertEquals(ResourceAcquisitionCommandResult.LockFailed, response.getCommitResult());
        assertEquals("User1", response.getResource().getUserName());
        assertEquals(ResourceAcquisitionState.Locked, response.getResource().getState());
    }
}