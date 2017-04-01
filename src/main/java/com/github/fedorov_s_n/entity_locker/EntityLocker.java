package com.github.fedorov_s_n.entity_locker;

import java.util.function.Supplier;

public class EntityLocker<Id> {

    public static final int DEFAULT_ESCALATION_THRESHOLD = 8;
    private final EntitySync<Id> sync;

    public EntityLocker() {
        this(DEFAULT_ESCALATION_THRESHOLD);
    }

    public EntityLocker(int escalationThreshold) {
        this.sync = new EntitySync<>(escalationThreshold);
    }

    public EntityLock<Id> lock(Id id) {
        return new EntityLock<>(id, sync);
    }

    public <T> T exclusive(Id id, Supplier<T> action) {
        EntityLock<Id> lock = lock(id);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
