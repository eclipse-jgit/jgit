/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import static org.eclipse.jgit.lib.RefDatabase.MAX_SYMBOLIC_REF_DEPTH;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;

/**
 * Abstract table of references.
 */
public abstract class Reftable {
	/**
	 * References to convert into a reftable
	 *
	 * @param refs
	 *            references to convert into a reftable; may be empty.
	 * @return a reader for the supplied references.
	 */
	public static Reftable from(Collection<Ref> refs) {
		try {
			ReftableConfig cfg = new ReftableConfig();
			cfg.setIndexObjects(false);
			cfg.setAlignBlocks(false);
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			new ReftableWriter(buf)
				.setConfig(cfg)
				.begin()
				.sortAndWriteRefs(refs)
				.finish();
			return new ReftableReader(BlockSource.from(buf.toByteArray()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** {@code true} if deletions should be included in results. */
	protected boolean includeDeletes;

	/**
	 * Whether deleted references will be returned.
	 *
	 * @param deletes
	 *            if {@code true} deleted references will be returned. If
	 *            {@code false} (default behavior), deleted references will be
	 *            skipped, and not returned.
	 */
	public void setIncludeDeletes(boolean deletes) {
		includeDeletes = deletes;
	}


	/**
	 * Get the maximum update index for log entries that appear in this
	 * reftable.
	 *
	 * @return the maximum update index for log entries that appear in this
	 *         reftable. This should be 1 higher than the prior reftable's
	 *         {@code maxUpdateIndex} if this table is used in a stack.
	 * @throws java.io.IOException
	 *             file cannot be read.
	 */
	public abstract long maxUpdateIndex() throws IOException;

	/**
	 * Seek to the first reference, to iterate in order.
	 *
	 * @return cursor to iterate.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	public abstract RefCursor allRefs() throws IOException;

	/**
	 * Seek to a reference.
	 * <p>
	 * This method will seek to the reference {@code refName}. If present, the
	 * returned cursor will iterate exactly one entry. If not found, an empty
	 * cursor is returned.
	 *
	 * @param refName
	 *            reference name.
	 * @return cursor to iterate; empty cursor if no references match.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	public abstract RefCursor seekRef(String refName) throws IOException;

	/**
	 * Seek references with prefix.
	 * <p>
	 * The method will seek all the references starting with {@code prefix} as a
	 * prefix. If no references start with this prefix, an empty cursor is
	 * returned.
	 *
	 * @param prefix
	 *            prefix to find.
	 * @return cursor to iterate; empty cursor if no references match.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	public abstract RefCursor seekRefsWithPrefix(String prefix) throws IOException;

	/**
	 * Match references pointing to a specific object.
	 *
	 * @param id
	 *            object to find.
	 * @return cursor to iterate; empty cursor if no references match.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	public abstract RefCursor byObjectId(AnyObjectId id) throws IOException;

	/**
	 * Seek reader to read log records.
	 *
	 * @return cursor to iterate; empty cursor if no logs are present.
	 * @throws java.io.IOException
	 *             if logs cannot be read.
	 */
	public abstract LogCursor allLogs() throws IOException;

	/**
	 * Read a single reference's log.
	 *
	 * @param refName
	 *            exact name of the reference whose log to read.
	 * @return cursor to iterate; empty cursor if no logs match.
	 * @throws java.io.IOException
	 *             if logs cannot be read.
	 */
	public LogCursor seekLog(String refName) throws IOException {
		return seekLog(refName, Long.MAX_VALUE);
	}

	/**
	 * Seek to an update index in a reference's log.
	 *
	 * @param refName
	 *            exact name of the reference whose log to read.
	 * @param updateIndex
	 *            most recent index to return first in the log cursor. Log
	 *            records at or before {@code updateIndex} will be returned.
	 * @return cursor to iterate; empty cursor if no logs match.
	 * @throws java.io.IOException
	 *             if logs cannot be read.
	 */
	public abstract LogCursor seekLog(String refName, long updateIndex)
			throws IOException;

	/**
	 * Lookup a reference, or null if not found.
	 *
	 * @param refName
	 *            reference name to find.
	 * @return the reference, or {@code null} if not found.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	@Nullable
	public Ref exactRef(String refName) throws IOException {
		try (RefCursor rc = seekRef(refName)) {
			return rc.next() ? rc.getRef() : null;
		}
	}

	/**
	 * Test if a reference exists.
	 *
	 * @param refName
	 *            reference name or subtree to find.
	 * @return {@code true} if the reference exists.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	public boolean hasRef(String refName) throws IOException {
		try (RefCursor rc = seekRef(refName)) {
			return rc.next();
		}
	}

	/**
	 * Test if any reference starts with {@code prefix} as a prefix.
	 *
	 * @param prefix
	 *            prefix to find.
	 * @return {@code true} if at least one reference exists with prefix.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	public boolean hasRefsWithPrefix(String prefix) throws IOException {
		try (RefCursor rc = seekRefsWithPrefix(prefix)) {
			return rc.next();
		}
	}

	/**
	 * Test if any reference directly refers to the object.
	 *
	 * @param id
	 *            ObjectId to find.
	 * @return {@code true} if any reference exists directly referencing
	 *         {@code id}, or a annotated tag that peels to {@code id}.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	public boolean hasId(AnyObjectId id) throws IOException {
		try (RefCursor rc = byObjectId(id)) {
			return rc.next();
		}
	}

	/**
	 * Resolve a symbolic reference to populate its value.
	 *
	 * @param symref
	 *            reference to resolve.
	 * @return resolved {@code symref}, or {@code null}.
	 * @throws java.io.IOException
	 *             if references cannot be read.
	 */
	@Nullable
	public Ref resolve(Ref symref) throws IOException {
		return resolve(symref, 0);
	}

	private Ref resolve(Ref ref, int depth) throws IOException {
		if (!ref.isSymbolic()) {
			return ref;
		}

		Ref dst = ref.getTarget();
		if (MAX_SYMBOLIC_REF_DEPTH <= depth) {
			return null; // claim it doesn't exist
		}

		dst = exactRef(dst.getName());
		if (dst == null) {
			return ref;
		}

		dst = resolve(dst, depth + 1);
		if (dst == null) {
			return null; // claim it doesn't exist
		}
		return new SymbolicRef(ref.getName(), dst, ref.getUpdateIndex());
	}
}
