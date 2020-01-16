/*
 * Copyright (C) 2017, Markus Duft <markus.duft@ssi-schaefer.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;

/**
 * Provides transparently either a stream to the blob or a LFS media file if
 * managed by LFS.
 *
 * @since 4.11
 */
public class LfsBlobFilter {

	/**
	 * In case the given {@link ObjectLoader} points to a LFS pointer file
	 * replace the loader with one pointing to the LFS media file contents.
	 * Missing LFS files are downloaded on the fly - same logic as the smudge
	 * filter.
	 *
	 * @param db
	 *            the repo
	 * @param loader
	 *            the loader for the blob
	 * @return either the original loader, or a loader for the LFS media file if
	 *         managed by LFS. Files are downloaded on demand if required.
	 * @throws IOException
	 *             in case of an error
	 */
	public static ObjectLoader smudgeLfsBlob(Repository db, ObjectLoader loader)
			throws IOException {
		if (loader.getSize() > LfsPointer.SIZE_THRESHOLD) {
			return loader;
		}

		try (InputStream is = loader.openStream()) {
			LfsPointer ptr = LfsPointer.parseLfsPointer(is);
			if (ptr != null) {
				Lfs lfs = new Lfs(db);
				AnyLongObjectId oid = ptr.getOid();
				Path mediaFile = lfs.getMediaFile(oid);
				if (!Files.exists(mediaFile)) {
					SmudgeFilter.downloadLfsResource(lfs, db, ptr);
				}

				return new LfsBlobLoader(mediaFile);
			}
		}

		return loader;
	}

	/**
	 * Run the LFS clean filter on the given stream and return a stream to the
	 * LFS pointer file buffer. Used when inserting objects.
	 *
	 * @param db
	 *            the {@link Repository}
	 * @param originalContent
	 *            the {@link InputStream} to the original content
	 * @return a {@link TemporaryBuffer} representing the LFS pointer. The
	 *         caller is responsible to destroy the buffer.
	 * @throws IOException
	 *             in case of any error.
	 */
	public static TemporaryBuffer cleanLfsBlob(Repository db,
			InputStream originalContent) throws IOException {
		LocalFile buffer = new TemporaryBuffer.LocalFile(null);
		CleanFilter f = new CleanFilter(db, originalContent, buffer);
		try {
			while (f.run() != -1) {
				// loop as long as f.run() tells there is work to do
			}
		} catch (IOException e) {
			buffer.destroy();
			throw e;
		}
		return buffer;
	}

}
