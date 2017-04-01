# Entity locker

Class that provides locking utilities for arbitrary amount of entities distinguished by id.

## Features

  1. Unlimited threads count
  2. Unlimited entities count
  3. Arbitrary entity id type
  4. Deadlock protection: `EntityLock.lock()` and `EntityLocker.exclusive()` methods prevent possible deadlocks
  5. Global (across all entities) locks
  6. Lock escalation: if a single thread has locked too many entities it promotes to global lock mode
  7. All locks are reentrant and reusable

## Usage examples

### Exclusive entity access

    EntityLocker<Integer> locker = new EntityLocker<>();
    locker.exclusive(1, () -> {
         // do something exclusive for entity with id 1
    });

or a longer example


    EntityLocker<Integer> locker = new EntityLocker<>();
    EntityLock lock = locker.lock(1);
    lock.lock();
    try {
        // do something
    } finally {
        lock.unlock();
    }

### Global access

    EntityLocker<T> locker = new EntityLocker<>();
    locker.exclusive(null, () -> {
         // do something
    });

### Specify timeout

    EntityLocker<Integer> locker = new EntityLocker<>();
    EntityLock lock = locker.lock(1);
    if (lock.tryLock(5, TimeUnit.SECONDS)) {
        try {
            // do something
        } finally {
            lock.unlock();
        }
    }

## Notes

  1. `null` id isn't allowed as `null` is reserved value for global locks
  2. Lock relies on correct implementation of `equals`/`hashCode` methods of id object
  3. Deadlock protection is only implemented for `EntityLock.lock()` and `EntityLocker.exclusive()` methods.
  4. After entering global lock mode thread keeps exclusive access until all the locks are released
  5. Call to lock and unlock methods of same lock from different threads leads to undefined behavior
  6. Requires java 8 to run
