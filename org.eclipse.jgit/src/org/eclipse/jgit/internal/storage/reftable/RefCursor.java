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

import java.io.IOException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;

/** Iterator over references. */
public abstract class RefCursor implements AutoCloseable {
	/** {@code true} if deletions should be included in results. */
	protected boolean includeDeletes;

	/**
	 * @param deletes
	 *            if {@code true} deleted references will be returned. If
	 *            {@code false} (default behavior), deleted references will be
	 *            skipped, and not returned.
	 */
	public void setIncludeDeletes(boolean deletes) {
		includeDeletes = deletes;
	}

	/**
	 * Seek to the first reference, to iterate in order.
	 *
	 * @throws IOException
	 *             references cannot be read.
	 */
	public abstract void seekToFirstRef() throws IOException;

	/**
	 * Seek either to a reference, or a reference subtree.
	 * <p>
	 * If {@code refName} ends with {@code "/"} the method will seek to the
	 * subtree of all references starting with {@code refName} as a prefix.
	 * <p>
	 * Otherwise, only {@code refName} will be found, if present.
	 *
	 * @param refName
	 *            reference name or subtree to find.
	 * @throws IOException
	 *             references cannot be read.
	 */
	public abstract void seek(String refName) throws IOException;

	/**
	 * Seek reader to read log records.
	 *
	 * @throws IOException
	 *             logs cannot be read.
	 */
	public abstract void seekToFirstLog() throws IOException;

	/**
	 * Seek to a timestamp in a reference's log.
	 *
	 * @param refName
	 *            exact name of the reference whose log to read.
	 * @param time
	 *            time in seconds since the epoch to scan from. Records at this
	 *            time and older will be returned.
	 * @throws IOException
	 *             logs cannot be read.
	 */
	public abstract void seekLog(String refName, int time) throws IOException;

	/**
	 * Check if another reference or log record is available.
	 *
	 * @return {@code true} if there is another result.
	 * @throws IOException
	 *             references cannot be read.
	 */
	public abstract boolean next() throws IOException;

	/** @return name of the current reference. */
	public abstract String getRefName();

	/** @return reference at the current position,s if scanning references. */
	public abstract Ref getRef();

	/** @return {@code true} if the current reference was deleted. */
	public boolean wasDeleted() {
		Ref r = getRef();
		return r.getStorage() == Ref.Storage.NEW && r.getObjectId() == null;
	}

	/** @return current log entry, if scanning the log. */
	public abstract ReflogEntry getReflogEntry();

	/**
	 * Lookup a reference, or null if not found.
	 *
	 * @param refName
	 *            reference name to find.
	 * @return the reference, or {@code null} if not found.
	 * @throws IOException
	 *             references cannot be read.
	 */
	@Nullable
	public Ref exactRef(String refName) throws IOException {
		seek(refName);
		return next() ? getRef() : null;
	}

	/**
	 * Test if a reference or reference subtree exists.
	 * <p>
	 * If {@code refName} ends with {@code "/"}, the method tests if any
	 * reference starts with {@code refName} as a prefix.
	 * <p>
	 * Otherwise, the method checks if {@code refName} exists.
	 *
	 * @param refName
	 *            reference name or subtree to find.
	 * @return {@code true} if the reference exists, or at least one reference
	 *         exists in the subtree.
	 * @throws IOException
	 *             references cannot be read.
	 */
	public boolean hasRef(String refName) throws IOException {
		seek(refName);
		return next();
	}

	@Override
	public abstract void close() throws IOException;
}
