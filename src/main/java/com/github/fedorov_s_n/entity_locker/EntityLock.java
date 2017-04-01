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
        setId();
        if (isEffectivelyGlobal()) {
            sync.acquire(EntitySync.AVOID_DEADLOCK);
        } else {
            sync.acquireShared(EntitySync.AVOID_DEADLOCK);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        setId();
        if (isEffectivelyGlobal()) {
            sync.acquireInterruptibly(EntitySync.IGNORE_DEADLOCK);
        } else {
            sync.acquireSharedInterruptibly(EntitySync.IGNORE_DEADLOCK);
        }
    }

    @Override
    public boolean tryLock() {
        setId();
        return isEffectivelyGlobal()
            ? sync.tryAcquire(EntitySync.IGNORE_DEADLOCK)
            : sync.tryAcquireShared(EntitySync.IGNORE_DEADLOCK) != -1;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        setId();
        return isEffectivelyGlobal()
            ? sync.tryAcquireNanos(EntitySync.IGNORE_DEADLOCK, unit.toNanos(time))
            : sync.tryAcquireSharedNanos(EntitySync.IGNORE_DEADLOCK, unit.toNanos(time));
    }

    @Override
    public void unlock() {
        setId();
        if (isGlobal()) {
            sync.release(EntitySync.IGNORE_DEADLOCK);
        } else {
            sync.releaseShared(EntitySync.IGNORE_DEADLOCK);
        }
    }

    @Override
    public Condition newCondition() {
        return sync.new ConditionObject();
    }

    public boolean isGlobal() {
        return id == null || sync.isHeldExclusively();
    }

    public boolean isEffectivelyGlobal() {
        return isGlobal() || sync.shouldEscalateToGlobal();
    }

    public Id getId() {
        return id;
    }

    private void setId() {
        sync.setId(getEffectiveId());
    }

    public Id getEffectiveId() {
        return isEffectivelyGlobal() ? null : id;
    }
}
