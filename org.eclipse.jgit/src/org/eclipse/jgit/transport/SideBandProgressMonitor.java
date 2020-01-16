/*
 * Copyright (C) 2008-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.lib.BatchingProgressMonitor;
import org.eclipse.jgit.lib.Constants;

/** Write progress messages out to the sideband channel. */
class SideBandProgressMonitor extends BatchingProgressMonitor {
	private final OutputStream out;

	private boolean write;

	SideBandProgressMonitor(OutputStream os) {
		out = os;
		write = true;
	}

	/** {@inheritDoc} */
	@Override
	protected void onUpdate(String taskName, int workCurr) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, workCurr);
		s.append("   \r"); //$NON-NLS-1$
		send(s);
	}

	/** {@inheritDoc} */
	@Override
	protected void onEndTask(String taskName, int workCurr) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, workCurr);
		s.append(", done\n"); //$NON-NLS-1$
		send(s);
	}

	private void format(StringBuilder s, String taskName, int workCurr) {
		s.append(taskName);
		s.append(": "); //$NON-NLS-1$
		s.append(workCurr);
	}

	/** {@inheritDoc} */
	@Override
	protected void onUpdate(String taskName, int cmp, int totalWork, int pcnt) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, cmp, totalWork, pcnt);
		s.append("   \r"); //$NON-NLS-1$
		send(s);
	}

	/** {@inheritDoc} */
	@Override
	protected void onEndTask(String taskName, int cmp, int totalWork, int pcnt) {
		StringBuilder s = new StringBuilder();
		format(s, taskName, cmp, totalWork, pcnt);
		s.append("\n"); //$NON-NLS-1$
		send(s);
	}

	private void format(StringBuilder s, String taskName, int cmp,
			int totalWork, int pcnt) {
		s.append(taskName);
		s.append(": "); //$NON-NLS-1$
		if (pcnt < 100)
			s.append(' ');
		if (pcnt < 10)
			s.append(' ');
		s.append(pcnt);
		s.append("% ("); //$NON-NLS-1$
		s.append(cmp);
		s.append("/"); //$NON-NLS-1$
		s.append(totalWork);
		s.append(")"); //$NON-NLS-1$
	}

	private void send(StringBuilder s) {
		if (write) {
			try {
				out.write(Constants.encode(s.toString()));
				out.flush();
			} catch (IOException err) {
				write = false;
			}
		}
	}
}
