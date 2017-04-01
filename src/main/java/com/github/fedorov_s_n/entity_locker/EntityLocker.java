package com.github.fedorov_s_n.entity_locker;

import java.util.function.Supplier;

/**
 * Class that provides locking utilities for arbitrary amount of entities
 * distinguished by id.
 *
 * @author Sergey N. Fedorov
 * @param <Id> type of entity id
 */
public class EntityLocker<Id> {

    /**
     * Default amount of distinct id's that should be locked by single thread to
     * switch it to global lock mode
     */
    public static final int DEFAULT_ESCALATION_THRESHOLD = 8;
    private final EntitySync<Id> sync;

    /**
     * Creates locker class with default escalation threshold
     */
    public EntityLocker() {
        this(DEFAULT_ESCALATION_THRESHOLD);
    }

    /**
     * Creates locker class with specified escalation threshold
     *
     * @param escalationThreshold amount of distinct id's that should be locked
     * by single thread to switch it to global lock mode
     */
    public EntityLocker(int escalationThreshold) {
        this.sync = new EntitySync<>(escalationThreshold);
    }

    /**
     * Create a lightweight Lock object that can be used to organize exclusive
     * access to entities
     *
     * @param id entity id or null for global lock
     * @return lock for given entity
     */
    public EntityLock<Id> lock(Id id) {
        return new EntityLock<>(id, sync);
    }

    /**
     * Creates an exclusive access section for given entity id
     *
     * @param <T> output object type
     * @param id entity id or null for global locking
     * @param action Function that has exclusive access to entity with given id
     * and returns object of type T
     * @return returned object upon lock release
     */
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
