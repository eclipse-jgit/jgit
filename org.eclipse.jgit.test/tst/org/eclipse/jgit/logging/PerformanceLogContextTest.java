package org.eclipse.jgit.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.List;

/**
 * Tests for performance log context utilities.
 */
public class PerformanceLogContextTest {

	@Test
	public void testAddEvent() {
		PerformanceLogRecord record = new PerformanceLogRecord("record", 0);
		PerformanceLogContext.getInstance().addEvent(record);

		List<PerformanceLogRecord> eventRecords = PerformanceLogContext
				.getInstance().getEventRecords();
		assertTrue(eventRecords.contains(record));
		assertEquals(1, eventRecords.size());
	}

	@Test
	public void testCleanEvents() {
		PerformanceLogRecord record1 = new PerformanceLogRecord("record1", 0);
		PerformanceLogContext.getInstance().addEvent(record1);

		PerformanceLogRecord record2 = new PerformanceLogRecord("record2", 0);
		PerformanceLogContext.getInstance().addEvent(record2);

		PerformanceLogContext.getInstance().cleanEvents();
		List<PerformanceLogRecord> eventRecords = PerformanceLogContext
				.getInstance().getEventRecords();
		assertEquals(0, eventRecords.size());
	}

	@Test
	public void testAddEventsTwoThreads() throws InterruptedException {
		TestRunnable runnable1 = new TestRunnable("record1", 1);
		TestRunnable runnable2 = new TestRunnable("record2", 2);

		Thread thread1 = new Thread(runnable1);
		Thread thread2 = new Thread(runnable2);

		thread1.start();
		thread2.start();

		thread1.join();
		thread2.join();
		assertEquals(1, runnable1.getEventRecordsCount());
		assertEquals(1, runnable2.getEventRecordsCount());
		assertFalse(runnable1.isThrown());
		assertFalse(runnable2.isThrown());
	}

	private static class TestRunnable implements Runnable {
		private String name;

		private long durationMs;

		private long eventRecordsCount;

		private boolean thrown = false;

		public TestRunnable(String name, long durationMs) {
			this.name = name;
			this.durationMs = durationMs;
		}

		public boolean isThrown() {
			return thrown;
		}

		public long getEventRecordsCount() {
			return eventRecordsCount;
		}

		@Override
		public void run() {
			PerformanceLogRecord record = new PerformanceLogRecord(name,
					durationMs);
			try {
				PerformanceLogContext.getInstance().addEvent(record);
				eventRecordsCount = PerformanceLogContext.getInstance()
						.getEventRecords().size();
				PerformanceLogContext.getInstance().cleanEvents();
			} catch (Exception e) {
				thrown = true;
			}
		}
	}
}
