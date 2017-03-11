/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class FooterLineTest extends RepositoryTestCase {
	@Test
	public void testNoFooters_EmptyBody() throws IOException {
		final RevCommit commit = parse("");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_OneChar() throws IOException {
		final RevCommit commit = parse("a");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("a", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_NewlineOnlyBody1() throws IOException {
		final RevCommit commit = parse("\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_OneCharNewLine() throws IOException {
		final RevCommit commit = parse("a\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("a", msgWithoutFooter);
	}


	@Test
	public void testNoFooters_NewlineOnlyBody5() throws IOException {
		final RevCommit commit = parse("\n\n\n\n\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_OneLineBodyNoLF() throws IOException {
		final RevCommit commit = parse("this is a commit");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("this is a commit", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_OneLineBodyWithLF() throws IOException {
		final RevCommit commit = parse("this is a commit\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("this is a commit", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_LongTitle() throws IOException {
		final RevCommit commit = parse("this is a very\nlong title\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		final String title = commit.getShortMessage();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("this is a very long title", title);
		assertEquals("this is a very\nlong title", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_ShortBodyNoLF() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_ShortBodyWithLF() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testNoFooters_LongBody() throws IOException {
		final RevCommit commit = parse(
				"subject\n\nbody of commit\nsecond body of commit");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("subject\n\nbody of commit\nsecond body of commit",
				msgWithoutFooter);
	}

	@Test
	public void testNoFooters_LongBodySLF() throws IOException {
		final RevCommit commit = parse(
				"subject\n\nbody of commit\nsecond body of commit\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("subject\n\nbody of commit\nsecond body of commit",
				msgWithoutFooter);
	}

	@Test
	public void testNoFooters_LongBodyWithDLF() throws IOException {
		final RevCommit commit = parse(
				"subject\n\nbody of commit\nsecond body of commit\n\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("subject\n\nbody of commit\nsecond body of commit",
				msgWithoutFooter);
	}

	@Test
	public void testNoFooters_NoFooterParagraph() throws IOException {
		final RevCommit commit = parse("subject\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		final String title = commit.getShortMessage();

		assertNotNull(footers);
		assertEquals(0, footers.size());
		assertEquals("subject Signed-off-by: A. U. Thor <a@example.com>", title);
		assertEquals("subject\nSigned-off-by: A. U. Thor <a@example.com>",
				msgWithoutFooter);
	}

	@Test
	public void testOneFooter_NoBody() throws IOException {
		final RevCommit commit = parse("subject\n\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		final String title = commit.getShortMessage();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject", title);
		assertEquals("subject", msgWithoutFooter);
	}

	@Test
	public void testOneFooter_NoBodySkip() throws IOException {
		final RevCommit commit = parse("subject\n\n"
				+ "not really a footer line but we'll skip it anyway\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		final String title = commit.getShortMessage();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject", title);
		assertEquals("subject", msgWithoutFooter);
	}


	@Test
	public void testSkipFooter_HeadAndTrail() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "not really a footer line but we'll skip it anyway\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>\n"
				+ "not really a footer line but we'll skip it anyway\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		final String title = commit.getShortMessage();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject", title);
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testSkipFooter_HeadOnly() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "not really a footer line but we'll skip it anyway\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		final String title = commit.getShortMessage();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject", title);
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testSkipFooter_TrailOnly() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>\n"
				+ "not really a footer line but we'll skip it anyway\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		final String title = commit.getShortMessage();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject", title);
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testSignedOffBy_OneUserNoLF() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testSignedOffBy_OneUserWithLF() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Signed-off-by: A. U. Thor <a@example.com>\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testSignedOffBy_IgnoreWhitespace() throws IOException {
		// We only ignore leading whitespace on the value, trailing
		// is assumed part of the value.
		//
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Signed-off-by:   A. U. Thor <a@example.com>  \n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>  ", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testEmptyValueNoLF() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Signed-off-by:");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("", f.getValue());
		assertNull(f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testEmptyValueWithLF() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Signed-off-by:\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("", f.getValue());
		assertNull(f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testShortKey() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "K:V\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("K", f.getKey());
		assertEquals("V", f.getValue());
		assertNull(f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testNonDelimtedEmail() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Acked-by: re@example.com\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Acked-by", f.getKey());
		assertEquals("re@example.com", f.getValue());
		assertEquals("re@example.com", f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testNotEmail() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n\n"
				+ "Acked-by: Main Tain Er\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Acked-by", f.getKey());
		assertEquals("Main Tain Er", f.getValue());
		assertNull(f.getEmailAddress());
		assertEquals("subject\n\nbody of commit", msgWithoutFooter);
	}

	@Test
	public void testSignedOffBy_ManyUsers() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n"
				+ "Not-A-Footer-Line: this line must not be read as a footer\n"
				+ "\n" // paragraph break, now footers appear in final block
				+ "Signed-off-by: A. U. Thor <a@example.com>\n"
				+ "CC:            <some.mailing.list@example.com>\n"
				+ "Acked-by: Some Reviewer <sr@example.com>\n"
				+ "Signed-off-by: Main Tain Er <mte@example.com>\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(4, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());

		f = footers.get(1);
		assertEquals("CC", f.getKey());
		assertEquals("<some.mailing.list@example.com>", f.getValue());
		assertEquals("some.mailing.list@example.com", f.getEmailAddress());

		f = footers.get(2);
		assertEquals("Acked-by", f.getKey());
		assertEquals("Some Reviewer <sr@example.com>", f.getValue());
		assertEquals("sr@example.com", f.getEmailAddress());

		f = footers.get(3);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("Main Tain Er <mte@example.com>", f.getValue());
		assertEquals("mte@example.com", f.getEmailAddress());
		assertEquals(
				"subject\n\nbody of commit\n"
						+ "Not-A-Footer-Line: this line must not be read as a footer",
				msgWithoutFooter);
	}

	@Test
	public void testSignedOffBy_SkipNonFooter() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n"
				+ "Not-A-Footer-Line: this line must not be read as a footer\n"
				+ "\n" // paragraph break, now footers appear in final block
				+ "Signed-off-by: A. U. Thor <a@example.com>\n"
				+ "CC:            <some.mailing.list@example.com>\n"
				+ "not really a footer line but we'll skip it anyway\n"
				+ "Acked-by: Some Reviewer <sr@example.com>\n"
				+ "Signed-off-by: Main Tain Er <mte@example.com>\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();
		FooterLine f;

		assertNotNull(footers);
		assertEquals(4, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());

		f = footers.get(1);
		assertEquals("CC", f.getKey());
		assertEquals("<some.mailing.list@example.com>", f.getValue());

		f = footers.get(2);
		assertEquals("Acked-by", f.getKey());
		assertEquals("Some Reviewer <sr@example.com>", f.getValue());

		f = footers.get(3);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("Main Tain Er <mte@example.com>", f.getValue());
		assertEquals(
				"subject\n\nbody of commit\n"
						+ "Not-A-Footer-Line: this line must not be read as a footer",
				msgWithoutFooter);
	}

	@Test
	public void testFilterFootersIgnoreCase() throws IOException {
		final RevCommit commit = parse("subject\n\nbody of commit\n"
				+ "Not-A-Footer-Line: this line must not be read as a footer\n"
				+ "\n" // paragraph break, now footers appear in final block
				+ "Signed-Off-By: A. U. Thor <a@example.com>\n"
				+ "CC:            <some.mailing.list@example.com>\n"
				+ "Acked-by: Some Reviewer <sr@example.com>\n"
				+ "signed-off-by: Main Tain Er <mte@example.com>\n");
		final List<String> footers = commit.getFooterLines("signed-off-by");
		final String msgWithoutFooter = commit.getMessageWithoutFooter();

		assertNotNull(footers);
		assertEquals(2, footers.size());

		assertEquals("A. U. Thor <a@example.com>", footers.get(0));
		assertEquals("Main Tain Er <mte@example.com>", footers.get(1));
		assertEquals(
				"subject\n\nbody of commit\n"
						+ "Not-A-Footer-Line: this line must not be read as a footer",
				msgWithoutFooter);
	}

	@Test
	public void testMatchesBugId() throws IOException {
		final RevCommit commit = parse("this is a commit subject for test\n"
				+ "\n" // paragraph break, now footers appear in final block
				+ "Simple-Bug-Id: 42\n");
		final List<FooterLine> footers = commit.getFooterLines();
		final String msgWithoutFooter = commit.getMessageWithoutFooter();

		assertNotNull(footers);
		assertEquals(1, footers.size());

		final FooterLine line = footers.get(0);
		assertNotNull(line);
		assertEquals("Simple-Bug-Id", line.getKey());
		assertEquals("42", line.getValue());

		final FooterKey bugid = new FooterKey("Simple-Bug-Id");
		assertTrue("matches Simple-Bug-Id", line.matches(bugid));
		assertFalse("not Signed-off-by", line.matches(FooterKey.SIGNED_OFF_BY));
		assertFalse("not CC", line.matches(FooterKey.CC));
		assertEquals("this is a commit subject for test", msgWithoutFooter);
	}

	private RevCommit parse(final String msg) throws IOException {
		final StringBuilder buf = new StringBuilder();
		buf.append("tree " + ObjectId.zeroId().name() + "\n");
		buf.append("author A. U. Thor <a@example.com> 1 +0000\n");
		buf.append("committer A. U. Thor <a@example.com> 1 +0000\n");
		buf.append("\n");
		buf.append(msg);

		try (RevWalk walk = new RevWalk(db)) {
			RevCommit c = new RevCommit(ObjectId.zeroId());
			c.parseCanonical(walk, Constants.encode(buf.toString()));
			return c;
		}
	}
}
