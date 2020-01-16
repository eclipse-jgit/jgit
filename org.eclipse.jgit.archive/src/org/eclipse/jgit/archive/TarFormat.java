/*
 * Copyright (C) 2012 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.archive;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.archive.internal.ArchiveText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;


/**
 * Unix TAR format (ustar + some PAX extensions).
 */
public final class TarFormat extends BaseFormat implements
		ArchiveCommand.Format<ArchiveOutputStream> {
	private static final List<String> SUFFIXES = Collections
			.unmodifiableList(Arrays.asList(".tar")); //$NON-NLS-1$

	/** {@inheritDoc} */
	@Override
	public ArchiveOutputStream createArchiveOutputStream(OutputStream s)
			throws IOException {
		return createArchiveOutputStream(s,
				Collections.<String, Object> emptyMap());
	}

	/** {@inheritDoc} */
	@Override
	public ArchiveOutputStream createArchiveOutputStream(OutputStream s,
			Map<String, Object> o) throws IOException {
		TarArchiveOutputStream out = new TarArchiveOutputStream(s,
				UTF_8.name());
		out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
		out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
		return applyFormatOptions(out, o);
	}

	/** {@inheritDoc} */
	@Override
	public void putEntry(ArchiveOutputStream out,
			ObjectId tree, String path, FileMode mode, ObjectLoader loader)
			throws IOException {
		if (mode == FileMode.SYMLINK) {
			final TarArchiveEntry entry = new TarArchiveEntry(
					path, TarConstants.LF_SYMLINK);
			entry.setLinkName(new String(loader.getCachedBytes(100), UTF_8));
			out.putArchiveEntry(entry);
			out.closeArchiveEntry();
			return;
		}

		// TarArchiveEntry detects directories by checking
		// for '/' at the end of the filename.
		if (path.endsWith("/") && mode != FileMode.TREE) //$NON-NLS-1$
			throw new IllegalArgumentException(MessageFormat.format(
					ArchiveText.get().pathDoesNotMatchMode, path, mode));
		if (!path.endsWith("/") && mode == FileMode.TREE) //$NON-NLS-1$
			path = path + "/"; //$NON-NLS-1$

		final TarArchiveEntry entry = new TarArchiveEntry(path);

		if (tree instanceof RevCommit) {
			long t = ((RevCommit) tree).getCommitTime() * 1000L;
			entry.setModTime(t);
		}

		if (mode == FileMode.TREE) {
			out.putArchiveEntry(entry);
			out.closeArchiveEntry();
			return;
		}

		if (mode == FileMode.REGULAR_FILE) {
			// ok
		} else if (mode == FileMode.EXECUTABLE_FILE) {
			entry.setMode(mode.getBits());
		} else {
			// Unsupported mode (e.g., GITLINK).
			throw new IllegalArgumentException(MessageFormat.format(
					ArchiveText.get().unsupportedMode, mode));
		}
		entry.setSize(loader.getSize());
		out.putArchiveEntry(entry);
		loader.copyTo(out);
		out.closeArchiveEntry();
	}

	/** {@inheritDoc} */
	@Override
	public Iterable<String> suffixes() {
		return SUFFIXES;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object other) {
		return (other instanceof TarFormat);
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
