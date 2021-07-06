/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import static org.junit.Assert.assertThrows;

import java.io.InterruptedIOException;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.junit.Test;

public class CancellableDigestOutputStreamTest {
	private static class CancelledTestMonitor implements ProgressMonitor {

		private boolean cancelled = false;

		public void setCancelled(boolean cancelled) {
			this.cancelled = cancelled;
		}

		@Override
		public void start(int totalTasks) {
		}

		@Override
		public void beginTask(String title, int totalWork) {
		}

		@Override
		public void update(int completed) {
		}

		@Override
		public void endTask() {
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}
	}

	@Test
	public void testCancelStream() throws Exception {
		CancelledTestMonitor m = new CancelledTestMonitor();
		CancellableDigestOutputStream out = new CancellableDigestOutputStream(m,
				NullOutputStream.INSTANCE);

		byte[] KB = new byte[1024];
		m.setCancelled(true);
		for (int i = 0; i < 128; i++) {
			out.write(KB);
		}

		assertThrows(InterruptedIOException.class, () -> {
			out.write(KB);
		});
	}
}
