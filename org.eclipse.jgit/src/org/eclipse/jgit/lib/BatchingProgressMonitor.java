/*
 * Copyright (C) 2008-2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.internal.WorkQueue;
import org.eclipse.jgit.util.SystemReader;

/**
 * ProgressMonitor that batches update events.
 */
public abstract class BatchingProgressMonitor implements ProgressMonitor {
	private static boolean performanceTrace = SystemReader.getInstance()
			.isPerformanceTraceEnabled();

	private long delayStartTime;

	private TimeUnit delayStartUnit = TimeUnit.MILLISECONDS;

	private Task task;

	private Boolean showDuration;

	/**
	 * Set an optional delay before the first output.
	 *
	 * @param time
	 *            how long to wait before output. If 0 output begins on the
	 *            first {@link #update(int)} call.
	 * @param unit
	 *            time unit of {@code time}.
	 */
	public void setDelayStart(long time, TimeUnit unit) {
		delayStartTime = time;
		delayStartUnit = unit;
	}

	@Override
	public void start(int totalTasks) {
		// Ignore the number of tasks.
	}

	@Override
	public void beginTask(String title, int work) {
		endTask();
		task = new Task(title, work);
		if (delayStartTime != 0)
			task.delay(delayStartTime, delayStartUnit);
	}

	@Override
	public void update(int completed) {
		if (task != null)
			task.update(this, completed);
	}

	@Override
	public void endTask() {
		if (task != null) {
			task.end(this);
			task = null;
		}
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public void showDuration(boolean enabled) {
		showDuration = Boolean.valueOf(enabled);
	}

	/**
	 * Update the progress monitor if the total work isn't known,
	 *
	 * @param taskName
	 *            name of the task.
	 * @param workCurr
	 *            number of units already completed.
	 * @param duration
	 *            how long this task runs
	 * @since 6.5
	 */
	protected abstract void onUpdate(String taskName, int workCurr,
			Duration duration);

	/**
	 * Finish the progress monitor when the total wasn't known in advance.
	 *
	 * @param taskName
	 *            name of the task.
	 * @param workCurr
	 *            total number of units processed.
	 * @param duration
	 *            how long this task runs
	 * @since 6.5
	 */
	protected abstract void onEndTask(String taskName, int workCurr,
			Duration duration);

	/**
	 * Update the progress monitor when the total is known in advance.
	 *
	 * @param taskName
	 *            name of the task.
	 * @param workCurr
	 *            number of units already completed.
	 * @param workTotal
	 *            estimated number of units to process.
	 * @param percentDone
	 *            {@code workCurr * 100 / workTotal}.
	 * @param duration
	 *            how long this task runs
	 * @since 6.5
	 */
	protected abstract void onUpdate(String taskName, int workCurr,
			int workTotal, int percentDone, Duration duration);

	/**
	 * Finish the progress monitor when the total is known in advance.
	 *
	 * @param taskName
	 *            name of the task.
	 * @param workCurr
	 *            total number of units processed.
	 * @param workTotal
	 *            estimated number of units to process.
	 * @param percentDone
	 *            {@code workCurr * 100 / workTotal}.
	 * @param duration
	 *            duration of the task
	 * @since 6.5
	 */
	protected abstract void onEndTask(String taskName, int workCurr,
			int workTotal, int percentDone, Duration duration);

	private boolean showDuration() {
		return showDuration != null ? showDuration.booleanValue()
				: performanceTrace;
	}

	/**
	 * Append formatted duration if system property or environment variable
	 * GIT_TRACE_PERFORMANCE is set to "true". If both are defined the system
	 * property takes precedence.
	 *
	 * @param s
	 *            StringBuilder to append the formatted duration to
	 * @param duration
	 *            duration to format
	 * @since 6.5
	 */
	@SuppressWarnings({ "boxing", "nls" })
	protected void appendDuration(StringBuilder s, Duration duration) {
		if (!showDuration()) {
			return;
		}
		long hours = duration.toHours();
		int minutes = duration.toMinutesPart();
		int seconds = duration.toSecondsPart();
		s.append(" [");
		if (hours > 0) {
			s.append(hours).append(':');
			s.append(String.format("%02d", minutes)).append(':');
			s.append(String.format("%02d", seconds));
		} else if (minutes > 0) {
			s.append(minutes).append(':');
			s.append(String.format("%02d", seconds));
		} else {
			s.append(seconds);
		}
		s.append('.').append(String.format("%03d", duration.toMillisPart()));
		if (hours > 0) {
			s.append('h');
		} else if (minutes > 0) {
			s.append('m');
		} else {
			s.append('s');
		}
		s.append(']');
	}

	private static class Task implements Runnable {
		/** Title of the current task. */
		private final String taskName;

		/** Number of work units, or {@link ProgressMonitor#UNKNOWN}. */
		private final int totalWork;

		/** True when timer expires and output should occur on next update. */
		private volatile boolean display;

		/** Scheduled timer, supporting cancellation if task ends early. */
		private Future<?> timerFuture;

		/** True if the task has displayed anything. */
		private boolean output;

		/** Number of work units already completed. */
		private int lastWork;

		/** Percentage of {@link #totalWork} that is done. */
		private int lastPercent;

		private final Instant startTime;

		Task(String taskName, int totalWork) {
			this.taskName = taskName;
			this.totalWork = totalWork;
			this.display = true;
			this.startTime = Instant.now();
		}

		void delay(long time, TimeUnit unit) {
			display = false;
			timerFuture = WorkQueue.getExecutor().schedule(this, time, unit);
		}

		@Override
		public void run() {
			display = true;
		}

		void update(BatchingProgressMonitor pm, int completed) {
			lastWork += completed;

			if (totalWork == UNKNOWN) {
				// Only display once per second, as the alarm fires.
				if (display) {
					pm.onUpdate(taskName, lastWork, elapsedTime());
					output = true;
					restartTimer();
				}
			} else {
				// Display once per second or when 1% is done.
				int currPercent = Math.round(lastWork * 100F / totalWork);
				if (display) {
					pm.onUpdate(taskName, lastWork, totalWork, currPercent,
							elapsedTime());
					output = true;
					restartTimer();
					lastPercent = currPercent;
				} else if (currPercent != lastPercent) {
					pm.onUpdate(taskName, lastWork, totalWork, currPercent,
							elapsedTime());
					output = true;
					lastPercent = currPercent;
				}
			}
		}

		private void restartTimer() {
			display = false;
			timerFuture = WorkQueue.getExecutor().schedule(this, 1,
					TimeUnit.SECONDS);
		}

		void end(BatchingProgressMonitor pm) {
			if (output) {
				if (totalWork == UNKNOWN) {
					pm.onEndTask(taskName, lastWork, elapsedTime());
				} else {
					int currPercent = Math.round(lastWork * 100F / totalWork);
					pm.onEndTask(taskName, lastWork, totalWork, currPercent, elapsedTime());
				}
			}
			if (timerFuture != null)
				timerFuture.cancel(false /* no interrupt */);
		}

		private Duration elapsedTime() {
			return Duration.between(startTime, Instant.now());
		}
	}
}
