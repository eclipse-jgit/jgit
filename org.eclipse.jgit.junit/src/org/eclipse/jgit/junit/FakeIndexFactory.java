/*
 * Copyright (C) 2025, Google Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackIndex.EntriesIterator;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Create indexes with predefined data
 *
 * @since 7.2
 */
public class FakeIndexFactory {

	/**
	 * An object for the fake index
	 *
	 * @param name
	 *            a sha1
	 * @param offset
	 *            the (fake) position of the object in the pack
	 */
	public record IndexObject(String name, long offset) {
		/**
		 * Name (sha1) as an objectId
		 *
		 * @return name (a sha1) as an objectId.
		 */
		public ObjectId getObjectId() {
			return ObjectId.fromString(name);
		}
	}

	/**
	 * Return an index populated with these objects
	 *
	 * @param objs
	 *            objects to be indexed
	 * @return a PackIndex implementation
	 */
	public static PackIndex indexOf(List<IndexObject> objs) {
		return new FakePackIndex(objs);
	}

	/**
	 * Return a reverse pack index with these objects
	 *
	 * @param objs
	 *            objects to be indexed
	 * @return a PackReverseIndex implementation
	 */
	public static PackReverseIndex reverseIndexOf(List<IndexObject> objs) {
		return new FakeReverseIndex(objs);
	}

	private FakeIndexFactory() {
	}

	private static class FakePackIndex implements PackIndex {
		private static final Comparator<IndexObject> SHA1_COMPARATOR = (o1,
				o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.name(),
						o2.name());

		private final Map<String, IndexObject> idx;

		private final List<IndexObject> sha1Ordered;

		private final long offset64count;

		FakePackIndex(List<IndexObject> objs) {
			sha1Ordered = objs.stream().sorted(SHA1_COMPARATOR)
					.collect(toUnmodifiableList());
			idx = objs.stream().collect(toMap(IndexObject::name, identity()));
			offset64count = objs.stream()
					.filter(o -> o.offset > Integer.MAX_VALUE).count();
		}

		@Override
		public Iterator<MutableEntry> iterator() {
			return new FakeEntriesIterator(sha1Ordered);
		}

		@Override
		public long getObjectCount() {
			return sha1Ordered.size();
		}

		@Override
		public long getOffset64Count() {
			return offset64count;
		}

		@Override
		public ObjectId getObjectId(long nthPosition) {
			return ObjectId
					.fromString(sha1Ordered.get((int) nthPosition).name());
		}

		@Override
		public long getOffset(long nthPosition) {
			return sha1Ordered.get((int) nthPosition).offset();
		}

		@Override
		public long findOffset(AnyObjectId objId) {
			IndexObject o = idx.get(objId.name());
			if (o == null) {
				return -1;
			}
			return o.offset();
		}

		@Override
		public int findPosition(AnyObjectId objId) {
			IndexObject o = idx.get(objId.name());
			if (o == null) {
				return -1;
			}
			return sha1Ordered.indexOf(o);
		}

		@Override
		public long findCRC32(AnyObjectId objId) throws MissingObjectException {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasCRC32Support() {
			return false;
		}

		@Override
		public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
				int matchLimit) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public byte[] getChecksum() {
			return new byte[0];
		}
	}

	private static class FakeReverseIndex implements PackReverseIndex {
		private static final Comparator<IndexObject> OFFSET_COMPARATOR = Comparator
				.comparingLong(IndexObject::offset);

		private final List<IndexObject> byOffset;

		private final Map<Long, IndexObject> ridx;

		FakeReverseIndex(List<IndexObject> objs) {
			byOffset = objs.stream().sorted(OFFSET_COMPARATOR)
					.collect(toUnmodifiableList());
			ridx = byOffset.stream()
					.collect(toMap(IndexObject::offset, identity()));
		}

		@Override
		public void verifyPackChecksum(String packFilePath) {
			// Do nothing
		}

		@Override
		public ObjectId findObject(long offset) {
			IndexObject indexObject = ridx.get(offset);
			if (indexObject == null) {
				return null;
			}
			return ObjectId.fromString(indexObject.name());
		}

		@Override
		public long findNextOffset(long offset, long maxOffset)
				throws CorruptObjectException {
			IndexObject o = ridx.get(offset);
			if (o == null) {
				throw new CorruptObjectException("Invalid offset"); //$NON-NLS-1$
			}
			int pos = byOffset.indexOf(o);
			if (pos == byOffset.size() - 1) {
				return maxOffset;
			}
			return byOffset.get(pos + 1).offset();
		}

		@Override
		public int findPosition(long offset) {
			IndexObject indexObject = ridx.get(offset);
			return byOffset.indexOf(indexObject);
		}

		@Override
		public ObjectId findObjectByPosition(int nthPosition) {
			return byOffset.get(nthPosition).getObjectId();
		}
	}

	private static class FakeEntriesIterator extends EntriesIterator {

		private static final byte[] buffer = new byte[Constants.OBJECT_ID_LENGTH];

		private final Iterator<IndexObject> it;

		FakeEntriesIterator(List<IndexObject> objs) {
			super(objs.size());
			it = objs.iterator();
		}

		@Override
		protected void readNext() {
			IndexObject next = it.next();
			next.getObjectId().copyRawTo(buffer, 0);
			setIdBuffer(buffer, 0);
			setOffset(next.offset());
		}
	}
}
