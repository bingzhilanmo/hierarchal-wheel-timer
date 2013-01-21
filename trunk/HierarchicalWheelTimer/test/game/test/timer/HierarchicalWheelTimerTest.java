package game.test.timer;

import game.util.timer.HierarchicalWheelTimer;
import game.util.timer.Timeout;
import game.util.timer.Timer;
import game.util.timer.TimerTask;

public class HierarchicalWheelTimerTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Timer timer = new HierarchicalWheelTimer(1, 4, 4);
		System.out.println("submit job!");
		final long startTime = System.currentTimeMillis();
		int[] times = new int[148];
		for (int i = 0; i < 148; i++) {
			times[i] = i & 127;
		}
		for (final int perTime : times) {
			timer.newTimeout(new TimerTask() {
				@Override
				public void run(Timeout timeout) throws Exception {
					long currentTime = System.currentTimeMillis();
					System.out.printf("delay %s seconds,expect=%ss\n", perTime, (currentTime - startTime) / 1000);
				}
			}, perTime);
		}
		try {
			Thread.sleep(180000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("stop the timer");
		timer.stop();
	}

}
