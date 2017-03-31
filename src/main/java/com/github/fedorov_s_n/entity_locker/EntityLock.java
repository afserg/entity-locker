package com.github.fedorov_s_n.entity_locker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class EntityLock<Id> implements Lock {

    private final Id id;
    private final EntitySync<Id> sync;

    EntityLock(Id id, EntitySync<Id> sync) {
        this.id = id;
        this.sync = sync;
    }

    @Override
    public void lock() {
        sync.setId(id);
        if (isGlobal()) {
            sync.acquire(EntitySync.AVOID_DEADLOCK);
        } else {
            sync.acquireShared(EntitySync.AVOID_DEADLOCK);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.setId(id);
        if (isGlobal()) {
            sync.acquireInterruptibly(EntitySync.IGNORE_DEADLOCK);
        } else {
            sync.acquireSharedInterruptibly(EntitySync.IGNORE_DEADLOCK);
        }
    }

    @Override
    public boolean tryLock() {
        sync.setId(id);
        return isGlobal()
            ? sync.tryAcquire(EntitySync.IGNORE_DEADLOCK)
            : sync.tryAcquireShared(EntitySync.IGNORE_DEADLOCK) != -1;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        sync.setId(id);
        return isGlobal()
            ? sync.tryAcquireNanos(EntitySync.IGNORE_DEADLOCK, unit.toNanos(time))
            : sync.tryAcquireSharedNanos(EntitySync.IGNORE_DEADLOCK, unit.toNanos(time));
    }

    @Override
    public void unlock() {
        sync.setId(id);
        if (isGlobal()) {
            sync.release(EntitySync.IGNORE_DEADLOCK);
        } else {
            sync.releaseShared(EntitySync.IGNORE_DEADLOCK);
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Not supported.");
    }

    public boolean isGlobal() {
        return id == null;
    }

    public Id getId() {
        return id;
    }
}
