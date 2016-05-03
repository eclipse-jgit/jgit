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

import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.BuiltinCommand;
import org.eclipse.jgit.util.BuiltinCommandFactory;
import org.eclipse.jgit.util.FileUtils;

/**
 * @since 4.4
 */
public class CleanFilter extends BuiltinCommand {
	/**
	 *
	 */
	public final static BuiltinCommandFactory FACTORY = new BuiltinCommandFactory() {
		@Override
		public BuiltinCommand create(Repository db, InputStream in,
				OutputStream out) {
			try {
				return new CleanFilter(db, in, out);
			} catch (IOException e) {
				return null; // TODO: improve error handling
			}
		}
	};

	LongObjectId id;

	private DigestOutputStream mOut;

	private LfsUtil lfsUtil;

	private long size;

	private Path tmpFile;

	/**
	 * @param db
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public CleanFilter(Repository db, InputStream in, OutputStream out)
			throws IOException {
		super(in, out);
		this.size = 0;
		lfsUtil = new LfsUtil(db.getDirectory().toPath().resolve("lfs")); //$NON-NLS-1$
		Files.createDirectories(lfsUtil.getLfsTmpDir());
		tmpFile = lfsUtil.getTmpFile();
		mOut = new DigestOutputStream(
				Files.newOutputStream(tmpFile, StandardOpenOption.CREATE),
				Constants.newMessageDigest());
	}

	public int run() throws IOException {
		int b = in.read();
		if (b != -1) {
			mOut.write(b);
			size++;
			return 1;
		} else {
			mOut.close();
			LongObjectId loid = LongObjectId
					.fromRaw(mOut.getMessageDigest().digest());
			Path mediaFile = lfsUtil.getMediaFile(loid);
			if (Files.isReadable(mediaFile)) { // TODO: is isReadable correct?
												// If it exists but is not
												// accesible we directly can
												// throw an error
				long fsSize = Files.size(mediaFile);
				if (fsSize != size) {
					// @todo better exception
					throw new IOException("mediafile " + mediaFile
							+ " has unexpected length. Expected " + size
							+ " but found " + fsSize);
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
	}
}
