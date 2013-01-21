package game.test.timer;

import game.util.timer.HashWheelTimer;
import game.util.timer.Timeout;
import game.util.timer.Timer;
import game.util.timer.TimerTask;

public class HashWheelTimerTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Timer timer = new HashWheelTimer();
		System.out.println("submit job!");
		int[] times = new int[] { 15, 5, 16, 17, 24, 29, 32, 36, 37, 39, 40, 50, 52, 64, 66, 67, 68, 69, 70, 72, 80, 124, 96, 128, 129, 132, 160 };
		for (final int perTime : times) {
			timer.newTimeout(new TimerTask() {
				@Override
				public void run(Timeout timeout) throws Exception {
					System.out.printf("delay %s seconds\n", perTime);
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
