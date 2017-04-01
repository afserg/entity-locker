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
        sync.lock(id);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.lockInterruptibly(id);
    }

    @Override
    public boolean tryLock() {
        return sync.tryLock(id);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryLock(id, time, unit);
    }

    @Override
    public void unlock() {
        sync.unlock(id);
    }

    @Override
    public Condition newCondition() {
        return sync.new ConditionObject();
    }

    public boolean isGlobal() {
        return sync.isGlobal(id);
    }

    public boolean isEffectivelyGlobal() {
        return sync.isEffectivelyGlobal(id);
    }

    public Id getId() {
        return id;
    }
}
