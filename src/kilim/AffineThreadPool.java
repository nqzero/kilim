package kilim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kilim.timerservice.TimerService;

public class AffineThreadPool {
	private static final int MAX_QUEUE_SIZE = 4096;
	private static final String colon_ = ":";

	protected static int getCurrentThreadId() {
		String name = Thread.currentThread().getName();
                
		int sIndex = name.indexOf(colon_);
		try { 
                    return Integer.parseInt(name.substring(sIndex + 1, name.length()));
                }
                catch (Exception ex) {}
                return name.hashCode();
	}

	private int nThreads_;
	private String poolName_;
	private AtomicInteger currentIndex_ = new AtomicInteger(0);
	private List<BlockingQueue<Runnable>> queues_ = new ArrayList<BlockingQueue<Runnable>>();
	private List<KilimStats> queueStats_ = new ArrayList<KilimStats>();
	private List<KilimThreadPoolExecutor> executorService_ = new ArrayList<KilimThreadPoolExecutor>();

	public AffineThreadPool(int nThreads, String name, TimerService timerService) {
		this(nThreads, MAX_QUEUE_SIZE, name, timerService);
	}

	public AffineThreadPool(int nThreads, int queueSize, String name,
			TimerService timerservice) {
		nThreads_ = nThreads;
		poolName_ = name;

		for (int i = 0; i < nThreads; ++i) {
			String threadName = name + colon_ + i;
			BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(
					queueSize);
			queues_.add(queue);

			KilimThreadPoolExecutor executorService = new KilimThreadPoolExecutor(
					i, 1, queue, new ThreadFactoryImpl(threadName),
					timerservice);
			executorService_.add(executorService);

			queueStats_.add(new KilimStats(12, "num"));
		}
	}

	public long getTaskCount() {
		long totalRemainingCapacity = 0L;
		for (BlockingQueue<Runnable> queue : queues_) {
			totalRemainingCapacity += queue.size();
		}
		return totalRemainingCapacity;
	}

	private int getNextIndex() {
		int value = 0, newValue = 0;
		do {
			value = currentIndex_.get();
			newValue = ((value != Integer.MAX_VALUE) ? (value + 1) : 0);
		} while (!currentIndex_.compareAndSet(value, newValue));
		return (newValue) % nThreads_;
	}

	public int publish(Task task) {
		int index = getNextIndex();
		task.setTid(index);
		return publish(index, task);
	}

        volatile boolean idledown = false;
        ConcurrentLinkedQueue<Future> futures = new ConcurrentLinkedQueue();
        
	public int publish(int index, Task task) {
		KilimThreadPoolExecutor executorService = executorService_.get(index);
                if (idledown) synchronized (this) {
                    executorService.count.incrementAndGet(); }
                else
                    executorService.count.incrementAndGet();
		Future future = executorService.submit(task);
                futures.add(future);
		queueStats_.get(index).record(executorService.getQueueSize());
		return index;
	}

	public String getQueueStats() {
		String statsStr = "";
		for (int i = 0; i < queueStats_.size(); ++i) {
			statsStr += queueStats_.get(i).dumpStatistics(
					poolName_ + ":QUEUE-SZ-" + i);
		}
		return statsStr;
	}

        
        /*
        
        wait till there are no pending timers
        no running tasks
        no tasks waiting to be run
        
        */
	public boolean idledown(TimerService ts,int delay) {
            while (! Thread.interrupted()) {
                if (sum(ts))
                    return true;
                try { Thread.sleep(delay); } catch (InterruptedException ex) { break; }
            }
            return false;
	}

        private boolean sum(TimerService ts) {
            for (Future fut; (fut=futures.peek()) != null && fut.isDone();) futures.poll();
            return futures.isEmpty() && ts.isEmptyLazy();
        }
	public boolean idledown2(TimerService ts,int delay) {
            while (! Thread.interrupted()) {
                if (sum(ts)) {
                    idledown = true;
                    synchronized (this) {
                        if (sum(ts))
                            return (idledown=false) || true;
                    }
                    idledown = false;
                }
                try { Thread.sleep(delay); } catch (InterruptedException ex) { break; }
            }
            return idledown = false;
	}
        private boolean sum2(TimerService ts) {
            int sum = 0;
            for (KilimThreadPoolExecutor ke : executorService_)
                sum += ke.count.get();
            System.out.format("sum: %d, %b\n",sum, ts.isEmptyLazy());
            return sum==0 && ts.isEmptyLazy();
        }
        
        public void shutdown() {
		for (ExecutorService executorService : executorService_) {
			executorService.shutdown();
		}
	}
}

class KilimThreadPoolExecutor extends ThreadPoolExecutor {
	int id = 0;
	private TimerService timerService;
	private BlockingQueue<Runnable> queue;
        AtomicInteger count = new AtomicInteger();

	KilimThreadPoolExecutor(int id, int nThreads,
			BlockingQueue<Runnable> queue, ThreadFactory tFactory,
			TimerService timerService) {
		super(nThreads, nThreads, Integer.MAX_VALUE, TimeUnit.MILLISECONDS,
				queue, tFactory);
		this.id = id;
		this.queue = queue;
		this.timerService = timerService;
	}

	protected void afterExecute(Runnable r, Throwable th) {
		super.afterExecute(r, th);
		timerService.trigger(this);
                int cnt = count.decrementAndGet();
                
	}

	protected int getQueueSize() {
		return super.getQueue().size();
	}

}
