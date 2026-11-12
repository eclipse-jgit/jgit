/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.sha1.SHA1;

/**
 * {@link ExternalObjectStore} that keeps objects as plain files in a local
 * directory.
 * <p>
 * Each object is stored uncompressed in a file named after its id, using the
 * same two-character fan-out as loose objects, for example
 * {@code ab/cdef0123...}. New objects are written to a temporary file in the
 * store's root directory, hashed while they are written, and renamed only if
 * they are complete and hash to their id.
 */
public class LocalDirectoryObjectStore implements ExternalObjectStore {

	private static final int BUFFER_SIZE = 8192;

	private final File directory;

	/**
	 * Create a store in a local directory.
	 *
	 * @param directory
	 *            root directory of the store. It is created when the first
	 *            object is stored.
	 */
	public LocalDirectoryObjectStore(File directory) {
		this.directory = directory;
	}

	/**
	 * Get the root directory of this store.
	 *
	 * @return the root directory of this store
	 */
	public File getDirectory() {
		return directory;
	}

	@Override
	public long find(AnyObjectId id) throws IOException {
		try {
			return Files.size(fileFor(id).toPath());
		} catch (NoSuchFileException e) {
			return -1;
		}
	}

	@Override
	public InputStream open(AnyObjectId id) throws IOException {
		try {
			return Files.newInputStream(fileFor(id).toPath());
		} catch (NoSuchFileException e) {
			MissingObjectException missing = new MissingObjectException(
					id.copy(), JGitText.get().unknownObjectType2);
			missing.initCause(e);
			throw missing;
		}
	}

	@Override
	public void put(AnyObjectId id, long size, InputStream content)
			throws IOException {
		FileUtils.mkdirs(directory, true);
		Path tmp = Files.createTempFile(directory.toPath(), "put_", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			SHA1 md = SHA1.newInstance();
			md.update(Constants.encodedTypeString(Constants.OBJ_BLOB));
			md.update((byte) ' ');
			md.update(Constants.encodeASCII(size));
			md.update((byte) 0);
			try (OutputStream out = Files.newOutputStream(tmp)) {
				copyExactly(id, size, content, out, md);
			}
			ObjectId actual = md.toObjectId();
			if (!actual.equals(id)) {
				throw new IOException(MessageFormat.format(
						JGitText.get().externalObjectIdMismatch, id.name(),
						actual.name()));
			}
			File dst = fileFor(id);
			FileUtils.mkdirs(dst.getParentFile(), true);
			FileUtils.rename(tmp.toFile(), dst,
					StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	@Override
	public void delete(AnyObjectId id) throws IOException {
		FileUtils.delete(fileFor(id), FileUtils.SKIP_MISSING);
	}

	@Override
	public void close() {
		// Nothing to release.
	}

	@Override
	public String toString() {
		return "LocalDirectoryObjectStore[" + directory + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	File fileFor(AnyObjectId id) {
		String name = id.name();
		return new File(new File(directory, name.substring(0, 2)),
				name.substring(2));
	}

	private static void copyExactly(AnyObjectId id, long size,
			InputStream in, OutputStream out, SHA1 md) throws IOException {
		byte[] buf = new byte[BUFFER_SIZE];
		long remaining = size;
		while (remaining > 0) {
			int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
			if (n < 0) {
				throw sizeMismatch(id, size);
			}
			md.update(buf, 0, n);
			out.write(buf, 0, n);
			remaining -= n;
		}
		if (in.read() >= 0) {
			throw sizeMismatch(id, size);
		}
	}

	private static IOException sizeMismatch(AnyObjectId id, long size) {
		return new IOException(MessageFormat.format(
				JGitText.get().externalObjectSizeMismatch, id.name(),
				Long.valueOf(size)));
	}
}
