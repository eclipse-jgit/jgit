/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;

import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.lfs.errors.CorruptMediaFile;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * Built-in LFS clean filter
 *
 * When new content is about to be added to the git repository and this filter
 * is configured for that content, then this filter will replace the original
 * content with content of a so-called LFS pointer file. The pointer file
 * content will then be added to the git repository. Additionally this filter
 * writes the original content in a so-called 'media file' to '.git/lfs/objects/
 * <first-two-characters-of-contentid>/<rest-of-contentid>'
 *
 * @see <a href="https://github.com/github/git-lfs/blob/master/docs/spec.md">Git
 *      LFS Specification</a>
 * @since 4.6
 */
public class CleanFilter extends FilterCommand {
	/**
	 * The factory is responsible for creating instances of {@link CleanFilter}
	 */
	public final static FilterCommandFactory FACTORY = new FilterCommandFactory() {

		@Override
		public FilterCommand create(Repository db, InputStream in,
				OutputStream out) throws IOException {
			return new CleanFilter(db, in, out);
		}
	};

	/**
	 * Registers this filter by calling
	 * {@link FilterCommandRegistry#register(String, FilterCommandFactory)}
	 */
	public final static void register() {
		FilterCommandRegistry.register(
				org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
						+ "lfs/clean", //$NON-NLS-1$
				FACTORY);
	}

	// The OutputStream to a temporary file which will be renamed to mediafile
	// when the operation succeeds
	private OutputStream tmpOut;

	// Used to compute the hash for the original content
	private DigestOutputStream dOut;

	private Lfs lfsUtil;

	// the size of the original content
	private long size;

	// a temporary file into which the original content is written. When no
	// errors occur this file will be renamed to the mediafile
	private Path tmpFile;

	/**
	 * @param db
	 *            the repository
	 * @param in
	 *            an {@link InputStream} providing the original content
	 * @param out
	 *            the {@link OutputStream} into which the content of the pointer
	 *            file should be written. That's the content which will be added
	 *            to the git repository
	 * @throws IOException
	 *             when the creation of the temporary file fails or when no
	 *             {@link OutputStream} for this file can be created
	 */
	public CleanFilter(Repository db, InputStream in, OutputStream out)
			throws IOException {
		super(in, out);
		lfsUtil = new Lfs(db);
		Files.createDirectories(lfsUtil.getLfsTmpDir());
		tmpFile = lfsUtil.createTmpFile();
		tmpOut = Files.newOutputStream(tmpFile,
				StandardOpenOption.CREATE);
		this.dOut = new DigestOutputStream(
				tmpOut,
				Constants.newMessageDigest());
	}

	public int run() throws IOException {
		try {
			byte[] buf = new byte[8192];
			int length = in.read(buf);
			if (length != -1) {
				dOut.write(buf, 0, length);
				size += length;
				return length;
			} else {
				dOut.close();
				tmpOut.close();
				LongObjectId loid = LongObjectId
						.fromRaw(dOut.getMessageDigest().digest());
				Path mediaFile = lfsUtil.getMediaFile(loid);
				if (Files.isRegularFile(mediaFile)) {
					long fsSize = Files.size(mediaFile);
					if (fsSize != size) {
						throw new CorruptMediaFile(mediaFile, size, fsSize);
					}
				} else {
					FileUtils.mkdirs(mediaFile.getParent().toFile(), true);
					FileUtils.rename(tmpFile.toFile(), mediaFile.toFile());
				}
				LfsPointer lfsPointer = new LfsPointer(loid, size);
				lfsPointer.encode(out);
				out.close();
				return -1;
			}
		} catch (IOException e) {
			out.close();
			dOut.close();
			tmpOut.close();
			throw e;
		}
	}
}
