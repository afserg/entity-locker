package com.github.fedorov_s_n.entity_locker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

public class EntityLockerTest {

    public EntityLockerTest() {
    }

    @Test
    public void testDeadlockPositive() {
        EntityLocker<Integer> locker = new EntityLocker<>();
        AtomicInteger state = new AtomicInteger();
        Runnable agent1 = () -> {
            locker.exclusive(1, () -> {
                state.incrementAndGet();
                System.out.println("agent 1 got access to lock 1, state is " + state);
                Awaitility.await().until(() -> state.get() >= 2);
                return locker.exclusive(2, () -> {
                    state.incrementAndGet();
                    System.out.println("agent 1 got access to lock 2, state is " + state);
                    return null;
                });
            });
            System.out.println("agent 1 has finished work");
        };
        Runnable agent2 = () -> {
            locker.exclusive(2, () -> {
                state.incrementAndGet();
                System.out.println("agent 2 got access to lock 1, state is " + state);
                Awaitility.await().until(() -> state.get() >= 2);
                return locker.exclusive(1, () -> {
                    state.incrementAndGet();
                    System.out.println("agent 2 got access to lock 2, state is " + state);
                    return null;
                });
            });
            System.out.println("agent 2 has finished work");
        };
        new Thread(agent1).start();
        new Thread(agent2).start();
        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> state.get() == 4);
    }

    @Test
    public void testDeadlockNegative() {
        EntityLocker<Integer> locker = new EntityLocker<>();
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger state = new AtomicInteger();
        Runnable agent1 = () -> {
            try {
                Lock lock1 = locker.lock(1);
                lock1.lockInterruptibly();
                state.incrementAndGet();
                System.out.println("agent 1 got access to lock 1, state is " + state);
                Awaitility.await().until(() -> state.get() >= 2);
                Lock lock2 = locker.lock(2);
                lock2.lockInterruptibly();
                state.incrementAndGet();
                System.out.println("agent 1 got access to lock 2, state is " + state);
                lock2.unlock();
                lock1.unlock();
                System.out.println("agent 1 has finished work");
            } catch (InterruptedException ex) {
                exceptions.incrementAndGet();
            }
        };
        Runnable agent2 = () -> {
            try {
                Lock lock1 = locker.lock(2);
                lock1.lockInterruptibly();
                state.incrementAndGet();
                System.out.println("agent 2 got access to lock 1, state is " + state);
                Awaitility.await().until(() -> state.get() >= 2);
                Lock lock2 = locker.lock(1);
                lock2.lockInterruptibly();
                state.incrementAndGet();
                System.out.println("agent 2 got access to lock 2, state is " + state);
                lock2.unlock();
                lock1.unlock();
                System.out.println("agent 2 has finished work");
            } catch (InterruptedException ex) {
                exceptions.incrementAndGet();
            }
        };
        Thread t1 = new Thread(agent1);
        Thread t2 = new Thread(agent2);
        t1.start();
        t2.start();
        try {
            Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> state.get() == 4);
            fail("Unreachable because of deadlock");
        } catch (ConditionTimeoutException ex) {
            t1.interrupt();
            t2.interrupt();
            Awaitility.await().until(() -> exceptions.get() == 2);
        }
    }

}
