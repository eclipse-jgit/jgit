/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * A simple spinner connected to an {@code OutputStream}.
 * <p>
 * This is class is not thread-safe. The update method may only be used from a
 * single thread. Updates are sent only as frequently as {@link #update()} is
 * invoked by the caller, and are capped at no more than 2 times per second by
 * requiring at least 500 milliseconds between updates.
 *
 * @since 4.2
 */
public class ProgressSpinner {
	private static final long MIN_REFRESH_MILLIS = 500;
	private static final char[] STATES = new char[] { '-', '\\', '|', '/' };

	private final OutputStream out;
	private String msg;
	private int state;
	private boolean write;
	private boolean shown;
	private long nextUpdateMillis;

	/**
	 * Initialize a new spinner.
	 *
	 * @param out
	 *            where to send output to.
	 */
	public ProgressSpinner(OutputStream out) {
		this.out = out;
		this.write = true;
	}

	/**
	 * Begin a time consuming task.
	 *
	 * @param title
	 *            description of the task, suitable for human viewing.
	 * @param delay
	 *            delay to wait before displaying anything at all.
	 * @param delayUnits
	 *            unit for {@code delay}.
	 */
	public void beginTask(String title, long delay, TimeUnit delayUnits) {
		msg = title;
		state = 0;
		shown = false;

		long now = System.currentTimeMillis();
		if (delay > 0) {
			nextUpdateMillis = now + delayUnits.toMillis(delay);
		} else {
			send(now);
		}
	}

	/**
	 * Update the spinner if it is showing.
	 */
	public void update() {
		long now = System.currentTimeMillis();
		if (now >= nextUpdateMillis) {
			send(now);
			state = (state + 1) % STATES.length;
		}
	}

	private void send(long now) {
		StringBuilder buf = new StringBuilder(msg.length() + 16);
		buf.append('\r').append(msg).append("... ("); //$NON-NLS-1$
		buf.append(STATES[state]);
		buf.append(")  "); //$NON-NLS-1$
		shown = true;
		write(buf.toString());
		nextUpdateMillis = now + MIN_REFRESH_MILLIS;
	}

	/**
	 * Denote the current task completed.
	 *
	 * @param result
	 *            text to print after the task's title
	 *            {@code "$title ... $result"}.
	 */
	public void endTask(String result) {
		if (shown) {
			write('\r' + msg + "... " + result + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void write(String s) {
		if (write) {
			try {
				out.write(s.getBytes(UTF_8));
				out.flush();
			} catch (IOException e) {
				write = false;
			}
		}
	}
}
