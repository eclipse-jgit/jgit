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

import static org.junit.Assert.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Test construction of squash message by {@link SquashMessageFormatterTest}.
 */
public class SquashMessageFormatterTest extends SampleDataRepositoryTestCase {
	private SquashMessageFormatter formatter;

	private RevCommit revCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		formatter = new SquashMessageFormatter();
	}

	@Test
	public void testCommit() throws Exception {
		Git git = new Git(db);
		revCommit = git.commit().setMessage("squash_me").call();

		Ref master = db.getRef("refs/heads/master");
		String message = formatter.format(Arrays.asList(revCommit), master);
		assertEquals(
				"Squashed commit of the following:\n\ncommit "
						+ revCommit.getName() + "\nAuthor: "
						+ revCommit.getAuthorIdent().getName() + " <"
						+ revCommit.getAuthorIdent().getEmailAddress()
						+ ">\nDate:   " + formatCommitTime(author)
						+ "\n\n    squash_me\n", message);
	}

	private String formatCommitTime(PersonIdent a) {
		SimpleDateFormat dtfmt = new SimpleDateFormat(
				"EEE MMM d HH:mm:ss yyyy Z", Locale.US);
		dtfmt.setTimeZone(a.getTimeZone());
		return dtfmt.format(a.getWhen());
	}
}
