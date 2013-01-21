package game.util.timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * copy from netty project {@link org.jboss.netty.util.HashedWheelTimer}
 * 
 * @author hexuhui
 * 
 */
public class HashWheelTimer implements Timer {

	final int mask;

	final Set<HashedWheelTimeout>[] wheel;

	final long tickDuration;

	private final Worker worker = new Worker();

	final Thread workerThread;
	final AtomicInteger workerState = new AtomicInteger(); // 0 - init, 1 - started, 2 - shut down

	private final long roundDuration;

	final ReadWriteLock lock = new ReentrantReadWriteLock();
	volatile int wheelCursor;

	final ReusableIterator<HashedWheelTimeout>[] iterators;

	public HashWheelTimer() {
		this(100, TimeUnit.MILLISECONDS);
	}

	public HashWheelTimer(long tickDuration, TimeUnit unit) {
		this(tickDuration, unit, 512);
	}

	public HashWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
		if (unit == null) {
			throw new NullPointerException("unit");
		}
		if (tickDuration <= 0) {
			throw new IllegalArgumentException("tickDuration must be greater than 0: " + tickDuration);
		}
		if (ticksPerWheel <= 0) {
			throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
		}
		wheel = createWheel(ticksPerWheel);
		iterators = createIterators(wheel);
		mask = wheel.length - 1;

		// Convert tickDuration to milliseconds.
		this.tickDuration = tickDuration = unit.toMillis(tickDuration);

		// Prevent overflow.
		if (tickDuration == Long.MAX_VALUE || tickDuration >= Long.MAX_VALUE / wheel.length) {
			throw new IllegalArgumentException("tickDuration is too long: " + tickDuration + ' ' + unit);
		}

		roundDuration = tickDuration * wheel.length;

		workerThread = new Thread(worker, getClass().getSimpleName());
	}

	@SuppressWarnings("unchecked")
	private static Set<HashedWheelTimeout>[] createWheel(int ticksPerWheel) {
		if (ticksPerWheel <= 0) {
			throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
		}
		if (ticksPerWheel > 1073741824) {
			throw new IllegalArgumentException("ticksPerWheel may not be greater than 2^30: " + ticksPerWheel);
		}

		ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);
		Set<HashedWheelTimeout>[] wheel = new Set[ticksPerWheel];
		for (int i = 0; i < wheel.length; i++) {
			wheel[i] = Collections.newSetFromMap(new ConcurrentIdentityHashMap<HashedWheelTimeout, Boolean>(16, 0.95f, 4));
		}
		return wheel;
	}

	@SuppressWarnings("unchecked")
	private static ReusableIterator<HashedWheelTimeout>[] createIterators(Set<HashedWheelTimeout>[] wheel) {
		ReusableIterator<HashedWheelTimeout>[] iterators = new ReusableIterator[wheel.length];
		for (int i = 0; i < wheel.length; i++) {
			iterators[i] = (ReusableIterator<HashedWheelTimeout>) wheel[i].iterator();
		}
		return iterators;
	}

	private static int normalizeTicksPerWheel(int ticksPerWheel) {
		int normalizedTicksPerWheel = 1;
		while (normalizedTicksPerWheel < ticksPerWheel) {
			normalizedTicksPerWheel <<= 1;
		}
		return normalizedTicksPerWheel;
	}

	public void start() {
		switch (workerState.get()) {
		case 0:
			if (workerState.compareAndSet(0, 1)) {
				workerThread.start();
			}
			break;
		case 1:
			break;
		case 2:
			throw new IllegalStateException("cannot be started once stopped");
		default:
			throw new Error();
		}
	}

	@Override
	public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
		final long currentTime = System.currentTimeMillis();

		if (task == null) {
			throw new NullPointerException("task");
		}
		if (unit == null) {
			throw new NullPointerException("unit");
		}

		start();

		delay = unit.toMillis(delay);
		HashedWheelTimeout timeout = new HashedWheelTimeout(task, currentTime + delay);
		scheduleTimeout(timeout, delay);
		return timeout;
	}

	void scheduleTimeout(HashedWheelTimeout timeout, long delay) {
		// delay must be equal to or greater than tickDuration so that the
		// worker thread never misses the timeout.
		if (delay < tickDuration) {
			delay = tickDuration;
		}

		// Prepare the required parameters to schedule the timeout object.
		final long lastRoundDelay = delay % roundDuration;
		final long lastTickDelay = delay % tickDuration;
		final long relativeIndex = lastRoundDelay / tickDuration + (lastTickDelay != 0 ? 1 : 0);

		final long remainingRounds = delay / roundDuration - (delay % roundDuration == 0 ? 1 : 0);

		// Add the timeout to the wheel.
		lock.readLock().lock();
		try {
			int stopIndex = (int) (wheelCursor + relativeIndex & mask);
			timeout.stopIndex = stopIndex;
			timeout.remainingRounds = remainingRounds;

			wheel[stopIndex].add(timeout);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public Set<Timeout> stop() {
		if (Thread.currentThread() == workerThread) {
			throw new IllegalStateException(HashWheelTimer.class.getSimpleName() + ".stop() cannot be called from " + TimerTask.class.getSimpleName());
		}

		if (workerState.getAndSet(2) != 1) {
			// workerState wasn't 1, so return an empty set
			return Collections.emptySet();
		}

		boolean interrupted = false;
		while (workerThread.isAlive()) {
			workerThread.interrupt();
			try {
				workerThread.join(100);
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}

		if (interrupted) {
			Thread.currentThread().interrupt();
		}

		Set<Timeout> unprocessedTimeouts = new HashSet<Timeout>();
		for (Set<HashedWheelTimeout> bucket : wheel) {
			unprocessedTimeouts.addAll(bucket);
			bucket.clear();
		}

		return Collections.unmodifiableSet(unprocessedTimeouts);
	}

	@Override
	public Timeout newTimeout(TimerTask task, int delay) {
		return newTimeout(task, delay, TimeUnit.SECONDS);
	}

	private final class Worker implements Runnable {

		private long startTime;
		private long elapseTime;

		Worker() {
		}

		public void run() {
			List<HashedWheelTimeout> expiredTimeouts = new ArrayList<HashedWheelTimeout>();

			startTime = System.currentTimeMillis();
			elapseTime = tickDuration;

			while (workerState.get() == 1) {
				final long deadline = waitForNextTick();
				if (deadline > 0) {
					fetchExpiredTimeouts(expiredTimeouts, deadline);
					notifyExpiredTimeouts(expiredTimeouts);
				}
			}
		}

		private void fetchExpiredTimeouts(List<HashedWheelTimeout> expiredTimeouts, long deadline) {

			// Find the expired timeouts and decrease the round counter
			// if necessary. Note that we don't send the notification
			// immediately to make sure the listeners are called without
			// an exclusive lock.
			lock.writeLock().lock();
			try {
				int newWheelCursor = wheelCursor = wheelCursor + 1 & mask;
				ReusableIterator<HashedWheelTimeout> i = iterators[newWheelCursor];
				fetchExpiredTimeouts(expiredTimeouts, i, deadline);
			} finally {
				lock.writeLock().unlock();
			}
		}

		private void fetchExpiredTimeouts(List<HashedWheelTimeout> expiredTimeouts, ReusableIterator<HashedWheelTimeout> i, long deadline) {
			List<HashedWheelTimeout> slipped = null;
			i.rewind();
			while (i.hasNext()) {
				HashedWheelTimeout timeout = i.next();
				if (timeout.remainingRounds <= 0) {
					i.remove();
					if (timeout.deadline <= deadline) {
						expiredTimeouts.add(timeout);
					} else {
						// Handle the case where the timeout is put into a wrong
						// place, usually one tick earlier. For now, just add
						// it to a temporary list - we will reschedule it in a
						// separate loop.
						if (slipped == null) {
							slipped = new ArrayList<HashedWheelTimeout>();
						}
						slipped.add(timeout);
					}
				} else {
					timeout.remainingRounds--;
				}
			}

			// Reschedule the slipped timeouts.
			if (slipped != null) {
				for (HashedWheelTimeout timeout : slipped) {
					scheduleTimeout(timeout, timeout.deadline - deadline);
				}
			}
		}

		private void notifyExpiredTimeouts(List<HashedWheelTimeout> expiredTimeouts) {
			// Notify the expired timeouts.
			for (int i = expiredTimeouts.size() - 1; i >= 0; i--) {
				expiredTimeouts.get(i).expire();
			}

			// Clean up the temporary list.
			expiredTimeouts.clear();
		}

		private long waitForNextTick() {
			long deadline = startTime + elapseTime;

			for (;;) {
				final long currentTime = System.currentTimeMillis();
				long sleepTime = elapseTime - (currentTime - startTime);

				// Check if we run on windows, as if thats the case we will need
				// to round the sleepTime as workaround for a bug that only affect
				// the JVM if it runs on windows.
				//
				// See https://github.com/netty/netty/issues/356
				// if (DetectionUtil.isWindows()) {
				// sleepTime = sleepTime / 10 * 10;
				// }

				if (sleepTime <= 0) {
					break;
				}
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					if (workerState.get() != 1) {
						return -1;
					}
				}
			}

			// Increase the tick.
			elapseTime += tickDuration;
			return deadline;
		}
	}

	private final class HashedWheelTimeout implements Timeout {

		private static final int ST_INIT = 0;
		private static final int ST_CANCELLED = 1;
		private static final int ST_EXPIRED = 2;

		private final TimerTask task;
		final long deadline;
		volatile int stopIndex;
		volatile long remainingRounds;
		private final AtomicInteger state = new AtomicInteger(ST_INIT);

		HashedWheelTimeout(TimerTask task, long deadline) {
			this.task = task;
			this.deadline = deadline;
		}

		public Timer getTimer() {
			return HashWheelTimer.this;
		}

		public TimerTask getTask() {
			return task;
		}

		public void cancel() {
			if (!state.compareAndSet(ST_INIT, ST_CANCELLED)) {
				// TODO return false
				return;
			}

			wheel[stopIndex].remove(this);
		}

		public boolean isCancelled() {
			return state.get() == ST_CANCELLED;
		}

		public boolean isExpired() {
			return state.get() != ST_INIT;
		}

		public void expire() {
			if (!state.compareAndSet(ST_INIT, ST_EXPIRED)) {
				return;
			}

			try {
				task.run(this);
			} catch (Throwable t) {
			}
		}

		@Override
		public String toString() {
			long currentTime = System.currentTimeMillis();
			long remaining = deadline - currentTime;

			StringBuilder buf = new StringBuilder(192);
			buf.append(getClass().getSimpleName());
			buf.append('(');

			buf.append("deadline: ");
			if (remaining > 0) {
				buf.append(remaining);
				buf.append(" ms later, ");
			} else if (remaining < 0) {
				buf.append(-remaining);
				buf.append(" ms ago, ");
			} else {
				buf.append("now, ");
			}

			if (isCancelled()) {
				buf.append(", cancelled");
			}

			return buf.append(')').toString();
		}
	}

}
