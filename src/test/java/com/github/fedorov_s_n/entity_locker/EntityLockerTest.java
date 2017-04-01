package com.github.fedorov_s_n.entity_locker;

import java.util.concurrent.Callable;
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

    private void await(Callable<Boolean> func) {
        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(func);
    }

    private void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void testDeadlockPositive() {
        EntityLocker<Integer> locker = new EntityLocker<>();
        AtomicInteger state = new AtomicInteger();
        Runnable agent1 = () -> {
            locker.exclusive(1, () -> {
                state.incrementAndGet();
                System.out.println("agent 1 got access to lock 1, state is " + state);
                await(() -> state.get() >= 2);
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
                await(() -> state.get() >= 2);
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
        await(() -> state.get() == 4);
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
                await(() -> state.get() >= 2);
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
                await(() -> state.get() >= 2);
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
            await(() -> state.get() == 4);
            fail("Unreachable because of deadlock");
        } catch (ConditionTimeoutException ex) {
            t1.interrupt();
            t2.interrupt();
            await(() -> exceptions.get() == 2);
        }
    }

    @Test
    public void testGlobalPositive() {
        EntityLocker<Integer> locker = new EntityLocker<>();
        AtomicInteger state = new AtomicInteger();
        AtomicInteger flag = new AtomicInteger();
        Runnable agent1 = () -> {
            locker.exclusive(1, () -> {
                state.incrementAndGet();
                await(200);
                System.out.println("agent 1 got access to lock 1, state is " + state);
                return null;
            });
            System.out.println("agent 1 has finished work");
        };
        Runnable agent2 = () -> {
            locker.exclusive(2, () -> {
                state.incrementAndGet();
                System.out.println("agent 2 got access to lock 1, state is " + state);
                return locker.exclusive(null, () -> {
                    flag.set(1);
                    System.out.println("agent 2 got access to lock 2, state is " + state);
                    return null;
                });
            });
            System.out.println("agent 2 has finished work");
        };
        Runnable agent3 = () -> {
            await(200);
            locker.exclusive(null, () -> {
                state.updateAndGet(i -> i * 2);
                System.out.println("agent 3 got access to lock 1, state is " + state);
                return null;
            });
            System.out.println("agent 3 has finished work");
        };
        new Thread(agent1).start();
        new Thread(agent2).start();
        new Thread(agent3).start();
        await(() -> state.get() == 4);
        await(() -> flag.get() == 1);
    }
}
