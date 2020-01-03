/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit.time;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.util.time.MonotonicClock;
import org.eclipse.jgit.util.time.ProposedTimestamp;

/**
 * Fake {@link org.eclipse.jgit.util.time.MonotonicClock} for testing code that
 * uses Clock.
 *
 * @since 4.6
 */
public class MonotonicFakeClock implements MonotonicClock {
	private long now = TimeUnit.SECONDS.toMicros(42);

	/**
	 * Advance the time returned by future calls to {@link #propose()}.
	 *
	 * @param add
	 *            amount of time to add; must be {@code > 0}.
	 * @param unit
	 *            unit of {@code add}.
	 */
	public void tick(long add, TimeUnit unit) {
		if (add <= 0) {
			throw new IllegalArgumentException();
		}
		now += unit.toMillis(add);
	}

	/** {@inheritDoc} */
	@Override
	public ProposedTimestamp propose() {
		long t = now++;
		return new ProposedTimestamp() {
			@Override
			public long read(TimeUnit unit) {
				return unit.convert(t, TimeUnit.MILLISECONDS);
			}

			@Override
			public void blockUntil(Duration maxWait) {
				// Nothing to do, since fake time does not go backwards.
			}
		};
	}
}
