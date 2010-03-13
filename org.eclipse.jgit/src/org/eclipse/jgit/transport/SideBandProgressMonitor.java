/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;

/** Write progress messages out to the sideband channel. */
class SideBandProgressMonitor implements ProgressMonitor {
	private PrintWriter out;

	private boolean output;

	private long taskBeganAt;

	private long lastOutput;

	private String msg;

	private int lastWorked;

	private int totalWork;

	SideBandProgressMonitor(final OutputStream os) {
		out = new PrintWriter(new OutputStreamWriter(os, Constants.CHARSET));
	}

	public void start(final int totalTasks) {
		// Ignore the number of tasks.
		taskBeganAt = System.currentTimeMillis();
		lastOutput = taskBeganAt;
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
		final long now = System.currentTimeMillis();
		if (!output && now - taskBeganAt < 500)
			return;
		if (totalWork == UNKNOWN) {
			if (now - lastOutput >= 500) {
				display(cmp, null);
				lastOutput = now;
			}
		} else {
			if ((cmp * 100 / totalWork) != (lastWorked * 100) / totalWork
					|| now - lastOutput >= 500) {
				display(cmp, null);
				lastOutput = now;
			}
		}
		lastWorked = cmp;
		output = true;
	}

	private void display(final int cmp, final String eol) {
		final StringBuilder m = new StringBuilder();
		m.append(msg);
		m.append(": ");

		if (totalWork == UNKNOWN) {
			m.append(cmp);
		} else {
			final int pcnt = (cmp * 100 / totalWork);
			if (pcnt < 100)
				m.append(' ');
			if (pcnt < 10)
				m.append(' ');
			m.append(pcnt);
			m.append("% (");
			m.append(cmp);
			m.append("/");
			m.append(totalWork);
			m.append(")");
		}
		if (eol != null)
			m.append(eol);
		else
			m.append("   \r");
		out.print(m);
		out.flush();
	}

	public boolean isCancelled() {
		return false;
	}

	public void endTask() {
		if (output) {
			if (totalWork == UNKNOWN)
				display(lastWorked, ", done\n");
			else
				display(totalWork, "\n");
		}
		output = false;
		msg = null;
	}
}
