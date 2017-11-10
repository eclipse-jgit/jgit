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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.IO;

/**
 * An {@link ObjectLoader} implementation that reads a media file from the LFS
 * storage.
 *
 * @since 4.11
 */
public class LfsBlobLoader extends ObjectLoader {

	private Path mediaFile;

	private BasicFileAttributes attributes;

	private byte[] cached;

	/**
	 * Create a loader for the LFS media file at the given path.
	 *
	 * @param mediaFile
	 *            path to the file
	 * @throws IOException
	 *             in case of an error reading attributes
	 */
	public LfsBlobLoader(Path mediaFile) throws IOException {
		this.mediaFile = mediaFile;
		this.attributes = Files.readAttributes(mediaFile,
				BasicFileAttributes.class);
	}

	@Override
	public int getType() {
		return Constants.OBJ_BLOB;
	}

	@Override
	public long getSize() {
		return attributes.size();
	}

	@Override
	public byte[] getCachedBytes() throws LargeObjectException {
		if (getSize() > PackConfig.DEFAULT_BIG_FILE_THRESHOLD) {
			throw new LargeObjectException();
		}

		if (cached == null) {
			try {
				cached = IO.readFully(mediaFile.toFile());
			} catch (IOException ioe) {
				throw new LargeObjectException(ioe);
			}
		}
		return cached;
	}

	@Override
	public ObjectStream openStream()
			throws MissingObjectException, IOException {
		return new ObjectStream.Filter(getType(), getSize(),
				Files.newInputStream(mediaFile));
	}

}
