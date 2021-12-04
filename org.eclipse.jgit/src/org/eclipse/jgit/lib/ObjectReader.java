/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_ABBREV_STRING_LENGTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.revwalk.BitmappedObjectReachabilityChecker;
import org.eclipse.jgit.internal.revwalk.BitmappedReachabilityChecker;
import org.eclipse.jgit.internal.revwalk.PedestrianObjectReachabilityChecker;
import org.eclipse.jgit.internal.revwalk.PedestrianReachabilityChecker;
import org.eclipse.jgit.revwalk.ObjectReachabilityChecker;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Reads an {@link org.eclipse.jgit.lib.ObjectDatabase} for a single thread.
 * <p>
 * Readers that can support efficient reuse of pack encoded objects should also
 * implement the companion interface
 * {@link org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs}.
 */
public abstract class ObjectReader implements AutoCloseable {
	/** Type hint indicating the caller doesn't know the type. */
	public static final int OBJ_ANY = -1;

	/**
	 * The threshold at which a file will be streamed rather than loaded
	 * entirely into memory.
	 * @since 4.6
	 */
	protected int streamFileThreshold;

	/**
	 * Construct a new reader from the same data.
	 * <p>
	 * Applications can use this method to build a new reader from the same data
	 * source, but for an different thread.
	 *
	 * @return a brand new reader, using the same data source.
	 */
	public abstract ObjectReader newReader();

	/**
	 * Obtain a unique abbreviation (prefix) of an object SHA-1.
	 *
	 * This method uses a reasonable default for the minimum length. Callers who
	 * don't care about the minimum length should prefer this method.
	 *
	 * The returned abbreviation would expand back to the argument ObjectId when
	 * passed to {@link #resolve(AbbreviatedObjectId)}, assuming no new objects
	 * are added to this repository between calls.
	 *
	 * @param objectId
	 *            object identity that needs to be abbreviated.
	 * @return SHA-1 abbreviation.
	 * @throws java.io.IOException
	 *             the object store cannot be read.
	 */
	public AbbreviatedObjectId abbreviate(AnyObjectId objectId)
			throws IOException {
		return abbreviate(objectId, OBJECT_ID_ABBREV_STRING_LENGTH);
	}

	/**
	 * Obtain a unique abbreviation (prefix) of an object SHA-1.
	 *
	 * The returned abbreviation would expand back to the argument ObjectId when
	 * passed to {@link #resolve(AbbreviatedObjectId)}, assuming no new objects
	 * are added to this repository between calls.
	 *
	 * The default implementation of this method abbreviates the id to the
	 * minimum length, then resolves it to see if there are multiple results.
	 * When multiple results are found, the length is extended by 1 and resolve
	 * is tried again.
	 *
	 * @param objectId
	 *            object identity that needs to be abbreviated.
	 * @param len
	 *            minimum length of the abbreviated string. Must be in the range
	 *            [2, {@value Constants#OBJECT_ID_STRING_LENGTH}].
	 * @return SHA-1 abbreviation. If no matching objects exist in the
	 *         repository, the abbreviation will match the minimum length.
	 * @throws java.io.IOException
	 *             the object store cannot be read.
	 */
	public AbbreviatedObjectId abbreviate(AnyObjectId objectId, int len)
			throws IOException {
		if (len == Constants.OBJECT_ID_STRING_LENGTH)
			return AbbreviatedObjectId.fromObjectId(objectId);

		AbbreviatedObjectId abbrev = objectId.abbreviate(len);
		Collection<ObjectId> matches = resolve(abbrev);
		while (1 < matches.size() && len < Constants.OBJECT_ID_STRING_LENGTH) {
			abbrev = objectId.abbreviate(++len);
			List<ObjectId> n = new ArrayList<>(8);
			for (ObjectId candidate : matches) {
				if (abbrev.prefixCompare(candidate) == 0)
					n.add(candidate);
			}
			if (1 < n.size())
				matches = n;
			else
				matches = resolve(abbrev);
		}
		return abbrev;
	}

	/**
	 * Resolve an abbreviated ObjectId to its full form.
	 *
	 * This method searches for an ObjectId that begins with the abbreviation,
	 * and returns at least some matching candidates.
	 *
	 * If the returned collection is empty, no objects start with this
	 * abbreviation. The abbreviation doesn't belong to this repository, or the
	 * repository lacks the necessary objects to complete it.
	 *
	 * If the collection contains exactly one member, the abbreviation is
	 * (currently) unique within this database. There is a reasonably high
	 * probability that the returned id is what was previously abbreviated.
	 *
	 * If the collection contains 2 or more members, the abbreviation is not
	 * unique. In this case the implementation is only required to return at
	 * least 2 candidates to signal the abbreviation has conflicts. User
	 * friendly implementations should return as many candidates as reasonably
	 * possible, as the caller may be able to disambiguate further based on
	 * context. However since databases can be very large (e.g. 10 million
	 * objects) returning 625,000 candidates for the abbreviation "0" is simply
	 * unreasonable, so implementors should draw the line at around 256 matches.
	 *
	 * @param id
	 *            abbreviated id to resolve to a complete identity. The
	 *            abbreviation must have a length of at least 2.
	 * @return candidates that begin with the abbreviated identity.
	 * @throws java.io.IOException
	 *             the object store cannot be read.
	 */
	public abstract Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException;

	/**
	 * Does the requested object exist in this database?
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @return true if the specified object is stored in this database.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public boolean has(AnyObjectId objectId) throws IOException {
		return has(objectId, OBJ_ANY);
	}

	/**
	 * Does the requested object exist in this database?
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @param typeHint
	 *            hint about the type of object being requested, e.g.
	 *            {@link org.eclipse.jgit.lib.Constants#OBJ_BLOB};
	 *            {@link #OBJ_ANY} if the object type is not known, or does not
	 *            matter to the caller.
	 * @return true if the specified object is stored in this database.
	 * @throws IncorrectObjectTypeException
	 *             typeHint was not OBJ_ANY, and the object's actual type does
	 *             not match typeHint.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public boolean has(AnyObjectId objectId, int typeHint) throws IOException {
		try {
			open(objectId, typeHint);
			return true;
		} catch (MissingObjectException notFound) {
			return false;
		}
	}

	/**
	 * Open an object from this database.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @return a {@link org.eclipse.jgit.lib.ObjectLoader} for accessing the
	 *         object.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object does not exist.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public ObjectLoader open(AnyObjectId objectId)
			throws MissingObjectException, IOException {
		return open(objectId, OBJ_ANY);
	}

	/**
	 * Open an object from this database.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @param typeHint
	 *            hint about the type of object being requested, e.g.
	 *            {@link org.eclipse.jgit.lib.Constants#OBJ_BLOB};
	 *            {@link #OBJ_ANY} if the object type is not known, or does not
	 *            matter to the caller.
	 * @return a {@link org.eclipse.jgit.lib.ObjectLoader} for accessing the
	 *         object.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             typeHint was not OBJ_ANY, and the object's actual type does
	 *             not match typeHint.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public abstract ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException;

	/**
	 * Returns IDs for those commits which should be considered as shallow.
	 *
	 * @return IDs of shallow commits
	 * @throws java.io.IOException
	 */
	public abstract Set<ObjectId> getShallowCommits() throws IOException;

	/**
	 * Asynchronous object opening.
	 *
	 * @param objectIds
	 *            objects to open from the object store. The supplied collection
	 *            must not be modified until the queue has finished.
	 * @param reportMissing
	 *            if true missing objects are reported by calling failure with a
	 *            MissingObjectException. This may be more expensive for the
	 *            implementation to guarantee. If false the implementation may
	 *            choose to report MissingObjectException, or silently skip over
	 *            the object with no warning.
	 * @return queue to read the objects from.
	 */
	public <T extends ObjectId> AsyncObjectLoaderQueue<T> open(
			Iterable<T> objectIds, final boolean reportMissing) {
		final Iterator<T> idItr = objectIds.iterator();
		return new AsyncObjectLoaderQueue<>() {
			private T cur;

			@Override
			public boolean next() throws MissingObjectException, IOException {
				if (idItr.hasNext()) {
					cur = idItr.next();
					return true;
				}
				return false;
			}

			@Override
			public T getCurrent() {
				return cur;
			}

			@Override
			public ObjectId getObjectId() {
				return cur;
			}

			@Override
			public ObjectLoader open() throws IOException {
				return ObjectReader.this.open(cur, OBJ_ANY);
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return true;
			}

			@Override
			public void release() {
				// Since we are sequential by default, we don't
				// have any state to clean up if we terminate early.
			}
		};
	}

	/**
	 * Get only the size of an object.
	 * <p>
	 * The default implementation of this method opens an ObjectLoader.
	 * Databases are encouraged to override this if a faster access method is
	 * available to them.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @param typeHint
	 *            hint about the type of object being requested, e.g.
	 *            {@link org.eclipse.jgit.lib.Constants#OBJ_BLOB};
	 *            {@link #OBJ_ANY} if the object type is not known, or does not
	 *            matter to the caller.
	 * @return size of object in bytes.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             typeHint was not OBJ_ANY, and the object's actual type does
	 *             not match typeHint.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return open(objectId, typeHint).getSize();
	}

	/**
	 * Check if the object size is less or equal than certain value
	 *
	 * By default, it reads the object from storage to get the size. Subclasses
	 * can implement more efficient lookups.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @param typeHint
	 *            hint about the type of object being requested, e.g.
	 *            {@link org.eclipse.jgit.lib.Constants#OBJ_BLOB};
	 *            {@link #OBJ_ANY} if the object type is not known, or does not
	 *            matter to the caller.
	 * @param size
	 *            threshold value for the size of the object in bytes.
	 * @return true if the object size is equal or smaller than the threshold
	 *         value
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             typeHint was not OBJ_ANY, and the object's actual type does
	 *             not match typeHint.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public boolean isSmallerThan(AnyObjectId objectId, int typeHint, long size)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return open(objectId, typeHint).getSize() < size;
	}

	/**
	 * Asynchronous object size lookup.
	 *
	 * @param objectIds
	 *            objects to get the size of from the object store. The supplied
	 *            collection must not be modified until the queue has finished.
	 * @param reportMissing
	 *            if true missing objects are reported by calling failure with a
	 *            MissingObjectException. This may be more expensive for the
	 *            implementation to guarantee. If false the implementation may
	 *            choose to report MissingObjectException, or silently skip over
	 *            the object with no warning.
	 * @return queue to read object sizes from.
	 */
	public <T extends ObjectId> AsyncObjectSizeQueue<T> getObjectSize(
			Iterable<T> objectIds, final boolean reportMissing) {
		final Iterator<T> idItr = objectIds.iterator();
		return new AsyncObjectSizeQueue<>() {
			private T cur;

			private long sz;

			@Override
			public boolean next() throws MissingObjectException, IOException {
				if (idItr.hasNext()) {
					cur = idItr.next();
					sz = getObjectSize(cur, OBJ_ANY);
					return true;
				}
				return false;
			}

			@Override
			public T getCurrent() {
				return cur;
			}

			@Override
			public ObjectId getObjectId() {
				return cur;
			}

			@Override
			public long getSize() {
				return sz;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return true;
			}

			@Override
			public void release() {
				// Since we are sequential by default, we don't
				// have any state to clean up if we terminate early.
			}
		};
	}

	/**
	 * Advise the reader to avoid unreachable objects.
	 * <p>
	 * While enabled the reader will skip over anything previously proven to be
	 * unreachable. This may be dangerous in the face of concurrent writes.
	 *
	 * @param avoid
	 *            true to avoid unreachable objects.
	 * @since 3.0
	 */
	public void setAvoidUnreachableObjects(boolean avoid) {
		// Do nothing by default.
	}

	/**
	 * An index that can be used to speed up ObjectWalks.
	 *
	 * @return the index or null if one does not exist.
	 * @throws java.io.IOException
	 *             when the index fails to load
	 * @since 3.0
	 */
	public BitmapIndex getBitmapIndex() throws IOException {
		return null;
	}

	/**
	 * Create a reachability checker that will use bitmaps if possible.
	 *
	 * @param rw
	 *            revwalk for use by the reachability checker
	 * @return the most efficient reachability checker for this repository.
	 * @throws IOException
	 *             if it cannot open any of the underlying indices.
	 *
	 * @since 5.11
	 */
	@NonNull
	public ReachabilityChecker createReachabilityChecker(RevWalk rw)
			throws IOException {
		if (getBitmapIndex() != null) {
			return new BitmappedReachabilityChecker(rw);
		}

		return new PedestrianReachabilityChecker(true, rw);
	}

	/**
	 * Create an object reachability checker that will use bitmaps if possible.
	 *
	 * This reachability checker accepts any object as target. For checks
	 * exclusively between commits, use
	 * {@link #createReachabilityChecker(RevWalk)}.
	 *
	 * @param ow
	 *            objectwalk for use by the reachability checker
	 * @return the most efficient object reachability checker for this
	 *         repository.
	 *
	 * @throws IOException
	 *             if it cannot open any of the underlying indices.
	 *
	 * @since 5.11
	 */
	@NonNull
	public ObjectReachabilityChecker createObjectReachabilityChecker(
			ObjectWalk ow) throws IOException {
		if (getBitmapIndex() != null) {
			return new BitmappedObjectReachabilityChecker(ow);
		}

		return new PedestrianObjectReachabilityChecker(ow);
	}

	/**
	 * Get the {@link org.eclipse.jgit.lib.ObjectInserter} from which this
	 * reader was created using {@code inserter.newReader()}
	 *
	 * @return the {@link org.eclipse.jgit.lib.ObjectInserter} from which this
	 *         reader was created using {@code inserter.newReader()}, or null if
	 *         this reader was not created from an inserter.
	 * @since 4.4
	 */
	@Nullable
	public ObjectInserter getCreatedFromInserter() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Release any resources used by this reader.
	 * <p>
	 * A reader that has been released can be used again, but may need to be
	 * released after the subsequent usage.
	 *
	 * @since 4.0
	 */
	@Override
	public abstract void close();

	/**
	 * Sets the threshold at which a file will be streamed rather than loaded
	 * entirely into memory
	 *
	 * @param threshold
	 *            the new threshold
	 * @since 4.6
	 */
	public void setStreamFileThreshold(int threshold) {
		streamFileThreshold = threshold;
	}

	/**
	 * Returns the threshold at which a file will be streamed rather than loaded
	 * entirely into memory
	 *
	 * @return the threshold in bytes
	 * @since 4.6
	 */
	public int getStreamFileThreshold() {
		return streamFileThreshold;
	}

	/**
	 * Wraps a delegate ObjectReader.
	 *
	 * @since 4.4
	 */
	public abstract static class Filter extends ObjectReader {
		/**
		 * @return delegate ObjectReader to handle all processing.
		 * @since 4.4
		 */
		protected abstract ObjectReader delegate();

		@Override
		public ObjectReader newReader() {
			return delegate().newReader();
		}

		@Override
		public AbbreviatedObjectId abbreviate(AnyObjectId objectId)
				throws IOException {
			return delegate().abbreviate(objectId);
		}

		@Override
		public AbbreviatedObjectId abbreviate(AnyObjectId objectId, int len)
				throws IOException {
			return delegate().abbreviate(objectId, len);
		}

		@Override
		public Collection<ObjectId> resolve(AbbreviatedObjectId id)
				throws IOException {
			return delegate().resolve(id);
		}

		@Override
		public boolean has(AnyObjectId objectId) throws IOException {
			return delegate().has(objectId);
		}

		@Override
		public boolean has(AnyObjectId objectId, int typeHint) throws IOException {
			return delegate().has(objectId, typeHint);
		}

		@Override
		public ObjectLoader open(AnyObjectId objectId)
				throws MissingObjectException, IOException {
			return delegate().open(objectId);
		}

		@Override
		public ObjectLoader open(AnyObjectId objectId, int typeHint)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			return delegate().open(objectId, typeHint);
		}

		@Override
		public Set<ObjectId> getShallowCommits() throws IOException {
			return delegate().getShallowCommits();
		}

		@Override
		public <T extends ObjectId> AsyncObjectLoaderQueue<T> open(
				Iterable<T> objectIds, boolean reportMissing) {
			return delegate().open(objectIds, reportMissing);
		}

		@Override
		public long getObjectSize(AnyObjectId objectId, int typeHint)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			return delegate().getObjectSize(objectId, typeHint);
		}

		@Override
		public <T extends ObjectId> AsyncObjectSizeQueue<T> getObjectSize(
				Iterable<T> objectIds, boolean reportMissing) {
			return delegate().getObjectSize(objectIds, reportMissing);
		}

		@Override
		public void setAvoidUnreachableObjects(boolean avoid) {
			delegate().setAvoidUnreachableObjects(avoid);
		}

		@Override
		public BitmapIndex getBitmapIndex() throws IOException {
			return delegate().getBitmapIndex();
		}

		@Override
		@Nullable
		public ObjectInserter getCreatedFromInserter() {
			return delegate().getCreatedFromInserter();
		}

		@Override
		public void close() {
			delegate().close();
		}
	}
}
