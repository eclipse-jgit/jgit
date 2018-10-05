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
import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.junit.JGitTestUtil.concat;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_BAD;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.Constants.encodeASCII;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.DUPLICATE_ENTRIES;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.EMPTY_NAME;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.FULL_PATHNAME;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.HAS_DOT;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.HAS_DOTDOT;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.HAS_DOTGIT;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.NULL_SHA1;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.TREE_NOT_SORTED;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.ZERO_PADDED_FILEMODE;
import static org.eclipse.jgit.util.RawParseUtils.decode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ObjectCheckerTest {
	private static final ObjectChecker SECRET_KEY_CHECKER = new ObjectChecker() {
		@Override
		public void checkBlob(byte[] raw) throws CorruptObjectException {
			String in = decode(raw);
			if (in.contains("secret_key")) {
				throw new CorruptObjectException("don't add a secret key");
			}
		}
	};

	private static final ObjectChecker SECRET_KEY_BLOB_CHECKER = new ObjectChecker() {
		@Override
		public BlobObjectChecker newBlobObjectChecker() {
			return new BlobObjectChecker() {
				private boolean containSecretKey;

				@Override
				public void update(byte[] in, int offset, int len) {
					String str = decode(in, offset, offset + len);
					if (str.contains("secret_key")) {
						containSecretKey = true;
					}
				}

				@Override
				public void endBlob(AnyObjectId id)
						throws CorruptObjectException {
					if (containSecretKey) {
						throw new CorruptObjectException(
								"don't add a secret key");
					}
				}
			};
		}
	};

	private ObjectChecker checker;

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Before
	public void setUp() throws Exception {
		checker = new ObjectChecker();
	}

	@Test
	public void testInvalidType() {
		String msg = MessageFormat.format(
				JGitText.get().corruptObjectInvalidType2,
				valueOf(OBJ_BAD));
		assertCorrupt(msg, OBJ_BAD, new byte[0]);
	}

	@Test
	public void testCheckBlob() throws CorruptObjectException {
		// Any blob should pass...
		checker.checkBlob(new byte[0]);
		checker.checkBlob(new byte[1]);

		checker.check(OBJ_BLOB, new byte[0]);
		checker.check(OBJ_BLOB, new byte[1]);
	}

	@Test
	public void testCheckBlobNotCorrupt() throws CorruptObjectException {
		SECRET_KEY_CHECKER.check(OBJ_BLOB, encodeASCII("key = \"public_key\""));
	}

	@Test
	public void testCheckBlobCorrupt() throws CorruptObjectException {
		thrown.expect(CorruptObjectException.class);
		SECRET_KEY_CHECKER.check(OBJ_BLOB, encodeASCII("key = \"secret_key\""));
	}

	@Test
	public void testCheckBlobWithBlobObjectCheckerNotCorrupt()
			throws CorruptObjectException {
		SECRET_KEY_BLOB_CHECKER.check(OBJ_BLOB,
				encodeASCII("key = \"public_key\""));
	}

	@Test
	public void testCheckBlobWithBlobObjectCheckerCorrupt()
			throws CorruptObjectException {
		thrown.expect(CorruptObjectException.class);
		SECRET_KEY_BLOB_CHECKER.check(OBJ_BLOB,
				encodeASCII("key = \"secret_key\""));
	}

	@Test
	public void testValidCommitNoParent() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommitBlankAuthor() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author <> 0 +0000\n");
		b.append("committer <> 0 +0000\n");

		byte[] data = encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(OBJ_COMMIT, data);
	}

	@Test
	public void testCommitCorruptAuthor() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree be9bfa841874ccc9f2ef7c48d0c76226f89b7189\n");
		b.append("author b <b@c> <b@c> 0 +0000\n");
		b.append("committer <> 0 +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad date", OBJ_COMMIT, data);
		checker.setAllowInvalidPersonIdent(true);
		checker.checkCommit(data);

		checker.setAllowInvalidPersonIdent(false);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testCommitCorruptCommitter() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree be9bfa841874ccc9f2ef7c48d0c76226f89b7189\n");
		b.append("author <> 0 +0000\n");
		b.append("committer b <b@c> <b@c> 0 +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad date", OBJ_COMMIT, data);
		checker.setAllowInvalidPersonIdent(true);
		checker.checkCommit(data);

		checker.setAllowInvalidPersonIdent(false);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommit1Parent() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommit2Parent() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();

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

		byte[] data = encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommit128Parent() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();

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

		byte[] data = encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(OBJ_COMMIT, data);
	}

	@Test
	public void testValidCommitNormalTime() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		String when = "1222757360 -0730";

		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');

		b.append("author A. U. Thor <author@localhost> " + when + "\n");
		b.append("committer A. U. Thor <author@localhost> " + when + "\n");

		byte[] data = encodeASCII(b.toString());
		checker.checkCommit(data);
		checker.check(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitNoTree1() {
		StringBuilder b = new StringBuilder();
		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("no tree header", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitNoTree2() {
		StringBuilder b = new StringBuilder();
		b.append("trie ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("no tree header", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitNoTree3() {
		StringBuilder b = new StringBuilder();
		b.append("tree");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("no tree header", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitNoTree4() {
		StringBuilder b = new StringBuilder();
		b.append("tree\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("no tree header", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidTree1() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("zzzzfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("invalid tree", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidTree2() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("z\n");
		assertCorrupt("invalid tree", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidTree3() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9b");
		b.append("\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid tree", OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidTree4() {
		StringBuilder b = new StringBuilder();
		b.append("tree  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("invalid tree", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidParent1() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("parent ");
		b.append("\n");
		assertCorrupt("invalid parent", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidParent2() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("parent ");
		b.append("zzzzfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");
		assertCorrupt("invalid parent", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidParent3() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("parent  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");
		assertCorrupt("invalid parent", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidParent4() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("parent  ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("z\n");
		assertCorrupt("invalid parent", OBJ_COMMIT, b);
	}

	@Test
	public void testInvalidCommitInvalidParent5() {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("parent\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append("\n");

		byte[] data = encodeASCII(b.toString());
		// Yes, really, we complain about author not being
		// found as the invalid parent line wasn't consumed.
		assertCorrupt("no author", OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitNoAuthor() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("committer A. U. Thor <author@localhost> 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("no author", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitNoCommitter1() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author A. U. Thor <author@localhost> 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("no committer", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitNoCommitter2() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author A. U. Thor <author@localhost> 1 +0000\n");
		b.append("\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("no committer", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidAuthor1()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author A. U. Thor <foo 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad email", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidAuthor2()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author A. U. Thor foo> 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("missing email", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidAuthor3()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("missing email", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidAuthor4()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author a <b> +0000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad date", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidAuthor5()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author a <b>\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad date", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidAuthor6()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author a <b> z");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad date", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidAuthor7()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author a <b> 1 z");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad time zone", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testInvalidCommitInvalidCommitter()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("author a <b> 1 +0000\n");
		b.append("committer a <");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad email", OBJ_COMMIT, data);
		assertSkipListAccepts(OBJ_COMMIT, data);
	}

	@Test
	public void testValidTag() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit\n");
		b.append("tag test-tag\n");
		b.append("tagger A. U. Thor <author@localhost> 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		checker.checkTag(data);
		checker.check(OBJ_TAG, data);
	}

	@Test
	public void testInvalidTagNoObject1() {
		assertCorrupt("no object header", OBJ_TAG, new byte[0]);
	}

	@Test
	public void testInvalidTagNoObject2() {
		StringBuilder b = new StringBuilder();
		b.append("object\t");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("no object header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoObject3() {
		StringBuilder b = new StringBuilder();
		b.append("obejct ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("no object header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoObject4() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("zz9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("invalid object", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoObject5() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append(" \n");
		assertCorrupt("invalid object", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoObject6() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9");
		assertCorrupt("invalid object", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoType1() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		assertCorrupt("no type header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoType2() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type\tcommit\n");
		assertCorrupt("no type header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoType3() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("tpye commit\n");
		assertCorrupt("no type header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoType4() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit");
		assertCorrupt("no tag header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoTagHeader1() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit\n");
		assertCorrupt("no tag header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoTagHeader2() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit\n");
		b.append("tag\tfoo\n");
		assertCorrupt("no tag header", OBJ_TAG, b);
	}

	@Test
	public void testInvalidTagNoTagHeader3() {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit\n");
		b.append("tga foo\n");
		assertCorrupt("no tag header", OBJ_TAG, b);
	}

	@Test
	public void testValidTagHasNoTaggerHeader() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit\n");
		b.append("tag foo\n");
		checker.checkTag(encodeASCII(b.toString()));
	}

	@Test
	public void testInvalidTagInvalidTaggerHeader1()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger \n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("missing email", OBJ_TAG, data);
		checker.setAllowInvalidPersonIdent(true);
		checker.checkTag(data);

		checker.setAllowInvalidPersonIdent(false);
		assertSkipListAccepts(OBJ_TAG, data);
	}

	@Test
	public void testInvalidTagInvalidTaggerHeader3()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("type commit\n");
		b.append("tag foo\n");
		b.append("tagger a < 1 +000\n");

		byte[] data = encodeASCII(b.toString());
		assertCorrupt("bad email", OBJ_TAG, data);
		assertSkipListAccepts(OBJ_TAG, data);
	}

	@Test
	public void testValidEmptyTree() throws CorruptObjectException {
		checker.checkTree(new byte[0]);
		checker.check(OBJ_TREE, new byte[0]);
	}

	@Test
	public void testValidTree1() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 regular-file");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTree2() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100755 executable");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTree3() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "40000 tree");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTree4() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "120000 symlink");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTree5() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "160000 git link");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTree6() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .a");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeWithGitmodules() throws CorruptObjectException {
		ObjectId treeId = ObjectId
				.fromString("0123012301230123012301230123012301230123");
		StringBuilder b = new StringBuilder();
		ObjectId blobId = entry(b, "100644 .gitmodules");

		byte[] data = encodeASCII(b.toString());
		checker.checkTree(treeId, data);
		assertEquals(1, checker.getGitsubmodules().size());
		assertEquals(treeId, checker.getGitsubmodules().get(0).getTreeId());
		assertEquals(blobId, checker.getGitsubmodules().get(0).getBlobId());
	}

	/*
	 * Windows case insensitivity and long file name handling
	 * means that .gitmodules has many synonyms.
	 *
	 * Examples inspired by git.git's t/t0060-path-utils.sh, by
	 * Johannes Schindelin and Congyi Wu.
	 */
	@Test
	public void testNTFSGitmodules() throws CorruptObjectException {
		for (String gitmodules : new String[] {
			".GITMODULES",
			".gitmodules",
			".Gitmodules",
			".gitmoduleS",
			"gitmod~1",
			"GITMOD~1",
			"gitmod~4",
			"GI7EBA~1",
			"gi7eba~9",
			"GI7EB~10",
			"GI7E~123",
			"~1000000",
			"~9999999"
		}) {
			checker = new ObjectChecker(); // Reset the ObjectChecker state.
			checker.setSafeForWindows(true);
			ObjectId treeId = ObjectId
					.fromString("0123012301230123012301230123012301230123");
			StringBuilder b = new StringBuilder();
			ObjectId blobId = entry(b, "100644 " + gitmodules);

			byte[] data = encodeASCII(b.toString());
			checker.checkTree(treeId, data);
			assertEquals(1, checker.getGitsubmodules().size());
			assertEquals(treeId, checker.getGitsubmodules().get(0).getTreeId());
			assertEquals(blobId, checker.getGitsubmodules().get(0).getBlobId());
		}
	}

	@Test
	public void testNotGitmodules() throws CorruptObjectException {
		for (String notGitmodules : new String[] {
			".gitmodu",
			".gitmodules oh never mind",
		}) {
			checker = new ObjectChecker(); // Reset the ObjectChecker state.
			checker.setSafeForWindows(true);
			ObjectId treeId = ObjectId
					.fromString("0123012301230123012301230123012301230123");
			StringBuilder b = new StringBuilder();
			entry(b, "100644 " + notGitmodules);

			byte[] data = encodeASCII(b.toString());
			checker.checkTree(treeId, data);
			assertEquals(0, checker.getGitsubmodules().size());
		}
	}

	/*
	 * TODO HFS: match ".gitmodules" case-insensitively, after stripping out
	 * certain zero-length Unicode code points that HFS+ strips out
	 */

	@Test
	public void testValidTreeWithGitmodulesUppercase()
			throws CorruptObjectException {
		ObjectId treeId = ObjectId
				.fromString("0123012301230123012301230123012301230123");
		StringBuilder b = new StringBuilder();
		ObjectId blobId = entry(b, "100644 .GITMODULES");

		byte[] data = encodeASCII(b.toString());
		checker.setSafeForWindows(true);
		checker.checkTree(treeId, data);
		assertEquals(1, checker.getGitsubmodules().size());
		assertEquals(treeId, checker.getGitsubmodules().get(0).getTreeId());
		assertEquals(blobId, checker.getGitsubmodules().get(0).getBlobId());
	}

	@Test
	public void testTreeWithInvalidGitmodules() throws CorruptObjectException {
		ObjectId treeId = ObjectId
				.fromString("0123012301230123012301230123012301230123");
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .gitmodulez");

		byte[] data = encodeASCII(b.toString());
		checker.checkTree(treeId, data);
		checker.setSafeForWindows(true);
		assertEquals(0, checker.getGitsubmodules().size());
	}

	@Test
	public void testNullSha1InTreeEntry() throws CorruptObjectException {
		byte[] data = concat(
				encodeASCII("100644 A"), new byte[] { '\0' },
				new byte[OBJECT_ID_LENGTH]);
		assertCorrupt("entry points to null SHA-1", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(NULL_SHA1, true);
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
		StringBuilder b = new StringBuilder();
		entry(b, "100644 fooaaa");
		entry(b, "100755 foobar");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeSorting2() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100755 fooaaa");
		entry(b, "100644 foobar");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeSorting3() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 b");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeSorting4() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 b");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeSorting5() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a.c");
		entry(b, "40000 a");
		entry(b, "100644 a0c");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeSorting6() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 apple");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeSorting7() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "40000 an orang");
		entry(b, "40000 an orange");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testValidTreeSorting8() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a0c");
		entry(b, "100644 b");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testAcceptTreeModeWithZero() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "040000 a");
		byte[] data = encodeASCII(b.toString());
		checker.setAllowLeadingZeroFileMode(true);
		checker.checkTree(data);

		checker.setAllowLeadingZeroFileMode(false);
		assertSkipListAccepts(OBJ_TREE, data);

		checker.setIgnore(ZERO_PADDED_FILEMODE, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeModeStartsWithZero1() {
		StringBuilder b = new StringBuilder();
		entry(b, "0 a");
		assertCorrupt("mode starts with '0'", OBJ_TREE, b);
	}

	@Test
	public void testInvalidTreeModeStartsWithZero2() {
		StringBuilder b = new StringBuilder();
		entry(b, "0100644 a");
		assertCorrupt("mode starts with '0'", OBJ_TREE, b);
	}

	@Test
	public void testInvalidTreeModeStartsWithZero3() {
		StringBuilder b = new StringBuilder();
		entry(b, "040000 a");
		assertCorrupt("mode starts with '0'", OBJ_TREE, b);
	}

	@Test
	public void testInvalidTreeModeNotOctal1() {
		StringBuilder b = new StringBuilder();
		entry(b, "8 a");
		assertCorrupt("invalid mode character", OBJ_TREE, b);
	}

	@Test
	public void testInvalidTreeModeNotOctal2() {
		StringBuilder b = new StringBuilder();
		entry(b, "Z a");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid mode character", OBJ_TREE, data);
		assertSkipListRejects("invalid mode character", OBJ_TREE, data);
	}

	@Test
	public void testInvalidTreeModeNotSupportedMode1() {
		StringBuilder b = new StringBuilder();
		entry(b, "1 a");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid mode 1", OBJ_TREE, data);
		assertSkipListRejects("invalid mode 1", OBJ_TREE, data);
	}

	@Test
	public void testInvalidTreeModeNotSupportedMode2() {
		StringBuilder b = new StringBuilder();
		entry(b, "170000 a");
		assertCorrupt("invalid mode " + 0170000, OBJ_TREE, b);
	}

	@Test
	public void testInvalidTreeModeMissingName() {
		StringBuilder b = new StringBuilder();
		b.append("100644");
		assertCorrupt("truncated in mode", OBJ_TREE, b);
	}

	@Test
	public void testInvalidTreeNameContainsSlash()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a/b");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("name contains '/'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(FULL_PATHNAME, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsEmpty() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 ");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("zero length name", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(EMPTY_NAME, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDot() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '.'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotDot() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 ..");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '..'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTDOT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsGit() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '.git'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsMixedCaseGit()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .GiT");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '.GiT'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsMacHFSGit() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .gi\u200Ct");
		byte[] data = encode(b.toString());

		// Fine on POSIX.
		checker.checkTree(data);

		// Rejected on Mac OS.
		checker.setSafeForMacOS(true);
		assertCorrupt(
				"invalid name '.gi\u200Ct' contains ignorable Unicode characters",
				OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsMacHFSGit2()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 \u206B.git");
		byte[] data = encode(b.toString());

		// Fine on POSIX.
		checker.checkTree(data);

		// Rejected on Mac OS.
		checker.setSafeForMacOS(true);
		assertCorrupt(
				"invalid name '\u206B.git' contains ignorable Unicode characters",
				OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsMacHFSGit3()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git\uFEFF");
		byte[] data = encode(b.toString());

		// Fine on POSIX.
		checker.checkTree(data);

		// Rejected on Mac OS.
		checker.setSafeForMacOS(true);
		assertCorrupt(
				"invalid name '.git\uFEFF' contains ignorable Unicode characters",
				OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}



	@Test
	public void testInvalidTreeNameIsMacHFSGitCorruptUTF8AtEnd()
			throws CorruptObjectException {
		byte[] data = concat(encode("100644 .git"),
				new byte[] { (byte) 0xef });
		StringBuilder b = new StringBuilder();
		entry(b, "");
		data = concat(data, encode(b.toString()));

		// Fine on POSIX.
		checker.checkTree(data);

		// Rejected on Mac OS.
		checker.setSafeForMacOS(true);
		assertCorrupt(
				"invalid name contains byte sequence '0xef' which is not a valid UTF-8 character",
				OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
	}

	@Test
	public void testInvalidTreeNameIsMacHFSGitCorruptUTF8AtEnd2()
			throws CorruptObjectException {
		byte[] data = concat(encode("100644 .git"),
				new byte[] {
				(byte) 0xe2, (byte) 0xab });
		StringBuilder b = new StringBuilder();
		entry(b, "");
		data = concat(data, encode(b.toString()));

		// Fine on POSIX.
		checker.checkTree(data);

		// Rejected on Mac OS.
		checker.setSafeForMacOS(true);
		assertCorrupt(
				"invalid name contains byte sequence '0xe2ab' which is not a valid UTF-8 character",
				OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
	}

	@Test
	public void testInvalidTreeNameIsNotMacHFSGit()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git\u200Cx");
		byte[] data = encode(b.toString());
		checker.setSafeForMacOS(true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsNotMacHFSGit2()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .kit\u200C");
		byte[] data = encode(b.toString());
		checker.setSafeForMacOS(true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsNotMacHFSGitOtherPlatform()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git\u200C");
		byte[] data = encode(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotGitDot() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git.");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '.git.'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeNameIsDotGitDotDot()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git..");
		checker.checkTree(encodeASCII(b.toString()));
	}

	@Test
	public void testInvalidTreeNameIsDotGitSpace()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git ");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '.git '", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotGitSomething()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .gitfoobar");
		byte[] data = encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotGitSomethingSpaceSomething()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .gitfoo bar");
		byte[] data = encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotGitSomethingDot()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .gitfoobar.");
		byte[] data = encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotGitSomethingDotDot()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .gitfoobar..");
		byte[] data = encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotGitDotSpace()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git. ");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '.git. '", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsDotGitSpaceDot()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 .git . ");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name '.git . '", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsGITTilde1() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 GIT~1");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name 'GIT~1'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeNameIsGiTTilde1() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 GiT~1");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("invalid name 'GiT~1'", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(HAS_DOTGIT, true);
		checker.checkTree(data);
	}

	@Test
	public void testValidTreeNameIsGitTilde11() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 GIT~11");
		byte[] data = encodeASCII(b.toString());
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeTruncatedInName() {
		StringBuilder b = new StringBuilder();
		b.append("100644 b");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("truncated in name", OBJ_TREE, data);
		assertSkipListRejects("truncated in name", OBJ_TREE, data);
	}

	@Test
	public void testInvalidTreeTruncatedInObjectId() {
		StringBuilder b = new StringBuilder();
		b.append("100644 b\0\1\2");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("truncated in object id", OBJ_TREE, data);
		assertSkipListRejects("truncated in object id", OBJ_TREE, data);
	}

	@Test
	public void testInvalidTreeBadSorting1() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 foobar");
		entry(b, "100644 fooaaa");
		byte[] data = encodeASCII(b.toString());

		assertCorrupt("incorrectly sorted", OBJ_TREE, data);

		ObjectId id = idFor(OBJ_TREE, data);
		try {
			checker.check(id, OBJ_TREE, data);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException e) {
			assertSame(TREE_NOT_SORTED, e.getErrorType());
			assertEquals("treeNotSorted: object " + id.name()
					+ ": incorrectly sorted", e.getMessage());
		}

		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(TREE_NOT_SORTED, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeBadSorting2() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "100644 a.c");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("incorrectly sorted", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(TREE_NOT_SORTED, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeBadSorting3() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a0c");
		entry(b, "40000 a");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("incorrectly sorted", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(TREE_NOT_SORTED, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames1_File()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames1_Tree()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "40000 a");
		entry(b, "40000 a");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames2() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100755 a");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames3() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "40000 a");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames4() throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 a");
		entry(b, "100644 a.c");
		entry(b, "100644 a.d");
		entry(b, "100644 a.e");
		entry(b, "40000 a");
		entry(b, "100644 zoo");
		byte[] data = encodeASCII(b.toString());
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames5()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 A");
		entry(b, "100644 a");
		byte[] data = b.toString().getBytes(CHARSET);
		checker.setSafeForWindows(true);
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames6()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 A");
		entry(b, "100644 a");
		byte[] data = b.toString().getBytes(CHARSET);
		checker.setSafeForMacOS(true);
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames7()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 \u0065\u0301");
		entry(b, "100644 \u00e9");
		byte[] data = b.toString().getBytes(CHARSET);
		checker.setSafeForMacOS(true);
		assertCorrupt("duplicate entry names", OBJ_TREE, data);
		assertSkipListAccepts(OBJ_TREE, data);
		checker.setIgnore(DUPLICATE_ENTRIES, true);
		checker.checkTree(data);
	}

	@Test
	public void testInvalidTreeDuplicateNames8()
			throws CorruptObjectException {
		StringBuilder b = new StringBuilder();
		entry(b, "100644 A");
		checker.setSafeForMacOS(true);
		checker.checkTree(b.toString().getBytes(CHARSET));
	}

	@Test
	public void testRejectNulInPathSegment() {
		try {
			checker.checkPathSegment(encodeASCII("a\u0000b"), 0, 3);
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
	public void testBug477090() throws CorruptObjectException {
		checker.setSafeForMacOS(true);
		final byte[] bytes = {
				// U+221E 0xe2889e INFINITY ∞
				(byte) 0xe2, (byte) 0x88, (byte) 0x9e,
				// .html
				0x2e, 0x68, 0x74, 0x6d, 0x6c };
		checker.checkPathSegment(bytes, 0, bytes.length);
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
		checker.checkTree(encodeASCII(b.toString()));
	}

	/*
	 * Returns the id generated for the entry
	 */
	private static ObjectId entry(StringBuilder b, String modeName) {
		byte[] id = new byte[OBJECT_ID_LENGTH];

		b.append(modeName);
		b.append('\0');
		for (int i = 0; i < OBJECT_ID_LENGTH; i++) {
			b.append((char) i);
			id[i] = (byte) i;
		}

		return ObjectId.fromRaw(id);
	}

	private void assertCorrupt(String msg, int type, StringBuilder b) {
		assertCorrupt(msg, type, encodeASCII(b.toString()));
	}

	private void assertCorrupt(String msg, int type, byte[] data) {
		try {
			checker.check(type, data);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException e) {
			assertEquals(msg, e.getMessage());
		}
	}

	private void assertSkipListAccepts(int type, byte[] data)
			throws CorruptObjectException {
		ObjectId id = idFor(type, data);
		checker.setSkipList(set(id));
		checker.check(id, type, data);
		checker.setSkipList(null);
	}

	private void assertSkipListRejects(String msg, int type, byte[] data) {
		ObjectId id = idFor(type, data);
		checker.setSkipList(set(id));
		try {
			checker.check(id, type, data);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException e) {
			assertEquals(msg, e.getMessage());
		}
		checker.setSkipList(null);
	}

	private static ObjectIdSet set(ObjectId... ids) {
		return new ObjectIdSet() {
			@Override
			public boolean contains(AnyObjectId objectId) {
				for (ObjectId id : ids) {
					if (id.equals(objectId)) {
						return true;
					}
				}
				return false;
			}
		};
	}

	@SuppressWarnings("resource")
	private static ObjectId idFor(int type, byte[] raw) {
		return new ObjectInserter.Formatter().idFor(type, raw);
	}
}
