package com.proofpoint.event.monitor.executors;

import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestSerialScheduledExecutorService
{
    private SerialScheduledExecutorService executorService;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        executorService = new SerialScheduledExecutorService();
    }

    @Test
    public void testRunOnce()
            throws Exception
    {
        Counter counter = new Counter();
        executorService.execute(counter);

        assertEquals(counter.getCount(), 1);
    }

    @Test
    public void testSubmitRunnable()
            throws Exception
    {
        Counter counter = new Counter();
        Future<Integer> future = executorService.submit(counter, 10);

        assertEquals(counter.getCount(), 1);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals((int)future.get(), 10);
    }

    @Test
    public void testSubmitCallable()
            throws Exception
    {
        CallableCounter counter = new CallableCounter();
        Future<Integer> future = executorService.submit(counter);

        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals((int)future.get(), 1);
    }

    @Test
    public void testScheduleRunnable()
            throws Exception
    {
        Counter counter = new Counter();
        Future<?> future = executorService.schedule(counter, 10, TimeUnit.MINUTES);

        executorService.elapseTime(Duration.valueOf("9m"));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(Duration.valueOf("1m"));
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);
     }

    @Test
    public void testCancelScheduledRunnable()
            throws Exception
    {
        Counter counter = new Counter();
        Future<?> future = executorService.schedule(counter, 10, TimeUnit.MINUTES);

        executorService.elapseTime(Duration.valueOf("9m"));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        future.cancel(true);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());

        executorService.elapseTime(Duration.valueOf("1m"));
        assertEquals(counter.getCount(), 0);
     }

    @Test
    public void testScheduleCallable()
            throws Exception
    {
        CallableCounter counter = new CallableCounter();
        Future<Integer> future = executorService.schedule(counter, 10, TimeUnit.MINUTES);

        executorService.elapseTime(Duration.valueOf("9m"));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(Duration.valueOf("1m"));
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 1);
        assertEquals((int)future.get(), 1);
     }

    @Test(expectedExceptions = CancellationException.class)
    public void testCancelScheduledCallable()
            throws Exception
    {
        CallableCounter counter = new CallableCounter();
        Future<Integer> future = executorService.schedule(counter, 10, TimeUnit.MINUTES);

        executorService.elapseTime(Duration.valueOf("9m"));

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        future.cancel(true);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());

        executorService.elapseTime(Duration.valueOf("1m"));
        assertEquals(counter.getCount(), 0);

        // Should throw
        future.get();
    }

    @Test
    public void testRepeatingRunnable()
            throws Exception
    {
        Counter counter = new Counter();
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.MINUTES);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        // After 9 minutes, we shouldn't have run yet, and should have 1 minute left
        executorService.elapseTime(Duration.valueOf("9m"));
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(future.getDelay(TimeUnit.MINUTES), 1);
        assertEquals(counter.getCount(), 0);

        // After 1 more minute, we should have run once, and should have 5 minutes remaining
        executorService.elapseTime(Duration.valueOf("1m"));
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(future.getDelay(TimeUnit.MINUTES), 5);
        assertEquals(counter.getCount(), 1);

        // After another 10 minutes, we should have run twice more
        executorService.elapseTime(Duration.valueOf("10m"));
        assertEquals(counter.getCount(), 3);

    }

    @Test
    public void testCancelRepeatingRunnableBeforeFirstRun()
            throws Exception
    {
        Counter counter = new Counter();
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.MINUTES);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(Duration.valueOf("9m"));

        future.cancel(true);

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(Duration.valueOf("1m"));
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 0);
    }

    @Test
    public void testCancelRepeatingRunnableAfterFirstRun()
            throws Exception
    {
        Counter counter = new Counter();
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(counter, 10, 5, TimeUnit.MINUTES);

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(counter.getCount(), 0);

        executorService.elapseTime(Duration.valueOf("10m"));

        future.cancel(true);

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 1);

        executorService.elapseTime(Duration.valueOf("5m"));
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertEquals(counter.getCount(), 1);
    }

    @Test
    public void testMultipleRepeatingRunnables()
            throws Exception
    {
        Counter countEveryMinute = new Counter();
        Counter countEveryTwoMinutes = new Counter();
        ScheduledFuture<?> futureEveryMinute = executorService.scheduleAtFixedRate(countEveryMinute, 1, 1, TimeUnit.MINUTES);
        ScheduledFuture<?> futureEveryTwoMinutes = executorService.scheduleAtFixedRate(countEveryTwoMinutes, 2, 2, TimeUnit.MINUTES);

        executorService.elapseTime(Duration.valueOf("7m"));

        assertEquals(countEveryMinute.getCount(), 7);
        assertEquals(countEveryTwoMinutes.getCount(), 3);

        futureEveryMinute.cancel(true);

        executorService.elapseTime(Duration.valueOf("1m"));
        assertEquals(countEveryMinute.getCount(), 7);
        assertEquals(countEveryTwoMinutes.getCount(), 4);
    }

    static class Counter
            implements Runnable
    {
        private int count = 0;

        @Override
        public void run()
        {
            count++;
        }

        public int getCount()
        {
            return count;
        }
    }

    static class CallableCounter
            implements Callable<Integer>
    {
        private int count = 0;

        @Override
        public Integer call()
                throws Exception
        {
            return ++count;
        }

        public int getCount()
        {
            return count;
        }
    }
}
