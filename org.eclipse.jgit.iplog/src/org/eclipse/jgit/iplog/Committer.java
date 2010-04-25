/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.iplog;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A project committer. */
class Committer {
	/** Sorts committers by their name first name, then last name. */
	static final Comparator<Committer> COMPARATOR = new Comparator<Committer>() {
		public int compare(Committer a, Committer b) {
			int cmp = a.firstName.compareTo(b.firstName);
			if (cmp == 0)
				cmp = a.lastName.compareTo(b.lastName);
			return cmp;
		}
	};

	private final String id;

	private String firstName;

	private String lastName;

	private String affiliation;

	private boolean hasCommits;

	private String comments;

	private final Set<String> emailAddresses = new HashSet<String>();

	private final List<ActiveRange> active = new ArrayList<ActiveRange>(2);

	/**
	 * @param id
	 *            unique identity of the committer
	 */
	Committer(String id) {
		this.id = id;
	}

	/** @return unique identity of this committer in the foundation database. */
	String getID() {
		return id;
	}

	/** @return first name of the committer; their given name. */
	String getFirstName() {
		return firstName;
	}

	void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	/** @return last name of the committer; their surname or family name. */
	String getLastName() {
		return lastName;
	}

	void setLastName(String lastName) {
		this.lastName = lastName;
	}

	/** @return the organization the committer is affiliated with. */
	String getAffiliation() {
		return affiliation;
	}

	void setAffiliation(String affiliation) {
		this.affiliation = affiliation;
	}

	/** @return true if this committer is still an active member of the project. */
	boolean isActive() {
		if (active.isEmpty())
			return false;
		ActiveRange last = active.get(active.size() - 1);
		return last.end == null;
	}

	/** @return true if this committer has commits in the project. */
	boolean hasCommits() {
		return hasCommits;
	}

	void setHasCommits(boolean hasCommits) {
		this.hasCommits = hasCommits;
	}

	/** @return any additional comments about this committer. */
	String getComments() {
		return comments;
	}

	void setComments(String comments) {
		this.comments = comments;
	}

	void addEmailAddress(String email) {
		emailAddresses.add(email);
	}

	void addActiveRange(ActiveRange r) {
		active.add(r);
		Collections.sort(active, new Comparator<ActiveRange>() {
			public int compare(ActiveRange a, ActiveRange b) {
				return a.begin.compareTo(b.begin);
			}
		});
	}

	/**
	 * @param when
	 * @return true if the event occurred while an active committer.
	 */
	boolean inRange(Date when) {
		for (ActiveRange ar : active) {
			if (ar.contains(when))
				return true;
		}
		return false;
	}

	@Override
	public String toString() {
		return MessageFormat.format(IpLogText.get().committerString, getFirstName(), getLastName());
	}

	/** Date period during which the committer was active. */
	static class ActiveRange {
		private final Date begin;

		private final Date end;

		/**
		 * @param begin
		 * @param end
		 */
		ActiveRange(Date begin, Date end) {
			this.begin = begin;
			this.end = end;
		}

		/**
		 * @param when
		 * @return true if {@code when} is within this date span.
		 */
		boolean contains(Date when) {
			if (when.compareTo(begin) < 0)
				return false;
			if (end == null)
				return true;
			return when.compareTo(end) < 0;
		}
	}
}
