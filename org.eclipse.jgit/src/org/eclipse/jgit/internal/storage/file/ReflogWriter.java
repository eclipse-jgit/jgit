/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
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
package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_NOTES;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.util.FileUtils;

/**
 * Utility for writing reflog entries using the traditional one-file-per-log
 * format.
 */
public class ReflogWriter {

	/**
	 * Get the ref name to be used for when locking a ref's log for rewriting.
	 *
	 * @param name
	 *            name of the ref, relative to the Git repository top level
	 *            directory (so typically starts with refs/).
	 * @return the name of the ref's lock ref.
	 */
	public static String refLockFor(String name) {
		return name + LockFile.SUFFIX;
	}

	private final RefDirectory refdb;

	private final boolean forceWrite;

	/**
	 * Create writer for ref directory.
	 *
	 * @param refdb
	 */
	public ReflogWriter(RefDirectory refdb) {
		this(refdb, false);
	}

	/**
	 * Create writer for ref directory.
	 *
	 * @param refdb
	 * @param forceWrite
	 *            true to write to disk all entries logged, false to respect the
	 *            repository's config and current log file status.
	 */
	public ReflogWriter(RefDirectory refdb, boolean forceWrite) {
		this.refdb = refdb;
		this.forceWrite = forceWrite;
	}

	/**
	 * Create the log directories.
	 *
	 * @throws IOException
	 * @return this writer.
	 */
	public ReflogWriter create() throws IOException {
		FileUtils.mkdir(refdb.logsDir);
		FileUtils.mkdir(refdb.logsRefsDir);
		FileUtils.mkdir(
				new File(refdb.logsRefsDir, R_HEADS.substring(R_REFS.length())));
		return this;
	}

	/**
	 * Write the given entry to the ref's log.
	 *
	 * @param refName
	 * @param entry
	 * @return this writer
	 * @throws IOException
	 */
	public ReflogWriter log(String refName, ReflogEntry entry)
			throws IOException {
		return log(refName, entry.getOldId(), entry.getNewId(), entry.getWho(),
				entry.getComment());
	}

	/**
	 * Write the given entry information to the ref's log
	 *
	 * @param refName
	 * @param oldId
	 * @param newId
	 * @param ident
	 * @param message
	 * @return this writer
	 * @throws IOException
	 */
	public ReflogWriter log(String refName, ObjectId oldId,
			ObjectId newId, PersonIdent ident, String message) throws IOException {
		byte[] encoded = encode(oldId, newId, ident, message);
		return log(refName, encoded);
	}

	/**
	 * Write the given ref update to the ref's log.
	 *
	 * @param update
	 * @param msg
	 * @param deref
	 * @return this writer
	 * @throws IOException
	 */
	public ReflogWriter log(RefUpdate update, String msg,
			boolean deref) throws IOException {
		ObjectId oldId = update.getOldObjectId();
		ObjectId newId = update.getNewObjectId();
		Ref ref = update.getRef();

		PersonIdent ident = update.getRefLogIdent();
		if (ident == null)
			ident = new PersonIdent(refdb.getRepository());
		else
			ident = new PersonIdent(ident);

		byte[] rec = encode(oldId, newId, ident, msg);
		if (deref && ref.isSymbolic()) {
			log(ref.getName(), rec);
			log(ref.getLeaf().getName(), rec);
		} else
			log(ref.getName(), rec);

		return this;
	}

	private byte[] encode(ObjectId oldId, ObjectId newId, PersonIdent ident,
			String message) {
		StringBuilder r = new StringBuilder();
		r.append(ObjectId.toString(oldId));
		r.append(' ');
		r.append(ObjectId.toString(newId));
		r.append(' ');
		r.append(ident.toExternalString());
		r.append('\t');
		r.append(
				message.replace("\r\n", " ") //$NON-NLS-1$ //$NON-NLS-2$
						.replace("\n", " ")); //$NON-NLS-1$ //$NON-NLS-2$
		r.append('\n');
		return Constants.encode(r.toString());
	}

	private ReflogWriter log(String refName, byte[] rec) throws IOException {
		File log = refdb.logFor(refName);
		boolean write = forceWrite
				|| (isLogAllRefUpdates() && shouldAutoCreateLog(refName))
				|| log.isFile();
		if (!write)
			return this;

		WriteConfig wc = refdb.getRepository().getConfig().get(WriteConfig.KEY);
		FileOutputStream out;
		try {
			out = new FileOutputStream(log, true);
		} catch (FileNotFoundException err) {
			File dir = log.getParentFile();
			if (dir.exists())
				throw err;
			if (!dir.mkdirs() && !dir.isDirectory())
				throw new IOException(MessageFormat.format(
						JGitText.get().cannotCreateDirectory, dir));
			out = new FileOutputStream(log, true);
		}
		try {
			if (wc.getFSyncRefFiles()) {
				FileChannel fc = out.getChannel();
				ByteBuffer buf = ByteBuffer.wrap(rec);
				while (0 < buf.remaining())
					fc.write(buf);
				fc.force(true);
			} else
				out.write(rec);
		} finally {
			out.close();
		}
		return this;
	}

	private boolean isLogAllRefUpdates() {
		return refdb.getRepository().getConfig().get(CoreConfig.KEY)
				.isLogAllRefUpdates();
	}

	private boolean shouldAutoCreateLog(String refName) {
		return refName.equals(HEAD)
				|| refName.startsWith(R_HEADS)
				|| refName.startsWith(R_REMOTES)
				|| refName.startsWith(R_NOTES);
	}
}
