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
