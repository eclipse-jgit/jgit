/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.util.io;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Triggers an interrupt on the calling thread if it doesn't complete a block.
 * <p>
 * Classes can use this to trip an alarm interrupting the calling thread if it
 * doesn't complete a block within the specified timeout. Typical calling
 * pattern is:
 *
 * <pre>
 * private InterruptTimer myTimer = ...;
 * void foo() {
 *   try {
 *     myTimer.begin(timeout);
 *     // work
 *   } finally {
 *     myTimer.end();
 *   }
 * }
 * </pre>
 * <p>
 * An InterruptTimer is not recursive. To implement recursive timers,
 * independent InterruptTimer instances are required. A single InterruptTimer
 * may be shared between objects which won't recursively call each other.
 * <p>
 * Each InterruptTimer spawns one background thread to sleep the specified time
 * and interrupt the thread which called {@link #begin(int)}. It is up to the
 * caller to ensure that the operations within the work block between the
 * matched begin and end calls tests the interrupt flag (most IO operations do).
 * <p>
 * To terminate the background thread, use {@link #terminate()}. If the
 * application fails to terminate the thread, it will (eventually) terminate
 * itself when the InterruptTimer instance is garbage collected.
 *
 * @see TimeoutInputStream
 */
public final class InterruptTimer {
	private final AlarmState state;

	private final AlarmThread thread;

	final AutoKiller autoKiller;

	/** Create a new timer with a default thread name. */
	public InterruptTimer() {
		this("JGit-InterruptTimer");
	}

	/**
	 * Create a new timer to signal on interrupt on the caller.
	 * <p>
	 * The timer thread is created in the calling thread's ThreadGroup.
	 *
	 * @param threadName
	 *            name of the timer thread.
	 */
	public InterruptTimer(final String threadName) {
		state = new AlarmState();
		autoKiller = new AutoKiller(state);
		thread = new AlarmThread(threadName, state);
		thread.start();
	}

	/**
	 * Arm the interrupt timer before entering a blocking operation.
	 *
	 * @param timeout
	 *            number of milliseconds before the interrupt should trigger.
	 *            Must be > 0.
	 */
	public void begin(final int timeout) {
		if (timeout <= 0)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidTimeout, Integer.valueOf(timeout)));
		Thread.interrupted();
		state.begin(timeout);
	}

	/** Disable the interrupt timer, as the operation is complete. */
	public void end() {
		state.end();
	}

	/** Shutdown the timer thread, and wait for it to terminate. */
	public void terminate() {
		state.terminate();
		try {
			thread.join();
		} catch (InterruptedException e) {
			//
		}
	}

	static final class AlarmThread extends Thread {
		AlarmThread(final String name, final AlarmState q) {
			super(q);
			setName(name);
			setDaemon(true);
		}
	}

	// The trick here is, the AlarmThread does not have a reference to the
	// AutoKiller instance, only the InterruptTimer itself does. Thus when
	// the InterruptTimer is GC'd, the AutoKiller is also unreachable and
	// can be GC'd. When it gets finalized, it tells the AlarmThread to
	// terminate, triggering the thread to exit gracefully.
	//
	private static final class AutoKiller {
		private final AlarmState state;

		AutoKiller(final AlarmState s) {
			state = s;
		}

		@Override
		protected void finalize() throws Throwable {
			state.terminate();
		}
	}

	static final class AlarmState implements Runnable {
		private Thread callingThread;

		private long deadline;

		private boolean terminated;

		AlarmState() {
			callingThread = Thread.currentThread();
		}

		public synchronized void run() {
			while (!terminated && callingThread.isAlive()) {
				try {
					if (0 < deadline) {
						final long delay = deadline - now();
						if (delay <= 0) {
							deadline = 0;
							callingThread.interrupt();
						} else {
							wait(delay);
						}
					} else {
						wait(1000);
					}
				} catch (InterruptedException e) {
					// Treat an interrupt as notice to examine state.
				}
			}
		}

		synchronized void begin(final int timeout) {
			if (terminated)
				throw new IllegalStateException(JGitText.get().timerAlreadyTerminated);
			callingThread = Thread.currentThread();
			deadline = now() + timeout;
			notifyAll();
		}

		synchronized void end() {
			if (0 == deadline)
				Thread.interrupted();
			else
				deadline = 0;
			notifyAll();
		}

		synchronized void terminate() {
			if (!terminated) {
				deadline = 0;
				terminated = true;
				notifyAll();
			}
		}

		private static long now() {
			return System.currentTimeMillis();
		}
	}
}
