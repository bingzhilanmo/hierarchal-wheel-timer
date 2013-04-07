package game.test.timer;

import game.util.timer.HierarchicalWheelTimer;
import game.util.timer.Timeout;
import game.util.timer.Timer;
import game.util.timer.TimerTask;

public class HierarchicalWheelTimerCancelTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Timer timer = new HierarchicalWheelTimer(1, 4, 4);
		System.out.println("submit job!");
		final long startTime = System.currentTimeMillis();
		final int delay1 = 4;
		timer.newTimeout(new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				long currentTime = System.currentTimeMillis();
				System.out.println("delay " + delay1 + " seconds,expect=" + (currentTime - startTime) / 1000 + "s");
			}
		}, delay1);
		final int delay2 = 16;
		Timeout scheduleTimeout = timer.newTimeout(new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				long currentTime = System.currentTimeMillis();
				System.out.println("delay " + delay2 + " seconds,expect=" + (currentTime - startTime) / 1000 + "s");
			}
		}, delay2);
		final int delay3 = 36;
		Timeout scheduleTimeout36 = timer.newTimeout(new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				long currentTime = System.currentTimeMillis();
				System.out.println("delay " + delay3 + " seconds,expect=" + (currentTime - startTime) / 1000 + "s");
			}
		}, delay3);
		final int delay4 = 40;
		timer.newTimeout(new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				long currentTime = System.currentTimeMillis();
				System.out.println("delay " + delay4 + " seconds,expect=" + (currentTime - startTime) / 1000 + "s");
			}
		}, delay4);
		final int delay5 = 64;
		Timeout scheduleTimeout64 = timer.newTimeout(new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				long currentTime = System.currentTimeMillis();
				System.out.println("delay " + delay5 + " seconds,expect=" + (currentTime - startTime) / 1000 + "s");
			}
		}, delay5);
		try {
			Thread.sleep(8000);
			System.out.println("cancel job!,delay=" + delay2 + "," + delay3 + "," + delay5);
			scheduleTimeout.cancel();
			scheduleTimeout36.cancel();
			scheduleTimeout64.cancel();
			Thread.sleep(60000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("stop the timer");
		timer.stop();
	}

}
