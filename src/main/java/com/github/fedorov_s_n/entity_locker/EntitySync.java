package com.github.fedorov_s_n.entity_locker;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

class EntitySync<Id> extends AbstractQueuedSynchronizer {

    static class LockState {

        final Thread thread = Thread.currentThread();
        volatile int lockCount = 1;

        boolean currentThread() {
            return Thread.currentThread().equals(thread);
        }
    }

    static class ThreadState<Id> {

        Id id = null;
        Map<Id, LockState> acquired = new HashMap<>();
        boolean sleeping = false;
    }

    final static int AVOID_DEADLOCK = 0;
    final static int IGNORE_DEADLOCK = 1;
    final int escalationThreshold;
    final ThreadLocal<ThreadState<Id>> state = ThreadLocal.withInitial(ThreadState::new);
    final AtomicReference<Map<Id, LockState>> locks = new AtomicReference<>(new HashMap<>());

    EntitySync(int escalationThreshold) {
        this.escalationThreshold = escalationThreshold;
    }

    @Override
    public boolean tryAcquire(int sleepAction) {
        ThreadState<Id> threadState = state.get();
        Map<Id, LockState> presentLocks;
        Map<Id, LockState> proposedLocks;
        Supplier<Boolean> deferredAction;
        do {
            presentLocks = locks.get();
            proposedLocks = new HashMap<>(presentLocks);
            LockState presentState = proposedLocks.get(null);
            boolean failedAcquire
                = (presentState != null && !presentState.currentThread())
                | (threadState.sleeping
                    ? !proposedLocks.isEmpty()
                    : proposedLocks.size() > threadState.acquired.size());
            if (failedAcquire) {
                if (threadState.sleeping || sleepAction != AVOID_DEADLOCK) {
                    return false;
                }
                proposedLocks.keySet().removeAll(threadState.acquired.keySet());
                deferredAction = () -> {
                    threadState.sleeping = true;
                    releaseShared(AVOID_DEADLOCK);
                    return false;
                };
            } else {
                if (presentState != null) {
                    presentState.lockCount++;
                    threadState.sleeping = false;
                    return true;
                }
                LockState proposedState = new LockState();
                proposedLocks.clear();
                proposedLocks.put(null, proposedState);
                deferredAction = () -> {
                    threadState.sleeping = false;
                    proposedState.lockCount += threadState.acquired
                        .values()
                        .stream()
                        .mapToInt(s -> s.lockCount)
                        .sum();
                    threadState.acquired.clear();
                    threadState.acquired.put(null, proposedState);
                    setExclusiveOwnerThread(Thread.currentThread());
                    return true;
                };
            }
        } while (!locks.compareAndSet(presentLocks, proposedLocks));
        return deferredAction.get();
    }

    @Override
    public boolean tryRelease(int arg) {
        ThreadState<Id> threadState = state.get();
        Map<Id, LockState> presentLocks = locks.get();
        LockState presentState = presentLocks.get(null);
        if (presentState == null || !presentState.currentThread()) {
            throw new IllegalMonitorStateException(
                "Shouldn't attempt to release global lock without acquiring it"
            );
        } else if (presentState.lockCount > 1) {
            presentState.lockCount--;
            return false;
        } else {
            Map<Id, LockState> proposedLocks = new HashMap<>(presentLocks);
            proposedLocks.remove(null);
            setExclusiveOwnerThread(null);
            threadState.acquired.remove(null);
            if (!locks.compareAndSet(presentLocks, proposedLocks)) {
                throw new IllegalMonitorStateException(
                    "State was changed without exclusive access"
                );
            }
            return true;
        }
    }

    @Override
    public boolean isHeldExclusively() {
        LockState lockState = state.get().acquired.get(null);
        return lockState != null && lockState.lockCount > 0;
    }

    @Override
    public int tryAcquireShared(int sleepAction) {
        ThreadState<Id> threadState = state.get();
        Id id = Objects.requireNonNull(threadState.id);
        Map<Id, LockState> presentLocks;
        Map<Id, LockState> proposedLocks;
        IntSupplier deferredAction;
        do {
            presentLocks = locks.get();
            LockState globalState = presentLocks.get(null);
            if (globalState == null) {
                // ok
            } else if (globalState.currentThread()) {
                throw new IllegalMonitorStateException(
                    "Shouldn't attempt to acquire entity lock after acquiring global lock"
                );
            } else {
                // another thread has exclusive access
                return -1;
            }
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
            } else if (presentState.currentThread()) {
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
    public boolean tryReleaseShared(int sleepAction) {
        if (sleepAction == AVOID_DEADLOCK) {
            return true;
        }
        ThreadState<Id> threadState = state.get();
        Id id = Objects.requireNonNull(threadState.id);
        Map<Id, LockState> presentLocks;
        Map<Id, LockState> proposedLocks;
        do {
            presentLocks = locks.get();
            proposedLocks = new HashMap<>(presentLocks);
            LockState presentState = proposedLocks.get(id);
            if (presentState == null) {
                return false;
            } else if (presentState.currentThread()) {
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
        threadState.acquired.remove(id);
        return true;
    }

    public void lock(Id id) {
        setId(id);
        if (isEffectivelyGlobal(id)) {
            acquire(AVOID_DEADLOCK);
        } else {
            acquireShared(AVOID_DEADLOCK);
        }
    }

    public void lockInterruptibly(Id id) throws InterruptedException {
        setId(id);
        if (isEffectivelyGlobal(id)) {
            acquireInterruptibly(IGNORE_DEADLOCK);
        } else {
            acquireSharedInterruptibly(IGNORE_DEADLOCK);
        }
    }

    public boolean tryLock(Id id) {
        setId(id);
        return isEffectivelyGlobal(id)
            ? tryAcquire(IGNORE_DEADLOCK)
            : tryAcquireShared(IGNORE_DEADLOCK) != -1;
    }

    public boolean tryLock(Id id, long time, TimeUnit unit) throws InterruptedException {
        setId(id);
        return isEffectivelyGlobal(id)
            ? tryAcquireNanos(IGNORE_DEADLOCK, unit.toNanos(time))
            : tryAcquireSharedNanos(IGNORE_DEADLOCK, unit.toNanos(time));
    }

    public void unlock(Id id) {
        setId(id);
        if (isGlobal(id)) {
            release(IGNORE_DEADLOCK);
        } else {
            releaseShared(IGNORE_DEADLOCK);
        }
    }

    public boolean isGlobal(Id id) {
        return id == null || isHeldExclusively();
    }

    public boolean isEffectivelyGlobal(Id id) {
        return isGlobal(id) || state.get().acquired.size() >= escalationThreshold;
    }

    private void setId(Id id) {
        state.get().id = isEffectivelyGlobal(id) ? null : id;
    }
}
