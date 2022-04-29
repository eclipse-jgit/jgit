/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.LOCK_SUFFIX;
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
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
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
		return name + LOCK_SUFFIX;
	}

	private final RefDirectory refdb;

	private final boolean forceWrite;

	/**
	 * Create writer for ref directory.
	 *
	 * @param refdb
	 *            a {@link org.eclipse.jgit.internal.storage.file.RefDirectory}
	 *            object.
	 */
	public ReflogWriter(RefDirectory refdb) {
		this(refdb, false);
	}

	/**
	 * Create writer for ref directory.
	 *
	 * @param refdb
	 *            a {@link org.eclipse.jgit.internal.storage.file.RefDirectory}
	 *            object.
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
	 * @throws java.io.IOException
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
	 * NOTE: log entries associated with remote-tracking refs (refs/remotes/*)
	 * are not written to reflog.
	 *
	 * @param refName
	 *            a {@link java.lang.String} object.
	 * @param entry
	 *            a {@link org.eclipse.jgit.lib.ReflogEntry} object.
	 * @return this writer
	 * @throws java.io.IOException
	 */
	public ReflogWriter log(String refName, ReflogEntry entry)
			throws IOException {
		return log(refName, entry.getOldId(), entry.getNewId(), entry.getWho(),
				entry.getComment());
	}

	/**
	 * Write the given entry information to the ref's log
	 *
	 * NOTE: log entries associated with remote-tracking refs (refs/remotes/*)
	 * are not written to reflog.
	 *
	 * @param refName
	 *            ref name
	 * @param oldId
	 *            old object id
	 * @param newId
	 *            new object id
	 * @param ident
	 *            a {@link org.eclipse.jgit.lib.PersonIdent}
	 * @param message
	 *            reflog message
	 * @return this writer
	 * @throws java.io.IOException
	 */
	public ReflogWriter log(String refName, ObjectId oldId,
			ObjectId newId, PersonIdent ident, String message) throws IOException {
		byte[] encoded = encode(oldId, newId, ident, message);
		return log(refName, encoded);
	}

	/**
	 * Write the given ref update to the ref's log.
	 *
	 * NOTE: Updates associated with remote-tracking refs (refs/remotes/*) do
	 * not generate a reflog.
	 *
	 * @param update
	 *            a {@link org.eclipse.jgit.lib.RefUpdate}
	 * @param msg
	 *            reflog message
	 * @param deref
	 *            whether to dereference symbolic refs
	 * @return this writer
	 * @throws java.io.IOException
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

	private FileOutputStream getFileOutputStream(File log) throws IOException {
		try {
			return new FileOutputStream(log, true);
		} catch (FileNotFoundException err) {
			File dir = log.getParentFile();
			if (dir.exists()) {
				throw err;
			}
			if (!dir.mkdirs() && !dir.isDirectory()) {
				throw new IOException(MessageFormat
						.format(JGitText.get().cannotCreateDirectory, dir));
			}
			return new FileOutputStream(log, true);
		}
	}

	private ReflogWriter log(String refName, byte[] rec) throws IOException {
		if (refName.startsWith(R_REMOTES)) {
			return this;
		}

		File log = refdb.logFor(refName);
		boolean write = forceWrite
				|| shouldAutoCreateLog(refName)
				|| log.isFile();
		if (!write)
			return this;

		WriteConfig wc = refdb.getRepository().getConfig().get(WriteConfig.KEY);
		try (FileOutputStream out = getFileOutputStream(log)) {
			if (wc.getFSyncRefFiles()) {
				FileChannel fc = out.getChannel();
				ByteBuffer buf = ByteBuffer.wrap(rec);
				while (0 < buf.remaining()) {
					fc.write(buf);
				}
				fc.force(true);
			} else {
				out.write(rec);
			}
		}
		return this;
	}

	private boolean shouldAutoCreateLog(String refName) {
		Repository repo = refdb.getRepository();
		CoreConfig.LogRefUpdates value = repo.isBare()
				? CoreConfig.LogRefUpdates.FALSE
				: CoreConfig.LogRefUpdates.TRUE;
		value = repo.getConfig().getEnum(ConfigConstants.CONFIG_CORE_SECTION,
				null, ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, value);
		if (value != null) {
			switch (value) {
			case FALSE:
				break;
			case TRUE:
				return refName.equals(HEAD) || refName.startsWith(R_HEADS)
						|| refName.startsWith(R_REMOTES)
						|| refName.startsWith(R_NOTES);
			case ALWAYS:
				return refName.equals(HEAD) || refName.startsWith(R_REFS);
			default:
				break;
			}
		}
		return false;
	}
}