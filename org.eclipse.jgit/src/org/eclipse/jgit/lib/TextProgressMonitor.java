/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

/**
 * A simple progress reporter printing on stderr
 */
public class TextProgressMonitor implements ProgressMonitor {
	private boolean output;

	private long taskBeganAt;

	private String msg;

	private int lastWorked;

	private int totalWork;

	/** Initialize a new progress monitor. */
	public TextProgressMonitor() {
		taskBeganAt = System.currentTimeMillis();
	}

	public void start(final int totalTasks) {
		// Ignore the number of tasks.
		taskBeganAt = System.currentTimeMillis();
	}

	public void beginTask(final String title, final int total) {
		endTask();
		msg = title;
		lastWorked = 0;
		totalWork = total;
	}

	public void update(final int completed) {
		if (msg == null)
			return;

		final int cmp = lastWorked + completed;
		if (!output && System.currentTimeMillis() - taskBeganAt < 500)
			return;
		if (totalWork == UNKNOWN) {
			display(cmp);
			System.err.flush();
		} else {
			if ((cmp * 100 / totalWork) != (lastWorked * 100) / totalWork) {
				display(cmp);
				System.err.flush();
			}
		}
		lastWorked = cmp;
		output = true;
	}

	private void display(final int cmp) {
		final StringBuilder m = new StringBuilder();
		m.append('\r');
		m.append(msg);
		m.append(": ");
		while (m.length() < 25)
			m.append(' ');

		if (totalWork == UNKNOWN) {
			m.append(cmp);
		} else {
			final String twstr = String.valueOf(totalWork);
			String cmpstr = String.valueOf(cmp);
			while (cmpstr.length() < twstr.length())
				cmpstr = " " + cmpstr;
			final int pcnt = (cmp * 100 / totalWork);
			if (pcnt < 100)
				m.append(' ');
			if (pcnt < 10)
				m.append(' ');
			m.append(pcnt);
			m.append("% (");
			m.append(cmpstr);
			m.append("/");
			m.append(twstr);
			m.append(")");
		}

		System.err.print(m);
	}

	public boolean isCancelled() {
		return false;
	}

	public void endTask() {
		if (output) {
			if (totalWork != UNKNOWN)
				display(totalWork);
			System.err.println();
		}
		output = false;
		msg = null;
	}
}
