/*
 * Copyright (C) 2026 Shawn Hartsock <shartsock@nvidia.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PromisedObjectContent;
import org.eclipse.jgit.lib.PromisedObjectException;
import org.eclipse.jgit.lib.PromisedObjectInventory;
import org.eclipse.jgit.lib.PromisedObjectRequest;
import org.eclipse.jgit.lib.PromisedObjectResolver;

/**
 * Resolves objects promised by an external store into the local object
 * database on demand.
 * <p>
 * One instance per {@link ObjectDirectory}, registered through
 * {@link ObjectDirectory#setPromisedObjects(PromisedObjectResolver, PromisedObjectInventory)}.
 * Only inventory-listed objects are fetched; every other missing object keeps
 * normal missing-object behavior.
 * <p>
 * Resolution fetches once, verifies the Git object id, installs a loose
 * object and reopens it locally. Requests for one object id coalesce;
 * distinct ids proceed in parallel. A failed transfer leaves the database
 * unchanged and surfaces as {@link MissingObjectException} with the typed
 * {@link PromisedObjectException} as cause; local write failures propagate
 * unchanged.
 */
final class PromisedObjectSupport {

	private final ObjectDirectory db;

	private final PromisedObjectResolver resolver;

	private final PromisedObjectInventory inventory;

	private final ConcurrentHashMap<ObjectId, ReentrantLock> inFlight = new ConcurrentHashMap<>();

	PromisedObjectSupport(ObjectDirectory db, PromisedObjectResolver resolver,
			PromisedObjectInventory inventory) {
		this.db = db;
		this.resolver = resolver;
		this.inventory = inventory;
	}

	PromisedObjectInventory getInventory() {
		return inventory;
	}

	/**
	 * Whether the object is a promised blob.
	 * <p>
	 * Answers from the inventory alone; never fetches anything.
	 *
	 * @param objectId
	 *            object id to test.
	 * @return {@code true} when the inventory lists the object as promised.
	 */
	boolean isPromisedBlob(AnyObjectId objectId) {
		return inventory.isPromised(objectId);
	}

	/**
	 * Whether a read with the given type hint may be resolved.
	 * <p>
	 * Only blob reads, including reads without a type hint, resolve. Other
	 * type hints keep normal missing-object behavior, so missing commits,
	 * trees and tags never trigger a fetch.
	 *
	 * @param objectId
	 *            object id to test.
	 * @param typeHint
	 *            requested object type, or {@link ObjectReader#OBJ_ANY}.
	 * @return {@code true} when the object is promised and the type hint
	 *         allows resolution.
	 */
	boolean canResolve(AnyObjectId objectId, int typeHint) {
		if (typeHint != Constants.OBJ_BLOB
				&& typeHint != ObjectReader.OBJ_ANY) {
			return false;
		}
		return inventory.isPromised(objectId);
	}

	/**
	 * Resolves a promised blob and returns a loader over the local result.
	 * <p>
	 * Fetches and installs the object unless another thread already did, in
	 * which case the local object is returned without fetching.
	 *
	 * @param curs
	 *            the requesting reader's window cursor.
	 * @param objectId
	 *            promised object id.
	 * @return loader over the local object; never {@code null}.
	 * @throws MissingObjectException
	 *             with the typed resolver failure as cause when the promise
	 *             cannot be resolved.
	 * @throws IOException
	 *             when installing or reading the resolved object fails
	 *             locally.
	 */
	ObjectLoader resolve(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		ReentrantLock lock = lockFor(objectId);
		lock.lock();
		try {
			ObjectLoader ldr = db.openObject(curs, objectId);
			if (ldr != null) {
				return ldr;
			}
			hydrate(curs, objectId);
			ldr = db.openObject(curs, objectId);
			if (ldr == null) {
				throw missing(objectId,
						new PromisedObjectException(
								PromisedObjectException.Reason.INTEGRITY,
								MessageFormat.format(
										JGitText.get().promisedObjectNotInstalled,
										objectId.name())));
			}
			return ldr;
		} finally {
			lock.unlock();
			inFlight.remove(objectId.toObjectId(), lock);
		}
	}

	/**
	 * Resolves the size of a promised blob.
	 * <p>
	 * Answers the expected size from the inventory without fetching when the
	 * inventory records one; hydrates and measures otherwise.
	 *
	 * @param curs
	 *            the requesting reader's window cursor.
	 * @param objectId
	 *            promised object id.
	 * @return content size in bytes.
	 * @throws IOException
	 *             when the size cannot be answered.
	 */
	long resolveSize(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		long expected = inventory.getExpectedLength(objectId);
		if (expected >= 0) {
			return expected;
		}
		resolve(curs, objectId);
		long size = db.getObjectSize(curs, objectId);
		if (size < 0) {
			throw new MissingObjectException(objectId.copy(),
					Constants.OBJ_BLOB);
		}
		return size;
	}

	private void hydrate(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		long expectedLength = inventory.getExpectedLength(objectId);
		PromisedObjectRequest request = new PromisedObjectRequest(
				inventory.getNamespace(), objectId.toObjectId(),
				Constants.OBJ_BLOB, expectedLength);
		PromisedObjectContent content;
		try {
			content = resolver.open(request);
		} catch (IOException e) {
			throw missing(objectId, e);
		}
		try (content) {
			long length = content.getLength();
			if (expectedLength >= 0 && length != expectedLength) {
				throw missing(objectId, new PromisedObjectException(
						PromisedObjectException.Reason.INTEGRITY,
						MessageFormat.format(
								JGitText.get().promisedObjectContentLengthMismatch,
								objectId.name(),
								Long.toString(length),
								Long.toString(expectedLength))));
			}
			InputStream stream;
			try {
				stream = content.openStream();
			} catch (IOException e) {
				throw missing(objectId, e);
			}
			try (ObjectDirectoryInserter inserter = db.newInserter()) {
				try {
					inserter.insertVerified(Constants.OBJ_BLOB, length, stream,
							objectId);
				} catch (PromisedObjectException e) {
					throw missing(objectId, e);
				}
			}
		}
	}

	private static MissingObjectException missing(AnyObjectId objectId,
			IOException cause) {
		MissingObjectException e = new MissingObjectException(objectId.copy(),
				Constants.OBJ_BLOB);
		e.initCause(cause);
		return e;
	}

	private ReentrantLock lockFor(AnyObjectId objectId) {
		return inFlight.computeIfAbsent(objectId.toObjectId(),
				k -> new ReentrantLock());
	}
}
