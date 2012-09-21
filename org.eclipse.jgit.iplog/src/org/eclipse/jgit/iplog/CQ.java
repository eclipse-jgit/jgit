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

import static java.lang.Long.valueOf;

import java.text.MessageFormat;
import java.util.Comparator;

/**
 * A contribution questionnaire stored in IPzilla.
 *
 * @see <a href="http://wiki.eclipse.org/IPzilla">IPzilla - Eclipsepedia</a>
 * @see <a href="https://dev.eclipse.org/ipzilla/">IPzilla - Login</a>
 */
class CQ {
	/** Sorts CQs by their unique number. */
	static final Comparator<CQ> COMPARATOR = new Comparator<CQ>() {
		public int compare(CQ a, CQ b) {
			int cmp = state(a) - state(b);
			if (cmp == 0)
				cmp = compare(a.getID(), b.getID());
			return cmp;
		}

		private int state(CQ a) {
			if ("approved".equals(a.getState()))
				return 1;
			return 50;
		}

		private int compare(long a, long b) {
			return a < b ? -1 : a == b ? 0 : 1;
		}
	};

	private final long id;

	private String description;

	private String license;

	private String use;

	private String state;

	private String comments;

	/**
	 * @param id
	 */
	CQ(final long id) {
		this.id = id;
	}

	/** @return unique id number of the contribution questionnaire. */
	long getID() {
		return id;
	}

	/** @return short description of this CQ record. */
	String getDescription() {
		return description;
	}

	void setDescription(String description) {
		this.description = description;
	}

	/** @return the license the contribution is under. */
	String getLicense() {
		return license;
	}

	void setLicense(String license) {
		this.license = license;
	}

	/** @return how this code is used by the project, e.g. "unmodified binary". */
	String getUse() {
		return use;
	}

	void setUse(String use) {
		this.use = use;
	}

	/** @return TODO find out what state is */
	String getState() {
		return state;
	}

	void setState(String state) {
		this.state = state;
	}

	/** @return any additional comments about this particular CQ. */
	String getComments() {
		return comments;
	}

	void setComments(String comments) {
		this.comments = comments;
	}

	@Override
	public int hashCode() {
		return (int) getID();
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof CQ) {
			return ((CQ) other).getID() == getID();
		}
		return false;
	}

	@Override
	public String toString() {
		return MessageFormat.format(IpLogText.get().CQString, valueOf(getID()));
	}
}
