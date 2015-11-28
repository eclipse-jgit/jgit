/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.internal.storage.reftree.RefTreeDb.MAX_SYMREF_DEPTH;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.FileMode.TYPE_GITLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_SYMLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.IOException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.RefList;

/** Scans a {@link RefTree} to build a {@link RefList}. */
class Scanner {
	private static final int MAX_SYMLINK_BYTES = 10 << 10;
	private static final byte[] BINARY_R_REFS = encode(R_REFS);
	private static final byte[] REFS_DOT_DOT = encode("refs/.."); //$NON-NLS-1$

	static class Result {
		final ObjectId id;
		final RefList<Ref> all;
		final RefList<Ref> sym;

		Result(ObjectId id, RefList<Ref> all, RefList<Ref> sym) {
			this.id = id;
			this.all = all;
			this.sym = sym;
		}
	}

	static Result scanRefTree(Repository repo, @Nullable Ref src,
			String prefix, boolean recursive)
					throws IOException, IncorrectObjectTypeException {
		RefList.Builder<Ref> all = new RefList.Builder<>();
		RefList.Builder<Ref> sym = new RefList.Builder<>();

		ObjectId srcId;
		if (src != null && src.getObjectId() != null) {
			try (ObjectReader reader = repo.newObjectReader()) {
				srcId = src.getObjectId();
				scan(reader, srcId, prefix, recursive, all, sym);
			}
		} else {
			srcId = ObjectId.zeroId();
		}

		RefList<Ref> aList = all.toRefList();
		for (int i = 0; i < sym.size(); i++) {
			sym.set(i, resolve(sym.get(i), 0, aList));
		}
		return new Result(srcId, aList, sym.toRefList());
	}

	private static void scan(ObjectReader reader, AnyObjectId srcId,
			String prefix, boolean recursive,
			RefList.Builder<Ref> all, RefList.Builder<Ref> sym)
					throws IncorrectObjectTypeException, IOException {
		CanonicalTreeParser p = createParserAtPath(reader, srcId, prefix);
		if (p == null) {
			return;
		}

		while (!p.eof()) {
			int mode = p.getEntryRawMode();
			if (mode == TYPE_TREE) {
				if (recursive) {
					p = p.createSubtreeIterator(reader);
				} else {
					p = p.next();
				}
				continue;
			}

			if (!isPeelSuffix(p)) {
				Ref r = toRef(reader, mode, p);
				if (r != null) {
					all.add(r);
					if (r.isSymbolic()) {
						sym.add(r);
					}
				}
			} else if (mode == TYPE_GITLINK) {
				peel(all, p);
			}
			p = p.next();
		}
	}

	private static CanonicalTreeParser createParserAtPath(ObjectReader reader,
			AnyObjectId srcId, String prefix) throws IOException {
		ObjectId root = toTree(reader, srcId);
		if (prefix.isEmpty()) {
			return new CanonicalTreeParser(BINARY_R_REFS, reader, root);
		}

		TreeWalk tw = TreeWalk.forPath(reader, prefix, root);
		if (tw == null || !tw.isSubtree()) {
			return null;
		}

		ObjectId id = tw.getObjectId(0);
		return new CanonicalTreeParser(encode(prefix), reader, id);
	}

	private static Ref resolve(Ref ref, int depth, RefList<Ref> refs)
			throws IOException {
		if (ref != null && ref.isSymbolic() && depth < MAX_SYMREF_DEPTH) {
			Ref r = refs.get(ref.getTarget().getName());
			Ref dst = resolve(r, depth + 1, refs);
			if (dst != null) {
				return new SymbolicRef(ref.getName(), dst);
			}
		}
		return ref;
	}

	@SuppressWarnings("resource")
	private static RevTree toTree(ObjectReader reader, AnyObjectId id)
			throws IOException {
		return new RevWalk(reader).parseTree(id);
	}

	private static boolean isPeelSuffix(CanonicalTreeParser t) {
		int n = t.getEntryPathLength();
		byte[] c = t.getEntryPathBuffer();
		return n > 3 && c[n - 3] == '^' && c[n - 2] == '{' && c[n - 1] == '}';
	}

	private static void peel(RefList.Builder<Ref> all, CanonicalTreeParser p) {
		String name = refName(p, true);
		for (int idx = all.size() - 1; 0 <= idx; idx--) {
			Ref r = all.get(idx);
			int cmp = r.getName().compareTo(name);
			if (cmp == 0) {
				all.set(idx, new ObjectIdRef.PeeledTag(PACKED, r.getName(),
						r.getObjectId(), p.getEntryObjectId()));
				break;
			} else if (cmp < 0) {
				// Stray peeled name without matching base name; skip entry.
				break;
			}
		}
	}

	private static Ref toRef(ObjectReader reader, int mode,
			CanonicalTreeParser p) throws IOException {
		if (mode == TYPE_GITLINK) {
			String name = refName(p, false);
			ObjectId id = p.getEntryObjectId();
			return new ObjectIdRef.PeeledNonTag(PACKED, name, id);

		} else if (mode == TYPE_SYMLINK) {
			ObjectId id = p.getEntryObjectId();
			byte[] bin = reader.open(id, OBJ_BLOB)
					.getCachedBytes(MAX_SYMLINK_BYTES);
			String dst = RawParseUtils.decode(bin);
			Ref trg = new ObjectIdRef.Unpeeled(NEW, dst, null);
			String name = refName(p, false);
			return new SymbolicRef(name, trg);
		}
		return null;
	}

	private static String refName(CanonicalTreeParser p, boolean peel) {
		byte[] buf = p.getEntryPathBuffer();
		int len = p.getEntryPathLength();
		if (peel) {
			len -= 3;
		}
		int ptr = 0;
		if (RawParseUtils.match(buf, ptr, REFS_DOT_DOT) > 0) {
			ptr = 7;
		}
		return RawParseUtils.decode(buf, ptr, len);
	}

	private Scanner() {
	}
}
