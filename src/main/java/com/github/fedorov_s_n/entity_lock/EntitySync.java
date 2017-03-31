package com.github.fedorov_s_n.entity_lock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.IntSupplier;

class EntitySync<Id> extends AbstractQueuedSynchronizer {

    static class LockState {

        final Thread thread;
        volatile int lockCount;

        LockState() {
            this.thread = Thread.currentThread();
            this.lockCount = 1;
        }
    }

    static class ThreadState<Id> {

        Id id = null;
        Map<Id, LockState> acquired = new HashMap<>();
        boolean sleeping = false;
    }

    final static int AVOID_DEADLOCK = 0;
    final static int IGNORE_DEADLOCK = 1;
    final ThreadLocal<ThreadState<Id>> state = ThreadLocal.withInitial(ThreadState::new);
    final AtomicReference<Map<Id, LockState>> locks = new AtomicReference<>(new HashMap<>());

    @Override
    public boolean tryAcquire(int arg) {
        return super.tryAcquire(arg);
    }

    @Override
    public int tryAcquireShared(int sleepAction) {
        ThreadState<Id> threadState = state.get();
        Id id = threadState.id;
        Map<Id, LockState> presentLocks;
        Map<Id, LockState> proposedLocks;
        IntSupplier deferredAction;
        do {
            presentLocks = locks.get();
            proposedLocks = new HashMap<>(presentLocks);
            if (threadState.sleeping) {
                for (Map.Entry<Id, LockState> e : threadState.acquired.entrySet()) {
                    if (proposedLocks.putIfAbsent(e.getKey(), e.getValue()) != null) {
                        // do not wake up
                        return -1;
                    }
                }
            }
            LockState proposedState = new LockState();
            LockState presentState = proposedLocks.putIfAbsent(id, proposedState);
            if (presentState == null) {
                deferredAction = () -> {
                    threadState.acquired.put(id, proposedState);
                    threadState.sleeping = false;
                    return 1;
                };
            } else if (Thread.currentThread().equals(presentState.thread)) {
                deferredAction = () -> {
                    presentState.lockCount++;
                    threadState.sleeping = false;
                    return 1;
                };
            } else if (sleepAction == AVOID_DEADLOCK) {
                proposedLocks.keySet().removeAll(threadState.acquired.keySet());
                deferredAction = () -> {
                    threadState.sleeping = true;
                    releaseShared(AVOID_DEADLOCK);
                    return -1;
                };
            } else {
                if (threadState.sleeping) {
                    proposedLocks = presentLocks;
                }
                deferredAction = () -> -1;
            }
        } while (!locks.compareAndSet(presentLocks, proposedLocks));
        return deferredAction.getAsInt();
    }

    @Override
    public boolean tryRelease(int arg) {
        return super.tryRelease(arg);
    }

    @Override
    public boolean tryReleaseShared(int sleepAction) {
        if (sleepAction == AVOID_DEADLOCK) {
            return true;
        }
        Id id = getId();
        Map<Id, LockState> presentLocks;
        Map<Id, LockState> proposedLocks;
        do {
            presentLocks = locks.get();
            proposedLocks = new HashMap<>(presentLocks);
            LockState presentState = proposedLocks.get(id);
            if (presentState == null) {
                return false;
            } else if (Thread.currentThread().equals(presentState.thread)) {
                if (presentState.lockCount == 1) {
                    proposedLocks.remove(id);
                } else {
                    presentState.lockCount--;
                    return true;
                }
            } else {
                throw new IllegalStateException();
            }
        } while (!locks.compareAndSet(presentLocks, proposedLocks));
        return true;
    }

    public Id getId() {
        return state.get().id;
    }

    public void setId(Id id) {
        state.get().id = id;
    }
}
