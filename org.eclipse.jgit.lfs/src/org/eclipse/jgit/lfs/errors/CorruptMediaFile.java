/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.errors;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;

import org.eclipse.jgit.lfs.internal.LfsText;

/**
 * Thrown when a LFS mediafile is found which doesn't have the expected size
 *
 * @since 4.6
 */
public class CorruptMediaFile extends IOException {
	private static final long serialVersionUID = 1L;

	private Path mediaFile;

	private long expectedSize;

	private long size;

	/**
	 * <p>Constructor for CorruptMediaFile.</p>
	 *
	 * @param mediaFile a {@link java.nio.file.Path} object.
	 * @param expectedSize a long.
	 * @param size a long.
	 */
	@SuppressWarnings("boxing")
	public CorruptMediaFile(Path mediaFile, long expectedSize,
			long size) {
		super(MessageFormat.format(LfsText.get().inconsistentMediafileLength,
				mediaFile, expectedSize, size));
		this.mediaFile = mediaFile;
		this.expectedSize = expectedSize;
		this.size = size;
	}

	/**
	 * Get the <code>mediaFile</code>.
	 *
	 * @return the media file which seems to be corrupt
	 */
	public Path getMediaFile() {
		return mediaFile;
	}

	/**
	 * Get the <code>expectedSize</code>.
	 *
	 * @return the expected size of the media file
	 */
	public long getExpectedSize() {
		return expectedSize;
	}

	/**
	 * Get the <code>size</code>.
	 *
	 * @return the actual size of the media file in the file system
	 */
	public long getSize() {
		return size;
	}
}
