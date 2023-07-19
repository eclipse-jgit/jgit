/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class FooterLineTest extends RepositoryTestCase {
	@Test
	public void testNoFooters_EmptyBody() {
		final String msg = buildMessage("");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		assertNotNull(footers);
		assertEquals(0, footers.size());
	}

	@Test
	public void testNoFooters_NewlineOnlyBody1() {
		final String msg = buildMessage("\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		assertNotNull(footers);
		assertEquals(0, footers.size());
	}

	@Test
	public void testNoFooters_NewlineOnlyBody5() {
		final String msg = buildMessage("\n\n\n\n\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		assertNotNull(footers);
		assertEquals(0, footers.size());
	}

	@Test
	public void testNoFooters_OneLineBodyNoLF() {
		final String msg = buildMessage("this is a commit");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		assertNotNull(footers);
		assertEquals(0, footers.size());
	}

	@Test
	public void testNoFooters_OneLineBodyWithLF() {
		final String msg = buildMessage("this is a commit\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		assertNotNull(footers);
		assertEquals(0, footers.size());
	}

	@Test
	public void testNoFooters_ShortBodyNoLF() {
		final String msg = buildMessage("subject\n\nbody of commit");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		assertNotNull(footers);
		assertEquals(0, footers.size());
	}

	@Test
	public void testNoFooters_ShortBodyWithLF() {
		final String msg = buildMessage("subject\n\nbody of commit\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		assertNotNull(footers);
		assertEquals(0, footers.size());
	}

	@Test
	public void testSignedOffBy_OneUserNoLF() {
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "Signed-off-by: A. U. Thor <a@example.com>");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
	}

	@Test
	public void testSignedOffBy_OneUserWithLF() {
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "Signed-off-by: A. U. Thor <a@example.com>\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
	}

	@Test
	public void testSignedOffBy_IgnoreWhitespace() {
		// We only ignore leading whitespace on the value, trailing
		// is assumed part of the value.
		//
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "Signed-off-by:   A. U. Thor <a@example.com>  \n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("A. U. Thor <a@example.com>  ", f.getValue());
		assertEquals("a@example.com", f.getEmailAddress());
	}

	@Test
	public void testEmptyValueNoLF() {
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "Signed-off-by:");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("", f.getValue());
		assertNull(f.getEmailAddress());
	}

	@Test
	public void testEmptyValueWithLF() {
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "Signed-off-by:\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Signed-off-by", f.getKey());
		assertEquals("", f.getValue());
		assertNull(f.getEmailAddress());
	}

	@Test
	public void testShortKey() {
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "K:V\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("K", f.getKey());
		assertEquals("V", f.getValue());
		assertNull(f.getEmailAddress());
	}

	@Test
	public void testNonDelimtedEmail() {
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "Acked-by: re@example.com\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Acked-by", f.getKey());
		assertEquals("re@example.com", f.getValue());
		assertEquals("re@example.com", f.getEmailAddress());
	}

	@Test
	public void testNotEmail() {
		final String msg = buildMessage("subject\n\nbody of commit\n" + "\n"
			+ "Acked-by: Main Tain Er\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
		FooterLine f;

		assertNotNull(footers);
		assertEquals(1, footers.size());

		f = footers.get(0);
		assertEquals("Acked-by", f.getKey());
		assertEquals("Main Tain Er", f.getValue());
		assertNull(f.getEmailAddress());
	}

	@Test
	public void testSignedOffBy_ManyUsers() {
		final String msg = buildMessage("subject\n\nbody of commit\n"
			+ "Not-A-Footer-Line: this line must not be read as a footer\n"
			+ "\n" // paragraph break, now footers appear in final block
			+ "Signed-off-by: A. U. Thor <a@example.com>\n"
			+ "CC:            <some.mailing.list@example.com>\n"
			+ "Acked-by: Some Reviewer <sr@example.com>\n"
			+ "Signed-off-by: Main Tain Er <mte@example.com>\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
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
	}

	@Test
	public void testSignedOffBy_SkipNonFooter() {
		final String msg = buildMessage("subject\n\nbody of commit\n"
			+ "Not-A-Footer-Line: this line must not be read as a footer\n"
			+ "\n" // paragraph break, now footers appear in final block
			+ "Signed-off-by: A. U. Thor <a@example.com>\n"
			+ "CC:            <some.mailing.list@example.com>\n"
			+ "not really a footer line but we'll skip it anyway\n"
			+ "Acked-by: Some Reviewer <sr@example.com>\n"
			+ "Signed-off-by: Main Tain Er <mte@example.com>\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);
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
	}

	@Test
	public void testFilterFootersIgnoreCase() throws IOException {
		final String msg = buildMessage("subject\n\nbody of commit\n"
			+ "Not-A-Footer-Line: this line must not be read as a footer\n"
			+ "\n" // paragraph break, now footers appear in final block
			+ "Signed-Off-By: A. U. Thor <a@example.com>\n"
			+ "CC:            <some.mailing.list@example.com>\n"
			+ "Acked-by: Some Reviewer <sr@example.com>\n"
			+ "signed-off-by: Main Tain Er <mte@example.com>\n");
		final List<String> footers = FooterLine.getValues(
			FooterLine.fromMessage(msg), "signed-off-by");

		assertNotNull(footers);
		assertEquals(2, footers.size());

		assertEquals("A. U. Thor <a@example.com>", footers.get(0));
		assertEquals("Main Tain Er <mte@example.com>", footers.get(1));
	}

	@Test
	public void testMatchesBugId() {
		final String msg = buildMessage("this is a commit subject for test\n"
			+ "\n" // paragraph break, now footers appear in final block
			+ "Simple-Bug-Id: 42\n");
		final List<FooterLine> footers = FooterLine.fromMessage(msg);

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
	}

	private String buildMessage(String msg) {
		final StringBuilder buf = new StringBuilder();
		buf.append("tree " + ObjectId.zeroId().name() + "\n");
		buf.append("author A. U. Thor <a@example.com> 1 +0000\n");
		buf.append("committer A. U. Thor <a@example.com> 1 +0000\n");
		buf.append("\n");
		buf.append(msg);
		return buf.toString();
	}
}
