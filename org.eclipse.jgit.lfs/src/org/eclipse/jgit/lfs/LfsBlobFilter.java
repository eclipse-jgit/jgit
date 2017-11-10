/*
 * Copyright (C) 2017, Markus Duft <markus.duft@ssi-schaefer.com>
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
