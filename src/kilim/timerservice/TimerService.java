package kilim.timerservice;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import kilim.Cell;
import kilim.Scheduler;
import kilim.concurrent.MPSCQueue;

public class TimerService {
    private final MPSCQueue<Timer> timerQueue;
    private final TimerPriorityHeap timerHeap;
    private ScheduledExecutorService timer;
    final private Lock lock;

    public TimerService() {
        timerHeap = new TimerPriorityHeap();
        timerQueue = new MPSCQueue<Timer>(Integer.getInteger("kilim.maxpendingtimers",200000));
        timer = Executors.newSingleThreadScheduledExecutor();
        lock = new java.util.concurrent.locks.ReentrantLock();
    }

    public void shutdown() {
        timer.shutdown();
    }
    
    public void submit(Timer t) {
        if (t.onQueue.compareAndSet(false, true)) {
            if (!timerQueue.offer(t)) {
                new Exception(
                        "Maximum pending timers limit:"
                        + Integer.getInteger("kilim.maxpendingtimers", 100000)
                        + " exceeded, set kilim.maxpendingtimers property"
                ).printStackTrace();
            }
        }
    }

    /**
     * return true if empty at the moment that the lock is acquired
     *  allowing false negatives if operations are ongoing
     */
    public boolean isEmptyLazy() {
        if (lock.tryLock())
            try { return timerHeap.isEmpty() && timerQueue.isEmpty(); }
            finally { lock.unlock(); }
        return false;
    }

    
    volatile long first = 0;
    
    public void trigger(final ThreadPoolExecutor executor) {
        int maxtry = 5;

        long clock = System.currentTimeMillis(), sched = 0, prev = -1;
        int retry = -1;
        while ((retry < 0 || !timerQueue.isEmpty() || (sched > 0 && sched <= clock))
                && ++retry < maxtry
                && lock.tryLock()) {
            prev = clock;
            try { 
                sched = doTrigger(clock);
            } finally { lock.unlock(); }
            clock = System.currentTimeMillis();
        }

//        if (Scheduler.getDefaultScheduler().isFullish())
//            return;
        
        // todo: cycle the queues and require all
        if (retry==maxtry) {
            Scheduler.getDefaultScheduler().launch(executor.getQueue(),new WatchdogTask());
        }
        else if (sched > 0 & (first <= prev | sched < first)) {
            // failing to set first is ok
            // just means we could generate a duplicate timer
            timer.schedule(new Watcher(executor),(first=sched)-clock,TimeUnit.MILLISECONDS);
//            int c2 = cnt.incrementAndGet();
//            if (c2==c3) { c3<<=1; System.out.println("sched: " + c2); }
        }
    }

    volatile int c3 = 8;
    AtomicInteger cnt = new AtomicInteger();
    
    
    
    private long doTrigger(long currentTime) {

        Timer[] buf = new Timer[100];
        
        long max = -1;
        Timer t = null;
        while ((t = timerHeap.peek())!=null && t.getExecutionTime()==-1)
            timerHeap.poll();
        t = null;
        int i = 0;
        timerQueue.fill(buf);
        do {
            for (i = 0; i<buf.length; i++) {
                if (buf[i]==null)
                    break;
                buf[i].onQueue.set(false);
                long executionTime = buf[i].getExecutionTime();
                if (executionTime<0) {
                    buf[i] = null;
                    continue;
                }
                if (executionTime<=currentTime)
                    buf[i].es.onEvent(null,Cell.timedOut);
                else if (!buf[i].onHeap) {
                    timerHeap.add(buf[i]);
                    buf[i].onHeap = true;
                }
                else 
                    timerHeap.reschedule(buf[i].index);
                buf[i] = null;
            }
        } while (i==100);
        while (!timerHeap.isEmpty()) {
            t = timerHeap.peek();
            long executionTime = t.getExecutionTime();
            if (executionTime<0) {
                t.onHeap = false;
                timerHeap.poll();
                continue; // No action required, poll queue
                // again
            }
            if (executionTime<=currentTime) {
                t.onHeap = false;
                timerHeap.poll();
                t.es.onEvent(null,Cell.timedOut);
            } else
                return executionTime;
        }
        return 0L;
    }
    private static class Watcher implements Runnable {
        ThreadPoolExecutor executor;
        Watcher(ThreadPoolExecutor $executor) { executor = $executor; }

        @Override
        public void run() {
            if (executor.getQueue().size()==0)
                executor.getQueue().add(new WatchdogTask());
        }
    }
    private static class WatchdogTask implements Runnable {
        @Override
        public void run() {}
    }

}
