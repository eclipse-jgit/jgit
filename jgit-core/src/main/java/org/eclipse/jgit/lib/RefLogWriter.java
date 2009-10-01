/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class to work with reflog files
 * 
 * @author Dave Watson
 */
public class RefLogWriter {
	static void append(final RefUpdate u, final String msg) throws IOException {
		final ObjectId oldId = u.getOldObjectId();
		final ObjectId newId = u.getNewObjectId();
		final Repository db = u.getRepository();
		final PersonIdent ident = u.getRefLogIdent();

		appendOneRecord(oldId, newId, ident, msg, db, u.getName());
		if (!u.getName().equals(u.getOrigName()))
			appendOneRecord(oldId, newId, ident, msg, db, u.getOrigName());
	}

	static void append(RefRename refRename, String logName, String msg) throws IOException {
		final ObjectId id = refRename.getObjectId();
		final Repository db = refRename.getRepository();
		final PersonIdent ident = refRename.getRefLogIdent();
		appendOneRecord(id, id, ident, msg, db, logName);
	}

	static void renameTo(final Repository db, final RefUpdate from,
			final RefUpdate to) throws IOException {
		final File logdir = new File(db.getDirectory(), Constants.LOGS);
		final File reflogFrom = new File(logdir, from.getName());
		if (!reflogFrom.exists())
			return;
		final File reflogTo = new File(logdir, to.getName());
		final File reflogToDir = reflogTo.getParentFile();
		File tmp = new File(logdir, "tmp-renamed-log.." + Thread.currentThread().getId());
		if (!reflogFrom.renameTo(tmp)) {
			throw new IOException("Cannot rename " + reflogFrom + " to (" + tmp
					+ ")" + reflogTo);
		}
		RefUpdate.deleteEmptyDir(reflogFrom, RefUpdate.count(from.getName(),
				'/'));
		if (!reflogToDir.exists() && !reflogToDir.mkdirs()) {
			throw new IOException("Cannot create directory " + reflogToDir);
		}
		if (!tmp.renameTo(reflogTo)) {
			throw new IOException("Cannot rename (" + tmp + ")" + reflogFrom
					+ " to " + reflogTo);
		}
	}

	private static void appendOneRecord(final ObjectId oldId,
			final ObjectId newId, PersonIdent ident, final String msg,
			final Repository db, final String refName) throws IOException {
		if (ident == null)
			ident = new PersonIdent(db);
		else
			ident = new PersonIdent(ident);

		final StringBuilder r = new StringBuilder();
		r.append(ObjectId.toString(oldId));
		r.append(' ');
		r.append(ObjectId.toString(newId));
		r.append(' ');
		r.append(ident.toExternalString());
		r.append('\t');
		r.append(msg);
		r.append('\n');

		final byte[] rec = Constants.encode(r.toString());
		final File logdir = new File(db.getDirectory(), Constants.LOGS);
		final File reflog = new File(logdir, refName);
		final File refdir = reflog.getParentFile();

		if (!refdir.exists() && !refdir.mkdirs())
			throw new IOException("Cannot create directory " + refdir);

		final FileOutputStream out = new FileOutputStream(reflog, true);
		try {
			out.write(rec);
		} finally {
			out.close();
		}
	}

	/**
	 * Writes reflog entry for ref specified by refName
	 * 
	 * @param repo
	 *            repository to use
	 * @param oldCommit
	 *            previous commit
	 * @param commit
	 *            new commit
	 * @param message
	 *            reflog message
	 * @param refName
	 *            full ref name
	 * @throws IOException
	 * @deprecated rely upon {@link RefUpdate}'s automatic logging instead.
	 */
	public static void writeReflog(Repository repo, ObjectId oldCommit,
			ObjectId commit, String message, String refName) throws IOException {
		appendOneRecord(oldCommit, commit, null, message, repo, refName);
	}
}
