/*
 * Copyright (C) 2009, Mark Struberg <struberg@yahoo.de>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Selects commits based upon the commit time field.
 */
public abstract class CommitTimeRevFilter extends RevFilter {
	/**
	 * Create a new filter to select commits before a given date/time.
	 *
	 * @param ts
	 *            the point in time to cut on.
	 * @return a new filter to select commits on or before <code>ts</code>.
	 *
	 * @deprecated Use {@link #before(Instant)} instead.
	 */
	@Deprecated(since="7.2")
	public static final RevFilter before(Date ts) {
		return before(ts.toInstant());
	}

	/**
	 * Create a new filter to select commits before a given date/time.
	 *
	 * @param ts
	 *            the point in time to cut on.
	 * @return a new filter to select commits on or before <code>ts</code>.
	 * @since 7.2
	 */
	public static RevFilter before(Instant ts) {
		return new Before(ts);
	}

	/**
	 * Create a new filter to select commits before a given date/time.
	 *
	 * @param ts
	 *            the point in time to cut on, in milliseconds
	 * @return a new filter to select commits on or before <code>ts</code>.
	 */
	public static final RevFilter before(long ts) {
		return new Before(Instant.ofEpochMilli(ts));
	}

	/**
	 * Create a new filter to select commits after a given date/time.
	 *
	 * @param ts
	 *            the point in time to cut on.
	 * @return a new filter to select commits on or after <code>ts</code>.
	 *
	 * @deprecated Use {@link #after(Instant)} instead.
	 */
	@Deprecated(since="7.2")
	public static final RevFilter after(Date ts) {
		return after(ts.toInstant());
	}

	/**
	 * Create a new filter to select commits after a given date/time.
	 *
	 * @param ts
	 *            the point in time to cut on.
	 * @return a new filter to select commits on or after <code>ts</code>.
	 * @since 7.2
	 */
	public static RevFilter after(Instant ts) {
		return new After(ts);
	}

	/**
	 * Create a new filter to select commits after a given date/time.
	 *
	 * @param ts
	 *            the point in time to cut on, in milliseconds.
	 * @return a new filter to select commits on or after <code>ts</code>.
	 */
	public static final RevFilter after(long ts) {
		return after(Instant.ofEpochMilli(ts));
	}

	/**
	 * Create a new filter to select commits after or equal a given date/time <code>since</code>
	 * and before or equal a given date/time <code>until</code>.
	 *
	 * @param since the point in time to cut on.
	 * @param until the point in time to cut off.
	 * @return a new filter to select commits between the given date/times.
	 *
	 * @deprecated Use {@link #between(Instant, Instant)} instead.
	 */
	@Deprecated(since="7.2")
	public static final RevFilter between(Date since, Date until) {
		return between(since.toInstant(), until.toInstant());
	}

	/**
	 * Create a new filter to select commits after or equal a given date/time
	 * <code>since</code> and before or equal a given date/time
	 * <code>until</code>.
	 *
	 * @param since
	 *            the point in time to cut on.
	 * @param until
	 *            the point in time to cut off.
	 * @return a new filter to select commits between the given date/times.
	 * @since 7.2
	 */
	public static RevFilter between(Instant since, Instant until) {
		return new Between(since, until);
	}

	/**
	 * Create a new filter to select commits after or equal a given date/time <code>since</code>
	 * and before or equal a given date/time <code>until</code>.
	 *
	 * @param since the point in time to cut on, in milliseconds.
	 * @param until the point in time to cut off, in millisconds.
	 * @return a new filter to select commits between the given date/times.
	 *
	 * @deprecated Use {@link #between(Instant, Instant)} instead.
	 */
	@Deprecated(since="7.2")
	public static final RevFilter between(long since, long until) {
		return new Between(Instant.ofEpochMilli(since), Instant.ofEpochMilli(until));
	}

	final int when;

	CommitTimeRevFilter(long ts) {
		when = (int) (ts / 1000);
	}

	CommitTimeRevFilter(Instant t) {
		when = (int) t.getEpochSecond();
	}

	@Override
	public RevFilter clone() {
		return this;
	}

	@Override
	public boolean requiresCommitBody() {
		return false;
	}

	private static class Before extends CommitTimeRevFilter {
		Before(Instant t) {
			super(t);
		}

		@Override
		public boolean include(RevWalk walker, RevCommit cmit)
				throws StopWalkException, MissingObjectException,
				IncorrectObjectTypeException, IOException {
			return cmit.getCommitTime() <= when;
		}

		@SuppressWarnings("nls")
		@Override
		public String toString() {
			return super.toString() + "(" + Instant.ofEpochSecond(when) + ")";
		}
	}

	private static class After extends CommitTimeRevFilter {
		After(Instant t) { super(t); }

		@Override
		public boolean include(RevWalk walker, RevCommit cmit)
				throws StopWalkException, MissingObjectException,
				IncorrectObjectTypeException, IOException {
			// Since the walker sorts commits by commit time we can be
			// reasonably certain there is nothing remaining worth our
			// scanning if this commit is before the point in question.
			//
			if (cmit.getCommitTime() < when)
				throw StopWalkException.INSTANCE;
			return true;
		}

		@SuppressWarnings("nls")
		@Override
		public String toString() {
			return super.toString() + "(" + Instant.ofEpochSecond(when) + ")";
		}
	}

	private static class Between extends CommitTimeRevFilter {
		private final int until;

		Between(Instant since, Instant until) {
			super(since);
			this.until = (int) until.getEpochSecond();
		}

		@Override
		public boolean include(RevWalk walker, RevCommit cmit)
				throws StopWalkException, MissingObjectException,
				IncorrectObjectTypeException, IOException {
			return cmit.getCommitTime() <= until && cmit.getCommitTime() >= when;
		}

		@SuppressWarnings("nls")
		@Override
		public String toString() {
			return super.toString() + "(" + Instant.ofEpochSecond(when) + " - "
					+ Instant.ofEpochSecond(until) + ")";
		}

	}

}
