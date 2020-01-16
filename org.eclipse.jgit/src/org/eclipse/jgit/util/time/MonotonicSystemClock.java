/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.time;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link org.eclipse.jgit.util.time.MonotonicClock} based on
 * {@code System.currentTimeMillis}.
 *
 * @since 4.6
 */
public class MonotonicSystemClock implements MonotonicClock {
	private static final AtomicLong before = new AtomicLong();

	private static long nowMicros() {
		long now = MILLISECONDS.toMicros(System.currentTimeMillis());
		for (;;) {
			long o = before.get();
			long n = Math.max(o + 1, now);
			if (before.compareAndSet(o, n)) {
				return n;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public ProposedTimestamp propose() {
		final long u = nowMicros();
		return new ProposedTimestamp() {
			@Override
			public long read(TimeUnit unit) {
				return unit.convert(u, MICROSECONDS);
			}

			@Override
			public void blockUntil(Duration maxWait) {
				// Assume system clock never goes backwards.
			}
		};
	}
}
