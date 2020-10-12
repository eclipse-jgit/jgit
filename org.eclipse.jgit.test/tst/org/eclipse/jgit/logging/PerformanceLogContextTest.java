package org.eclipse.jgit.logging;

import static org.junit.Assert.assertEquals;
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
	public void testAddEventsTwoThreads() {
		TestRunnable thread1 = new TestRunnable("record1", 1);
		TestRunnable thread2 = new TestRunnable("record2", 2);

		new Thread(thread1).start();
		new Thread(thread2).start();
	}

	private static class TestRunnable implements Runnable {
		private String name;
		private long durationMs;

		public TestRunnable(String name, long durationMs) {
			this.name = name;
			this.durationMs = durationMs;
		}

		@Override
		public void run() {
			PerformanceLogRecord record = new PerformanceLogRecord(name,
					durationMs);
			PerformanceLogContext.getInstance().addEvent(record);
			assertEquals(1, PerformanceLogContext.getInstance()
					.getEventRecords().size());
		}
	}
}
