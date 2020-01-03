/*
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Utility for reading reflog entries
 */
class ReflogReaderImpl implements ReflogReader {
	private File logName;

	/**
	 * @param db
	 * @param refname
	 */
	ReflogReaderImpl(Repository db, String refname) {
		logName = new File(db.getDirectory(), Constants.LOGS + '/' + refname);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogReaader#getLastEntry()
	 */
	/** {@inheritDoc} */
	@Override
	public ReflogEntry getLastEntry() throws IOException {
		return getReverseEntry(0);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogReaader#getReverseEntries()
	 */
	/** {@inheritDoc} */
	@Override
	public List<ReflogEntry> getReverseEntries() throws IOException {
		return getReverseEntries(Integer.MAX_VALUE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogReaader#getReverseEntry(int)
	 */
	/** {@inheritDoc} */
	@Override
	public ReflogEntry getReverseEntry(int number) throws IOException {
		if (number < 0)
			throw new IllegalArgumentException();

		final byte[] log;
		try {
			log = IO.readFully(logName);
		} catch (FileNotFoundException e) {
			if (logName.exists()) {
				throw e;
			}
			return null;
		}

		int rs = RawParseUtils.prevLF(log, log.length);
		int current = 0;
		while (rs >= 0) {
			rs = RawParseUtils.prevLF(log, rs);
			if (number == current)
				return new ReflogEntryImpl(log, rs < 0 ? 0 : rs + 2);
			current++;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.internal.storage.file.ReflogReaader#getReverseEntries(int)
	 */
	/** {@inheritDoc} */
	@Override
	public List<ReflogEntry> getReverseEntries(int max) throws IOException {
		final byte[] log;
		try {
			log = IO.readFully(logName);
		} catch (FileNotFoundException e) {
			if (logName.exists()) {
				throw e;
			}
			return Collections.emptyList();
		}

		int rs = RawParseUtils.prevLF(log, log.length);
		List<ReflogEntry> ret = new ArrayList<>();
		while (rs >= 0 && max-- > 0) {
			rs = RawParseUtils.prevLF(log, rs);
			ReflogEntry entry = new ReflogEntryImpl(log, rs < 0 ? 0 : rs + 2);
			ret.add(entry);
		}
		return ret;
	}
}
