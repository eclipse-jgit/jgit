/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.io;

import static org.eclipse.jgit.internal.storage.io.CancellableDigestOutputStream.BYTES_TO_WRITE_BEFORE_CANCEL_CHECK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.InterruptedIOException;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.junit.Test;

public class CancellableDigestOutputStreamTest {
	private static class CancelledTestMonitor implements ProgressMonitor {

		private boolean cancelled = false;

		public void setCancelled(boolean cancelled) {
			this.cancelled = cancelled;
		}

		@Override
		public void start(int totalTasks) {
			// not implemented
		}

		@Override
		public void beginTask(String title, int totalWork) {
			// not implemented
		}

		@Override
		public void update(int completed) {
			// not implemented
		}

		@Override
		public void endTask() {
			// not implemented
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}
	}

	@Test
	public void testCancelInProcess() throws Exception {
		CancelledTestMonitor m = new CancelledTestMonitor();
		CancellableDigestOutputStream out = new CancellableDigestOutputStream(m,
				NullOutputStream.INSTANCE);

		byte[] KB = new byte[1024];
		int triggerCancelWriteCnt = BYTES_TO_WRITE_BEFORE_CANCEL_CHECK
				/ KB.length;
		for (int i = 0; i < triggerCancelWriteCnt + 1; i++) {
			out.write(KB);
		}
		assertTrue(out.length() > BYTES_TO_WRITE_BEFORE_CANCEL_CHECK);
		m.setCancelled(true);

		for (int i = 0; i < triggerCancelWriteCnt - 1; i++) {
			out.write(KB);
		}

		long lastLength = out.length();
		assertThrows(InterruptedIOException.class, () -> {
			out.write(1);
		});
		assertEquals(lastLength, out.length());

		assertThrows(InterruptedIOException.class, () -> {
			out.write(new byte[1]);
		});
		assertEquals(lastLength, out.length());
	}

	@Test
	public void testTriggerCheckAfterSingleBytes() throws Exception {
		CancelledTestMonitor m = new CancelledTestMonitor();
		CancellableDigestOutputStream out = new CancellableDigestOutputStream(m,
				NullOutputStream.INSTANCE);

		byte[] bytes = new byte[BYTES_TO_WRITE_BEFORE_CANCEL_CHECK + 1];
		m.setCancelled(true);

		assertThrows(InterruptedIOException.class, () -> {
			out.write(bytes);
		});
		assertEquals(BYTES_TO_WRITE_BEFORE_CANCEL_CHECK, out.length());
	}
}
