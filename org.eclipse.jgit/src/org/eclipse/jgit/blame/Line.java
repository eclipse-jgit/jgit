/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.blame;

import java.text.SimpleDateFormat;
import java.util.Locale;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Line class that spans one or more continuous revisions.
 */
public class Line {

	private RevCommit commit;

	private int number;

	/**
	 * Create a line with starting commit and line number
	 *
	 * @param startCommit
	 * @param lineNumber
	 */
	public Line(RevCommit startCommit, int lineNumber) {
		commit = startCommit;
		number = lineNumber;
	}

	/**
	 * Set start commit
	 *
	 * @param startCommit
	 * @return this line
	 */
	Line setStart(RevCommit startCommit) {
		commit = startCommit;
		return this;
	}

	/**
	 * Get the commit in which this line was introduced
	 *
	 * @return number
	 */
	public RevCommit getCommit() {
		return commit;
	}

	/**
	 * Get the line number of this line at the latest revision
	 *
	 * @return startNumber
	 */
	public int getNumber() {
		return number;
	}

	@Override
	public int hashCode() {
		return commit.hashCode() ^ (number + 1);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (obj instanceof Line) {
			Line other = (Line) obj;
			return commit.equals(other.commit) && number == other.number;
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append('^');
		builder.append(commit.abbreviate(7).name());
		builder.append(' ').append('(');
		PersonIdent author = commit.getAuthorIdent();
		builder.append(author.getName());
		builder.append(' ');
		final SimpleDateFormat format;
		format = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
		format.setTimeZone(author.getTimeZone());
		builder.append(format.format(author.getWhen()));
		builder.append(' ');
		builder.append(number + 1);
		builder.append(')');
		return builder.toString();
	}

}
