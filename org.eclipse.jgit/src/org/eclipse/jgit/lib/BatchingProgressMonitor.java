/*
 * Copyright (C) 2008-2011, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.lib;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.internal.WorkQueue;

/** ProgressMonitor that batches update events. */
public abstract class BatchingProgressMonitor implements ProgressMonitor {
	private long delayStartTime;

	private TimeUnit delayStartUnit = TimeUnit.MILLISECONDS;

	private Task task;

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

	/**
	 * Update the progress monitor if the total work isn't known,
	 *
	 * @param taskName
	 *            name of the task.
	 * @param workCurr
	 *            number of units already completed.
	 */
	protected abstract void onUpdate(String taskName, int workCurr);

	/**
	 * Finish the progress monitor when the total wasn't known in advance.
	 *
	 * @param taskName
	 *            name of the task.
	 * @param workCurr
	 *            total number of units processed.
	 */
	protected abstract void onEndTask(String taskName, int workCurr);

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
	 */
	protected abstract void onUpdate(String taskName, int workCurr,
			int workTotal, int percentDone);

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
	 */
	protected abstract void onEndTask(String taskName, int workCurr,
			int workTotal, int percentDone);

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

		Task(String taskName, int totalWork) {
			this.taskName = taskName;
			this.totalWork = totalWork;
			this.display = true;
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
					pm.onUpdate(taskName, lastWork);
					output = true;
					restartTimer();
				}
			} else {
				// Display once per second or when 1% is done.
				int currPercent = lastWork * 100 / totalWork;
				if (display) {
					pm.onUpdate(taskName, lastWork, totalWork, currPercent);
					output = true;
					restartTimer();
					lastPercent = currPercent;
				} else if (currPercent != lastPercent) {
					pm.onUpdate(taskName, lastWork, totalWork, currPercent);
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
					pm.onEndTask(taskName, lastWork);
				} else {
					int pDone = lastWork * 100 / totalWork;
					pm.onEndTask(taskName, lastWork, totalWork, pDone);
				}
			}
			if (timerFuture != null)
				timerFuture.cancel(false /* no interrupt */);
		}
	}
}
