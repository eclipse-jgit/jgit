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

package org.eclipse.jgit.lib.objectcheckers;

import junit.framework.TestCase;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.Constants;

public class CommitCheckerTest extends TestCase {
	private CommitChecker checker= new CommitChecker();


	public void testValidCommitNoParent() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testValidCommitBlankAuthor() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author <> 0 +0000\n");
		b.append("committer <> 0 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

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
		checker.check(data);
	}

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
		checker.check(data);
	}

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
		checker.check(data);
	}

	public void testValidCommitNormalTime() throws CorruptObjectException {
		final StringBuilder b = new StringBuilder();
		final String when = "1222757360 -0730";

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> " + when + "\n");
		b.append("committer A. U. Thor <author@localhost> " + when + "\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		checker.check(data);
	}

	public void testInvalidCommitNoTree1() {
		final StringBuilder b = new StringBuilder();

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitNoTree2() {
		final StringBuilder b = new StringBuilder();

		b.append("trie ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitNoTree3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitNoTree4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("no tree header", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("zzzzfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("z\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9b");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidTree4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid tree", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidParent1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

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
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

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
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

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
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			assertEquals("invalid parent", e.getMessage());
		}
	}

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
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no author", e.getMessage());
		}
	}

	public void testInvalidCommitNoAuthor() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no author", e.getMessage());
		}
	}

	public void testInvalidCommitNoCommitter1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no committer", e.getMessage());
		}
	}

	public void testInvalidCommitNoCommitter2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("no committer", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor1() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <foo 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor2() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor foo> 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor3() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author 1 +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor4() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> +0000\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor5() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b>\n");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor6() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> z");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidAuthor7() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> 1 z");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid author", e.getMessage());
		}
	}

	public void testInvalidCommitInvalidCommitter() {
		final StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author a <b> 1 +0000\n");
		b.append("committer a <");

		final byte[] data = Constants.encodeASCII(b.toString());
		try {
			checker.check(data);
			fail("Did not catch corrupt object");
		} catch (CorruptObjectException e) {
			// Yes, really, we complain about author not being
			// found as the invalid parent line wasn't consumed.
			assertEquals("invalid committer", e.getMessage());
		}
	}

}
