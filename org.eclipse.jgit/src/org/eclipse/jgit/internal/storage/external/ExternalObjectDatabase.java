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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Object database for large blobs kept outside the repository's object
 * directory.
 * <p>
 * It has no state of its own: every lookup is answered by its
 * {@link ExternalObjectStore}, which is the only record of what it holds.
 * Only blobs of at least {@link #getMinBlobSize()} bytes belong here.
 * Abbreviated ids are not resolved against the store.
 * <p>
 * Stores are configured in the repository configuration, one subsection per
 * store:
 *
 * <pre>
 * [objectStore "large"]
 *     type = local
 *     path = /mnt/bigdisk/large-objects
 *     minBlobSize = 10m
 * </pre>
 *
 * {@code type} defaults to {@code local}, the only type supported so far. A
 * relative {@code path} is resolved against the objects directory.
 */
public class ExternalObjectDatabase extends ObjectDatabase {

	/** Configuration section holding one subsection per store. */
	public static final String CONFIG_SECTION = "objectStore"; //$NON-NLS-1$

	/** Configuration key for the store type. */
	public static final String CONFIG_KEY_TYPE = "type"; //$NON-NLS-1$

	/** Configuration key for the store location. */
	public static final String CONFIG_KEY_PATH = "path"; //$NON-NLS-1$

	/** Configuration key for the smallest blob size kept in the store. */
	public static final String CONFIG_KEY_MIN_BLOB_SIZE = "minBlobSize"; //$NON-NLS-1$

	/** Store type keeping objects in a local directory. */
	public static final String TYPE_LOCAL = "local"; //$NON-NLS-1$

	/** Default smallest blob size kept in a store: 10 MiB. */
	public static final long DEFAULT_MIN_BLOB_SIZE = 10 * 1024 * 1024;

	private final String name;

	private final long minBlobSize;

	private final ExternalObjectStore store;

	/**
	 * Create an external object database.
	 *
	 * @param name
	 *            name of the store, as used in the configuration
	 * @param minBlobSize
	 *            smallest blob size, in bytes, kept in this database
	 * @param store
	 *            storage backend holding the blobs
	 */
	public ExternalObjectDatabase(String name, long minBlobSize,
			ExternalObjectStore store) {
		this.name = name;
		this.minBlobSize = minBlobSize;
		this.store = store;
	}

	/**
	 * Create the external object databases configured for a repository.
	 *
	 * @param config
	 *            the repository configuration
	 * @param objectsDirectory
	 *            the repository's objects directory
	 * @return the configured databases, in configuration order; empty if no
	 *         store is configured
	 * @throws IOException
	 *             a store is misconfigured
	 */
	public static List<ExternalObjectDatabase> fromConfig(Config config,
			File objectsDirectory) throws IOException {
		Set<String> names = config.getSubsections(CONFIG_SECTION);
		if (names.isEmpty()) {
			return Collections.emptyList();
		}
		List<ExternalObjectDatabase> result = new ArrayList<>(names.size());
		for (String storeName : names) {
			result.add(fromConfig(config, objectsDirectory, storeName));
		}
		return Collections.unmodifiableList(result);
	}

	private static ExternalObjectDatabase fromConfig(Config config,
			File objectsDirectory, String storeName) throws IOException {
		String type = config.getString(CONFIG_SECTION, storeName,
				CONFIG_KEY_TYPE);
		if (type != null && !TYPE_LOCAL.equals(type)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().externalObjectStoreUnsupportedType,
					storeName, type));
		}
		String path = config.getString(CONFIG_SECTION, storeName,
				CONFIG_KEY_PATH);
		if (path == null || path.isEmpty()) {
			throw new IOException(MessageFormat.format(
					JGitText.get().externalObjectStoreMissingPath,
					storeName));
		}
		File dir = new File(path);
		if (!dir.isAbsolute()) {
			dir = new File(objectsDirectory, path);
		}
		long minBlobSize = config.getLong(CONFIG_SECTION, storeName,
				CONFIG_KEY_MIN_BLOB_SIZE, DEFAULT_MIN_BLOB_SIZE);
		return new ExternalObjectDatabase(storeName, minBlobSize,
				new LocalDirectoryObjectStore(dir));
	}

	/**
	 * Get the name of this store.
	 *
	 * @return name of the store, as used in the configuration
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the smallest blob size kept in this database.
	 *
	 * @return smallest blob size in bytes
	 */
	public long getMinBlobSize() {
		return minBlobSize;
	}

	ExternalObjectStore getStore() {
		return store;
	}

	/**
	 * Whether an object belongs in this database.
	 *
	 * @param type
	 *            type of the object, one of the {@code Constants.OBJ_*}
	 *            constants
	 * @param size
	 *            size of the object's content in bytes
	 * @return {@code true} if the object is a blob of at least
	 *         {@link #getMinBlobSize()} bytes
	 */
	public boolean accepts(int type, long size) {
		return type == OBJ_BLOB && size >= minBlobSize;
	}

	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		return store.find(objectId) >= 0;
	}

	/**
	 * Open an object without creating a reader.
	 *
	 * @param objectId
	 *            id of the object
	 * @return a loader for the object, or {@code null} if this database does
	 *         not hold it. Creating the loader looks the object up in the
	 *         store but does not read its content.
	 * @throws IOException
	 *             the store could not be queried
	 */
	@Nullable
	public ObjectLoader openObject(AnyObjectId objectId) throws IOException {
		long size = store.find(objectId);
		return size >= 0
				? new ExternalObjectLoader(store, objectId.copy(), size)
				: null;
	}

	/**
	 * Get the size of an object without creating a reader.
	 *
	 * @param objectId
	 *            id of the object
	 * @return size of the object in bytes, or {@code -1} if this database
	 *         does not hold it
	 * @throws IOException
	 *             the store could not be queried
	 */
	public long getObjectSize(AnyObjectId objectId) throws IOException {
		return store.find(objectId);
	}

	@Override
	public ObjectReader newReader() {
		return new ExternalObjectReader(this);
	}

	@Override
	public ExternalObjectInserter newInserter() {
		return new ExternalObjectInserter(this);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Always {@code -1}: the store keeps no count, and listing it could be
	 * expensive.
	 */
	@Override
	public long getApproximateObjectCount() {
		return -1;
	}

	@Override
	public void close() {
		store.close();
	}

	@Override
	public String toString() {
		return "ExternalObjectDatabase[" + name + ']'; //$NON-NLS-1$
	}
}
