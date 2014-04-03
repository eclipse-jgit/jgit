/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import static java.lang.Integer.valueOf;
import static java.lang.Long.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.junit.Before;
import org.junit.Test;

public class ObjectCheckerTest {
	private ObjectChecker checker;

	@Before
	public void setUp() throws Exception {
		checker = new ObjectChecker();
	}

	@Test
	public void testInvalidType() {
		try {
			checker.check(Constants.OBJ_BAD, new byte[0]);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException e) {
			final String m = e.getMessage();
			assertEquals(MessageFormat.format(
					JGitText.get().corruptObjectInvalidType2,
					valueOf(Constants.OBJ_BAD)), m);
		}
	}

	@Test
	public void testCheckBlob() throws CorruptObjectException {
		// Any blob should pass...
		checker.checkBlob(new byte[0]);
		checker.checkBlob(new byte[1]);

		checker.check(Constants.OBJ_BLOB, new byte[0]);
		checker.check(Constants.OBJ_BLOB, new byte[1]);
	}

	@Test
	public void testValidCommitNoParent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommitBlankAuthor() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author <> 0 +0000\n");
		b.append("committer <> 0 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommit1Parent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommit2Parent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommit128Parent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		for (int i = 0; i < 128; i++) {
			b.append("parent ");
			b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
			b.append('\n');
		}

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommitNormalTime() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		final String when = "1222757360 -0730";

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> " + when + "\n");
		b.append("committer A. U. Thor <author@localhost> " + when + "\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(Constants.OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitNoTree1() {
		final StringBuilder b = new StringBuilder();

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitNoTree2() {
		final StringBuilder b = new StringBuilder();

		b.append("trie ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitNoTree3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitNoTree4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidTree1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("zzzzfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidTree2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("z\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidTree3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9b");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidTree4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidParent1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidParent2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("zzzzfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidParent3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidParent4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("z\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidParent5() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitNoAuthor() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitNoCommitter1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no committer", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitNoCommitter2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no committer", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidAuthor1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <foo 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidAuthor2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor foo> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidAuthor3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidAuthor4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidAuthor5() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b>\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidAuthor6() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> z");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidAuthor7() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> 1 z");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	@Test
	public void testInvalidCommitInvalidCommitter() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> 1 +0000\n");
		b.append("committer a <");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkCommit(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid committer", e.getMessage());
		}
	}

	@Test
	public void testValidTag() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag test-tag\n");
		b.append("tagger A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTag(data);
		checker.check(Constants.OBJ_TAG, data);
	}

	@Test
	public void testInvalidTagNoObject1() {
		final StringBuilder b = new StringBuilder();

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoObject2() {
		final StringBuilder b = new StringBuilder();

		b.append("object\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoObject3() {
		final StringBuilder b = new StringBuilder();

		b.append("obejct ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no object header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoObject4() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("zz9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoObject5() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append(" \n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoObject6() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid object", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoType1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoType2() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type\tcommit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoType3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("tpye commit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no type header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoType4() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoTagHeader1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoTagHeader2() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag\tfoo\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagNoTagHeader3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tga foo\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("no tag header", e.getMessage());
		}
	}

	@Test
	public void testValidTagHasNoTaggerHeader() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");

		checker.checkTag(Constants.encodeASCII(b.toString()));
	}

	@Test
	public void testInvalidTagInvalidTaggerHeader1() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger \n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tagger", e.getMessage());
		}
	}

	@Test
	public void testInvalidTagInvalidTaggerHeader3() {
		final StringBuilder b = new StringBuilder();

		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger a < 1 +000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTag(data);
			fail("incorrectly accepted invalid tag");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tagger", e.getMessage());
		}
	}

	@Test
	public void testValidEmptyTree() throws CorruptObjectException {
		checker.checkTree(new byte[0]);
		checker.check(Constants.OBJ_TREE, new byte[0]);
	}

	@Test
	public void testValidTree1() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 regular-file");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTree2() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100755 executable");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTree3() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 tree");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTree4() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "120000 symlink");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTree5() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "160000 git link");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTree6() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 .a");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidPosixTree() throws CorruptObjectException {
		checkOneName("a<b>c:d|e");
		checkOneName("test ");
		checkOneName("test.");
		checkOneName("NUL");
	}

	@Test
	public void testValidTreeSorting1() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 fooaaa");
		entry(b, "100755 foobar");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeSorting2() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100755 fooaaa");
		entry(b, "100644 foobar");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeSorting3() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeSorting4() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeSorting5() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a.c");
		entry(b, "40000 a");
		entry(b, "100644 a0c");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeSorting6() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 apple");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeSorting7() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 an orang");
		entry(b, "40000 an orange");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeSorting8() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a0c");
		entry(b, "100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testAcceptTreeModeWithZero() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "040000 a");
		checker.setAllowLeadingZeroFileMode(true);
		checker.checkTree(Constants.encodeASCII(b.toString()));
	}

	@Test
	public void testInvalidTreeModeStartsWithZero1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "0 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeModeStartsWithZero2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "0100644 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeModeStartsWithZero3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "040000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("mode starts with '0'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeModeNotOctal1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "8 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode character", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeModeNotOctal2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "Z a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode character", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeModeNotSupportedMode1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "1 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode 1", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeModeNotSupportedMode2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "170000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid mode " + 0170000, e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeModeMissingName() {
		final StringBuilder b = new StringBuilder();
		b.append("100644");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in mode", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeNameContainsSlash() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a/b");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("name contains '/'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeNameIsEmpty() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 ");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("zero length name", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeNameIsDot() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 .");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '.'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeNameIsDotDot() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 ..");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '..'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeNameIsGit() {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git");
		byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '.git'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeNameIsMixedCaseGitWindows() {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .GiT");
		byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.setSafeForWindows(true);
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '.GiT'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeNameIsMixedCaseGitMacOS() {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .GiT");
		byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.setSafeForMacOS(true);
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name '.GiT'", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeTruncatedInName() {
		final StringBuilder b = new StringBuilder();
		b.append("100644 b");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in name", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeTruncatedInObjectId() {
		final StringBuilder b = new StringBuilder();
		b.append("100644 b\0\1\2");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("truncated in object id", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeBadSorting1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 foobar");
		entry(b, "100644 fooaaa");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeBadSorting2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 a.c");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeBadSorting3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a0c");
		entry(b, "40000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("incorrectly sorted", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames1() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames2() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100755 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames3() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 a");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames4() {
		final StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a.c");
		entry(b, "100644 a.d");
		entry(b, "100644 a.e");
		entry(b, "40000 a");
		entry(b, "100644 zoo");
		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames5()
			throws UnsupportedEncodingException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 A");
		byte[] data = b.toString().getBytes("UTF-8");
		try {
			checker.setSafeForWindows(true);
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames6()
			throws UnsupportedEncodingException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 A");
		byte[] data = b.toString().getBytes("UTF-8");
		try {
			checker.setSafeForMacOS(true);
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames7()
			throws UnsupportedEncodingException {
		try {
			Class.forName("java.text.Normalizer");
		} catch (ClassNotFoundException e) {
			// Ignore this test on Java 5 platform.
			return;
		}

		StringBuilder b = new StringBuilder();
		entry(b, "100644 \u0065\u0301");
		entry(b, "100644 \u00e9");
		byte[] data = b.toString().getBytes("UTF-8");
		try {
			checker.setSafeForMacOS(true);
			checker.checkTree(data);
			fail("incorrectly accepted an invalid tree");
		} catch (CorruptObjectException e) {
			assertEquals("duplicate entry names", e.getMessage());
		}
	}

	@Test
	public void testInvalidTreeDuplicateNames8()
			throws UnsupportedEncodingException, CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 A");
		checker.setSafeForMacOS(true);
		checker.checkTree(b.toString().getBytes("UTF-8"));
	}

	@Test
	public void testRejectNulInPathSegment() {
		try {
			checker.checkPathSegment(Constants.encodeASCII("a\u0000b"), 0, 3);
			fail("incorrectly accepted NUL in middle of name");
		} catch (CorruptObjectException e) {
			assertEquals("name contains byte 0x00", e.getMessage());
		}
	}

	@Test
	public void testRejectSpaceAtEndOnWindows() {
		checker.setSafeForWindows(true);
		try {
			checkOneName("test ");
			fail("incorrectly accepted space at end");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name ends with ' '", e.getMessage());
		}
	}

	@Test
	public void testRejectDotAtEndOnWindows() {
		checker.setSafeForWindows(true);
		try {
			checkOneName("test.");
			fail("incorrectly accepted dot at end");
		} catch (CorruptObjectException e) {
			assertEquals("invalid name ends with '.'", e.getMessage());
		}
	}

	@Test
	public void testRejectDevicesOnWindows() {
		checker.setSafeForWindows(true);

		String[] bad = { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3",
				"COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2",
				"LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };
		for (String b : bad) {
			try {
				checkOneName(b);
				fail("incorrectly accepted " + b);
			} catch (CorruptObjectException e) {
				assertEquals("invalid name '" + b + "'", e.getMessage());
			}
			try {
				checkOneName(b + ".txt");
				fail("incorrectly accepted " + b + ".txt");
			} catch (CorruptObjectException e) {
				assertEquals("invalid name '" + b + "'", e.getMessage());
			}
		}
	}

	@Test
	public void testRejectInvalidWindowsCharacters() {
		checker.setSafeForWindows(true);
		rejectName('<');
		rejectName('>');
		rejectName(':');
		rejectName('"');
		rejectName('/');
		rejectName('\\');
		rejectName('|');
		rejectName('?');
		rejectName('*');

		for (int i = 1; i <= 31; i++)
			rejectName((byte) i);
	}

	private void rejectName(char c) {
		try {
			checkOneName("te" + c + "st");
			fail("incorrectly accepted with " + c);
		} catch (CorruptObjectException e) {
			assertEquals("name contains '" + c + "'", e.getMessage());
		}
	}

	private void rejectName(byte c) {
		String h = Integer.toHexString(c);
		try {
			checkOneName("te" + ((char) c) + "st");
			fail("incorrectly accepted with 0x" + h);
		} catch (CorruptObjectException e) {
			assertEquals("name contains byte 0x" + h, e.getMessage());
		}
	}

	private void checkOneName(String name) throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 " + name);
		checker.checkTree(Constants.encodeASCII(b.toString()));
	}

	private static void entry(final StringBuilder b, final String modeName) {
		b.append(modeName);
		b.append('\0');
		for (int i = 0; i < Constants.OBJECT_ID_LENGTH; i++)
			b.append((char) i);
	}
}
