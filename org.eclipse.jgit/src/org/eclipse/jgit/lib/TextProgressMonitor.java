/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Duration;

/**
 * A simple progress reporter printing on a stream.
 */
public class TextProgressMonitor extends BatchingProgressMonitor {
	private final Writer out;

	private boolean write;

	/**
	 * Initialize a new progress monitor.
	 */
	public TextProgressMonitor() {
		this(new PrintWriter(new OutputStreamWriter(System.err, UTF_8)));
	}

	/**
	 * Initialize a new progress monitor.
	 *
	 * @param out
	 *            the stream to receive messages on.
	 */
	public TextProgressMonitor(Writer out) {
		this.out = out;
		this.write = true;
	}

	@Override
	protected void onUpdate(String taskName, int workCurr, Duration duration) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, workCurr, duration);
		send(s);
	}

	@Override
	protected void onEndTask(String taskName, int workCurr, Duration duration) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, workCurr, duration);
		s.append("\n"); //$NON-NLS-1$
		send(s);
	}

	private void format(StringBuilder s, String taskName, int workCurr,
			Duration duration) {
		s.append("\r"); //$NON-NLS-1$
		s.append(taskName);
		s.append(": "); //$NON-NLS-1$
		while (s.length() < 25)
			s.append(' ');
		s.append(workCurr);
		appendDuration(s, duration);
	}

	@Override
	protected void onUpdate(String taskName, int cmp, int totalWork, int pcnt,
			Duration duration) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, cmp, totalWork, pcnt, duration);
		send(s);
	}

	@Override
	protected void onEndTask(String taskName, int cmp, int totalWork, int pcnt,
			Duration duration) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, cmp, totalWork, pcnt, duration);
		s.append("\n"); //$NON-NLS-1$
		send(s);
	}

	private void format(StringBuilder s, String taskName, int cmp,
			int totalWork, int pcnt, Duration duration) {
		s.append("\r"); //$NON-NLS-1$
		s.append(taskName);
		s.append(": "); //$NON-NLS-1$
		while (s.length() < 25)
			s.append(' ');

		String endStr = String.valueOf(totalWork);
		String curStr = String.valueOf(cmp);
		while (curStr.length() < endStr.length())
			curStr = " " + curStr; //$NON-NLS-1$
		if (pcnt < 100)
			s.append(' ');
		if (pcnt < 10)
			s.append(' ');
		s.append(pcnt);
		s.append("% ("); //$NON-NLS-1$
		s.append(curStr);
		s.append('/');
		s.append(endStr);
		s.append(')');
		appendDuration(s, duration);
	}

	private void send(StringBuilder s) {
		if (write) {
			try {
				out.write(s.toString());
				out.flush();
			} catch (IOException err) {
				write = false;
			}
		}
	}
}
