/*
 * Copyright (C) 2012, IBM Corporation and others.
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
package org.eclipse.jgit.merge;

import java.util.List;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;

/**
 * Formatter for constructing the commit message for a squashed commit.
 * <p>
 * The format should be the same as C Git does it, for compatibility.
 */
public class SquashMessageFormatter {

	private GitDateFormatter dateFormatter;

	/**
	 * Create a new squash message formatter.
	 */
	public SquashMessageFormatter() {
		dateFormatter = new GitDateFormatter(Format.DEFAULT);
	}
	/**
	 * Construct the squashed commit message.
	 *
	 * @param squashedCommits
	 *            the squashed commits
	 * @param target
	 *            the target branch
	 * @return squashed commit message
	 */
	public String format(List<RevCommit> squashedCommits, Ref target) {
		StringBuilder sb = new StringBuilder();
		sb.append("Squashed commit of the following:\n"); //$NON-NLS-1$
		for (RevCommit c : squashedCommits) {
			sb.append("\ncommit "); //$NON-NLS-1$
			sb.append(c.getName());
			sb.append("\n"); //$NON-NLS-1$
			sb.append(toString(c.getAuthorIdent()));
			sb.append("\n\t"); //$NON-NLS-1$
			sb.append(c.getShortMessage());
			sb.append("\n"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	private String toString(PersonIdent author) {
		final StringBuilder a = new StringBuilder();

		a.append("Author: "); //$NON-NLS-1$
		a.append(author.getName());
		a.append(" <"); //$NON-NLS-1$
		a.append(author.getEmailAddress());
		a.append(">\n"); //$NON-NLS-1$
		a.append("Date:   "); //$NON-NLS-1$
		a.append(dateFormatter.formatDate(author));
		a.append("\n"); //$NON-NLS-1$

		return a.toString();
	}
}
