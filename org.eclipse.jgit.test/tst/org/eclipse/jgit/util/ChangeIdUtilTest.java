/*
 * Copyright (C) 2010, Robin Rosenberg
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
package org.eclipse.jgit.util;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;

import junit.framework.TestCase;

public class ChangeIdUtilTest extends TestCase {

	private static final ObjectId changeId = ObjectId
			.fromString("0123456789012345678901234567890123456789");

	public void testClean() {
		assertEquals("hej", ChangeIdUtil.clean("hej\n\n"));
		assertEquals("hej\nsan", ChangeIdUtil.clean("hej\n\nsan\n\n"));
		assertEquals("hej\nsan", ChangeIdUtil.clean("hej\n#men\nsan\n\n#men"));
		assertEquals("hej\nsan", ChangeIdUtil.clean("hej\nsan\n\n#men"));
		assertEquals("hej\nsan", ChangeIdUtil.clean("#no\nhej\nsan\n\n#men"));
		assertEquals("hej\nsan", ChangeIdUtil
				.clean("#no\nhej\nsan\nSigned-off-by: me \n#men"));
	}

	public void testId() throws IOException {
		String msg = "A\nMessage\n";
		final String i = "A U Thor <author@example.com> 1142878501 -0500";
		final String j = "W Riter <writer@example.com> 1142878502 -0500";
		final PersonIdent p = new PersonIdent(i);
		final PersonIdent q = new PersonIdent(j);
		ObjectId treeId = ObjectId
				.fromString("f51de923607cd51cf872b928a6b523ba823f7f35");
		ObjectId parentId = ObjectId
				.fromString("91fea719aaf9447feb9580477eb3dd08b62b5eca");
		ObjectId id = ChangeIdUtil.computeChangeId(treeId, parentId, p, q, msg);
		assertEquals("73f3751208ac92cbb76f9a26ac4a0d9d472e381b", ObjectId
				.toString(id));
	}

	// generated tests below
	/**
	 * has changeid
	 */
	public void test_has_changeid() {
		assertEquals(
				"has changeid\n\nBug: 33\nmore text\nSigned-off-by: me@you.too\nChange-Id: I0123456789012345678901234567890123456789\nAnd then some\n",
				ChangeIdUtil
						.insertId(
								"has changeid\n\nBug: 33\nmore text\nSigned-off-by: me@you.too\nChange-Id: I0123456789012345678901234567890123456789\nAnd then some\n",
								changeId));
	}

	/**
	 * oneliner
	 */
	public void test_oneliner() {
		assertEquals(
				"oneliner\n\nChange-Id: I0123456789012345678901234567890123456789\n",
				ChangeIdUtil.insertId("oneliner\n", changeId));
	}

	/**
	 * oneliner followed by blank
	 */
	public void test_oneliner_followed_by_blank() {
		assertEquals(
				"oneliner followed by blank\n\nChange-Id: I0123456789012345678901234567890123456789\n",
				ChangeIdUtil.insertId("oneliner followed by blank\n", changeId));
	}

	/**
	 * a two lines
	 */
	public void test_a_two_lines() {
		assertEquals(
				"a two lines\nwith text withour break after subject line\n\nChange-Id: I0123456789012345678901234567890123456789\n",
				ChangeIdUtil
						.insertId(
								"a two lines\nwith text withour break after subject line\n",
								changeId));
	}

	/**
	 * regular commit
	 */
	public void test_regular_commit() {
		assertEquals(
				"regular commit\n\nwith header and body\n\nChange-Id: I0123456789012345678901234567890123456789\n",
				ChangeIdUtil.insertId(
						"regular commit\n\nwith header and body\n", changeId));
	}

	/**
	 * regular commit with sob, but no body
	 */
	public void test_regular_commit_with_sob__but_no_body() {
		assertEquals(
				"regular commit with sob, but no body\n\nChange-Id: I0123456789012345678901234567890123456789\nSigned-off-by: me@you.too\n",
				ChangeIdUtil
						.insertId(
								"regular commit with sob, but no body\n\nSigned-off-by: me@you.too\n",
								changeId));
	}

	/**
	 * a commit with bug, sub but no body
	 */
	public void test_a_commit_with_bug__sub_but_no_body() {
		assertEquals(
				"a commit with bug, sub but no body\n\nBug: 33\nChange-Id: I0123456789012345678901234567890123456789\nSigned-off-by: me@you.too\n",
				ChangeIdUtil
						.insertId(
								"a commit with bug, sub but no body\n\nBug: 33\nSigned-off-by: me@you.too\n",
								changeId));
	}

	/**
	 * a commit with subject, no body sob and bug
	 */
	public void test_a_commit_with_subject__no_body_sob_and_bug() {
		assertEquals(
				"a commit with subject, no body sob and bug\n\nChange-Id: I0123456789012345678901234567890123456789\nSigned-off-by: me@you.too\nBug: 33\n",
				ChangeIdUtil
						.insertId(
								"a commit with subject, no body sob and bug\n\nSigned-off-by: me@you.too\nBug: 33\n",
								changeId));
	}

	/**
	 * a commit with subject bug, non-footer line and sob
	 */
	public void test_a_commit_with_subject_bug__non_footer_line_and_sob() {
		assertEquals(
				"a commit with subject bug, non-footer line and sob\n\nBug: 33\nmore text\nSigned-off-by: me@you.too\n\nChange-Id: I0123456789012345678901234567890123456789\n",
				ChangeIdUtil
						.insertId(
								"a commit with subject bug, non-footer line and sob\n\nBug: 33\nmore text\nSigned-off-by: me@you.too\n",
								changeId));
	}

	/**
	 * a commit with subject, non-footer and bug and sob
	 */
	public void test_a_commit_with_subject__non_footer_and_bug_and_sob() {
		assertEquals(
				"a commit with subject, non-footer and bug and sob\n\nmore text (two empty lines after bug)\nBug: 33\n\nChange-Id: I0123456789012345678901234567890123456789\nSigned-off-by: me@you.too\n",
				ChangeIdUtil
						.insertId(
								"a commit with subject, non-footer and bug and sob\n\nmore text (two empty lines after bug)\nBug: 33\n\n\nSigned-off-by: me@you.too\n",
								changeId));
	}

	/**
	 * a commit with subject body, bug. brackers and sob
	 */
	public void test_a_commit_with_subject_body__bug__brackers_and_sob() {
		assertEquals(
				"a commit with subject body, bug. brackers and sob\n\nText\n\nBug: 33\nChange-Id: I0123456789012345678901234567890123456789\n[bracket]\nSigned-off-by: me@you.too\n",
				ChangeIdUtil
						.insertId(
								"a commit with subject body, bug. brackers and sob\n\nText\n\nBug: 33\n[bracket]\nSigned-off-by: me@you.too\n\n",
								changeId));
	}

	/**
	 * a commit with subject body, bug. line with a space and sob
	 */
	public void test_a_commit_with_subject_body__bug__line_with_a_space_and_sob() {
		assertEquals(
				"a commit with subject body, bug. line with a space and sob\n\nText\n\nBug: 33\n\nChange-Id: I0123456789012345678901234567890123456789\nSigned-off-by: me@you.too\n",
				ChangeIdUtil
						.insertId(
								"a commit with subject body, bug. line with a space and sob\n\nText\n\nBug: 33\n \nSigned-off-by: me@you.too\n\n",
								changeId));
	}

	/**
	 * a commit with subject body, bug. empty line and sob
	 */
	public void test_a_commit_with_subject_body__bug__empty_line_and_sob() {
		assertEquals(
				"a commit with subject body, bug. empty line and sob\n\nText\n\nBug: 33\n\nChange-Id: I0123456789012345678901234567890123456789\nSigned-off-by: me@you.too\n",
				ChangeIdUtil
						.insertId(
								"a commit with subject body, bug. empty line and sob\n\nText\n\nBug: 33\n \nSigned-off-by: me@you.too\n\n",
								changeId));
	}
}
