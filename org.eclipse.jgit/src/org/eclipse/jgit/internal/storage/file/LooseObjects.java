/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.Set;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileObjectDatabase.InsertLooseObjectResult;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traditional file system based loose objects handler.
 * <p>
 * This is the loose object representation for a Git object database, where
 * objects are stored loose by hashing them into directories by their
 * {@link org.eclipse.jgit.lib.ObjectId}.
 */
class LooseObjects {
	private static final Logger LOG = LoggerFactory
			.getLogger(LooseObjects.class);

	/**
	 * Maximum number of attempts to read a loose object for which a stale file
	 * handle exception is thrown
	 */
	private final static int MAX_LOOSE_OBJECT_STALE_READ_ATTEMPTS = 5;

	private final File directory;

	private final UnpackedObjectCache unpackedObjectCache;

	private final boolean trustFolderStat;

	/**
	 * Initialize a reference to an on-disk object directory.
	 *
	 * @param config
	 *            configuration for the loose objects handler.
	 * @param dir
	 *            the location of the <code>objects</code> directory.
	 */
	LooseObjects(Config config, File dir) {
		directory = dir;
		unpackedObjectCache = new UnpackedObjectCache();
		trustFolderStat = config.getBoolean(
				ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, true);
	}

	/**
	 * Getter for the field <code>directory</code>.
	 *
	 * @return the location of the <code>objects</code> directory.
	 */
	File getDirectory() {
		return directory;
	}

	void create() throws IOException {
		FileUtils.mkdirs(directory);
	}

	void close() {
		unpackedObjectCache().clear();
	}

	@Override
	public String toString() {
		return "LooseObjects[" + directory + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	boolean hasCached(AnyObjectId id) {
		return unpackedObjectCache().isUnpacked(id);
	}

	/**
	 * Does the requested object exist as a loose object?
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @return {@code true} if the specified object is stored as a loose object.
	 */
	boolean has(AnyObjectId objectId) {
		boolean exists = hasWithoutRefresh(objectId);
		if (trustFolderStat || exists) {
			return exists;
		}
		try (InputStream stream = Files.newInputStream(directory.toPath())) {
			// refresh directory to work around NFS caching issue
		} catch (IOException e) {
			return false;
		}
		return hasWithoutRefresh(objectId);
	}

	private boolean hasWithoutRefresh(AnyObjectId objectId) {
		return fileFor(objectId).exists();
	}

	/**
	 * Find objects matching the prefix abbreviation.
	 *
	 * @param matches
	 *            set to add any located ObjectIds to. This is an output
	 *            parameter.
	 * @param id
	 *            prefix to search for.
	 * @param matchLimit
	 *            maximum number of results to return. At most this many
	 *            ObjectIds should be added to matches before returning.
	 * @return {@code true} if the matches were exhausted before reaching
	 *         {@code maxLimit}.
	 */
	boolean resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) {
		String fanOut = id.name().substring(0, 2);
		String[] entries = new File(directory, fanOut).list();
		if (entries != null) {
			for (String e : entries) {
				if (e.length() != Constants.OBJECT_ID_STRING_LENGTH - 2) {
					continue;
				}
				try {
					ObjectId entId = ObjectId.fromString(fanOut + e);
					if (id.prefixCompare(entId) == 0) {
						matches.add(entId);
					}
				} catch (IllegalArgumentException notId) {
					continue;
				}
				if (matches.size() > matchLimit) {
					return false;
				}
			}
		}
		return true;
	}

	ObjectLoader open(WindowCursor curs, AnyObjectId id) throws IOException {
		int readAttempts = 0;
		while (readAttempts < MAX_LOOSE_OBJECT_STALE_READ_ATTEMPTS) {
			readAttempts++;
			File path = fileFor(id);
			if (trustFolderStat && !path.exists()) {
				break;
			}
			try {
				return getObjectLoader(curs, path, id);
			} catch (FileNotFoundException noFile) {
				if (path.exists()) {
					throw noFile;
				}
				break;
			} catch (IOException e) {
				if (!FileUtils.isStaleFileHandleInCausalChain(e)) {
					throw e;
				}
				if (LOG.isDebugEnabled()) {
					LOG.debug(MessageFormat.format(
							JGitText.get().looseObjectHandleIsStale, id.name(),
							Integer.valueOf(readAttempts), Integer.valueOf(
									MAX_LOOSE_OBJECT_STALE_READ_ATTEMPTS)));
				}
			}
		}
		unpackedObjectCache().remove(id);
		return null;
	}

	/**
	 * Provides a loader for an objectId
	 *
	 * @param curs
	 *            cursor on the database
	 * @param path
	 *            the path of the loose object
	 * @param id
	 *            the object id
	 * @return a loader for the loose file object
	 * @throws IOException
	 *             when file does not exist or it could not be opened
	 */
	ObjectLoader getObjectLoader(WindowCursor curs, File path, AnyObjectId id)
			throws IOException {
		try {
			return getObjectLoaderWithoutRefresh(curs, path, id);
		} catch (FileNotFoundException e) {
			if (trustFolderStat) {
				throw e;
			}
			try (InputStream stream = Files
					.newInputStream(directory.toPath())) {
				// refresh directory to work around NFS caching issues
			}
			return getObjectLoaderWithoutRefresh(curs, path, id);
		}
	}

	private ObjectLoader getObjectLoaderWithoutRefresh(WindowCursor curs,
			File path, AnyObjectId id) throws IOException {
		try (FileInputStream in = new FileInputStream(path)) {
			unpackedObjectCache().add(id);
			return UnpackedObject.open(in, path, id, curs);
		}
	}

	/**
	 * <p>
	 * Getter for the field <code>unpackedObjectCache</code>.
	 * </p>
	 * This accessor is particularly useful to allow mocking of this class for
	 * testing purposes.
	 *
	 * @return the cache of the objects currently unpacked.
	 */
	UnpackedObjectCache unpackedObjectCache() {
		return unpackedObjectCache;
	}

	long getSize(WindowCursor curs, AnyObjectId id) throws IOException {
		try {
			return getSizeWithoutRefresh(curs, id);
		} catch (FileNotFoundException noFile) {
			try {
				if (trustFolderStat) {
					throw noFile;
				}
				try (InputStream stream = Files
						.newInputStream(directory.toPath())) {
					// refresh directory to work around NFS caching issue
				}
				return getSizeWithoutRefresh(curs, id);
			} catch (FileNotFoundException unused) {
				if (fileFor(id).exists()) {
					throw noFile;
				}
				unpackedObjectCache().remove(id);
				return -1;
			}
		}
	}

	private long getSizeWithoutRefresh(WindowCursor curs, AnyObjectId id)
			throws IOException {
		File f = fileFor(id);
		try (FileInputStream in = new FileInputStream(f)) {
			unpackedObjectCache().add(id);
			return UnpackedObject.getSize(in, id, curs);
		}
	}

	InsertLooseObjectResult insert(File tmp, ObjectId id) throws IOException {
		final File dst = fileFor(id);
		if (dst.exists()) {
			// We want to be extra careful and avoid replacing an object
			// that already exists. We can't be sure renameTo() would
			// fail on all platforms if dst exists, so we check first.
			//
			FileUtils.delete(tmp, FileUtils.RETRY | FileUtils.SKIP_MISSING);
			return InsertLooseObjectResult.EXISTS_LOOSE;
		}

		try {
			return tryMove(tmp, dst, id);
		} catch (NoSuchFileException e) {
			// It's possible the directory doesn't exist yet as the object
			// directories are always lazily created. Note that we try the
			// rename/move first as the directory likely does exist.
			//
			// Create the directory.
			//
			FileUtils.mkdir(dst.getParentFile(), true);
		} catch (IOException e) {
			// Any other IO error is considered a failure.
			//
			LOG.error(e.getMessage(), e);
			FileUtils.delete(tmp, FileUtils.RETRY | FileUtils.SKIP_MISSING);
			return InsertLooseObjectResult.FAILURE;
		}

		try {
			return tryMove(tmp, dst, id);
		} catch (IOException e) {
			// The object failed to be renamed into its proper location and
			// it doesn't exist in the repository either. We really don't
			// know what went wrong, so fail.
			//
			LOG.error(e.getMessage(), e);
			FileUtils.delete(tmp, FileUtils.RETRY | FileUtils.SKIP_MISSING);
			return InsertLooseObjectResult.FAILURE;
		}
	}

	private InsertLooseObjectResult tryMove(File tmp, File dst, ObjectId id)
			throws IOException {
		Files.move(FileUtils.toPath(tmp), FileUtils.toPath(dst),
				StandardCopyOption.ATOMIC_MOVE);
		dst.setReadOnly();
		unpackedObjectCache().add(id);
		return InsertLooseObjectResult.INSERTED;
	}

	/**
	 * Compute the location of a loose object file.
	 *
	 * @param objectId
	 *            identity of the object to get the File location for.
	 * @return {@link java.io.File} location of the specified loose object.
	 */
	File fileFor(AnyObjectId objectId) {
		String n = objectId.name();
		String d = n.substring(0, 2);
		String f = n.substring(2);
		return new File(new File(getDirectory(), d), f);
	}
}
