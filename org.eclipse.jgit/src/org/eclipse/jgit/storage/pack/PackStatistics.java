/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.storage.pack;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TAG;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Statistics about {@link org.eclipse.jgit.internal.storage.pack.PackWriter}
 * pack creation.
 *
 * @since 4.1
 */
public class PackStatistics {
	/**
	 * Statistics about a single type of object (commits, tags, trees and
	 * blobs).
	 */
	public static class ObjectType {
		/**
		 * POJO for accumulating the ObjectType statistics.
		 */
		public static class Accumulator {
			/** Count of objects of this type. */
			public long cntObjects;

			/** Count of deltas of this type. */
			public long cntDeltas;

			/** Count of reused objects of this type. */
			public long reusedObjects;

			/** Count of reused deltas of this type. */
			public long reusedDeltas;

			/** Count of bytes for all objects of this type. */
			public long bytes;

			/** Count of delta bytes for objects of this type. */
			public long deltaBytes;
		}

		private ObjectType.Accumulator objectType;

		/**
		 * Creates a new {@link ObjectType} object from the accumulator.
		 *
		 * @param accumulator
		 *            the accumulator of the statistics
		 */
		public ObjectType(ObjectType.Accumulator accumulator) {
			/*
			 * For efficiency this wraps and serves up the Accumulator object
			 * rather than making a deep clone. Normal usage of PackWriter is to
			 * create a single pack/index/bitmap and only call getStatistics()
			 * after all work is complete.
			 */
			objectType = accumulator;
		}

		/**
		 * @return total number of objects output. This total includes the value
		 *         of {@link #getDeltas()}.
		 */
		public long getObjects() {
			return objectType.cntObjects;
		}

		/**
		 * @return total number of deltas output. This may be lower than the
		 *         actual number of deltas if a cached pack was reused.
		 */
		public long getDeltas() {
			return objectType.cntDeltas;
		}

		/**
		 * @return number of objects whose existing representation was reused in
		 *         the output. This count includes {@link #getReusedDeltas()}.
		 */
		public long getReusedObjects() {
			return objectType.reusedObjects;
		}

		/**
		 * @return number of deltas whose existing representation was reused in
		 *         the output, as their base object was also output or was
		 *         assumed present for a thin pack. This may be lower than the
		 *         actual number of reused deltas if a cached pack was reused.
		 */
		public long getReusedDeltas() {
			return objectType.reusedDeltas;
		}

		/**
		 * @return total number of bytes written. This size includes the object
		 *         headers as well as the compressed data. This size also
		 *         includes all of {@link #getDeltaBytes()}.
		 */
		public long getBytes() {
			return objectType.bytes;
		}

		/**
		 * @return number of delta bytes written. This size includes the object
		 *         headers for the delta objects.
		 */
		public long getDeltaBytes() {
			return objectType.deltaBytes;
		}
	}

	/**
	 * POJO for accumulating the statistics.
	 */
	public static class Accumulator {
		/**
		 * The count of references in the ref advertisement.
		 *
		 * @since 4.11
		 */
		public long advertised;

		/**
		 * The count of client wants.
		 *
		 * @since 4.11
		 */
		public long wants;

		/**
		 * The count of client haves.
		 *
		 * @since 4.11
		 */
		public long haves;

		/**
		 * The count of wants that were not advertised by the server.
		 *
		 * @since 5.10
		 */
		public long notAdvertisedWants;

		/**
		 * Time in ms spent in the negotiation phase. For non-bidirectional
		 * transports (e.g., HTTP), this is only for the final request that
		 * sends back the pack file.
		 *
		 * @since 4.11
		 */
		public long timeNegotiating;

		/** The set of objects to be included in the pack. */
		public Set<ObjectId> interestingObjects;

		/** The set of objects to be excluded from the pack. */
		public Set<ObjectId> uninterestingObjects;

		/** The set of shallow commits on the client. */
		public Set<ObjectId> clientShallowCommits;

		/** The collection of reused packs in the upload. */
		public List<CachedPack> reusedPacks;

		/** Commits with no parents. */
		public Set<ObjectId> rootCommits;

		/** If a shallow pack, the depth in commits. */
		public int depth;

		/**
		 * The count of objects in the pack that went through the delta search
		 * process in order to find a potential delta base.
		 */
		public int deltaSearchNonEdgeObjects;

		/**
		 * The count of objects in the pack that went through delta base search
		 * and found a suitable base. This is a subset of
		 * deltaSearchNonEdgeObjects.
		 */
		public int deltasFound;

		/** The total count of objects in the pack. */
		public long totalObjects;

		/**
		 * The count of objects that needed to be discovered through an object
		 * walk because they were not found in bitmap indices.
		 */
		public long bitmapIndexMisses;

		/** The total count of deltas output. */
		public long totalDeltas;

		/** The count of reused objects in the pack. */
		public long reusedObjects;

		/** The count of reused deltas in the pack. */
		public long reusedDeltas;

		/** The count of total bytes in the pack. */
		public long totalBytes;

		/** The size of the thin pack in bytes, if a thin pack was generated. */
		public long thinPackBytes;

		/** Time in ms spent counting the objects that will go into the pack. */
		public long timeCounting;

		/** Time in ms spent searching for objects to reuse. */
		public long timeSearchingForReuse;

		/** Time in ms spent searching for sizes of objects. */
		public long timeSearchingForSizes;

		/** Time in ms spent compressing the pack. */
		public long timeCompressing;

		/** Time in ms spent writing the pack. */
		public long timeWriting;

		/** Time in ms spent checking reachability.
		 *
		 * @since 5.10
		 */
		public long reachabilityCheckDuration;

		/** Number of trees traversed in the walk when writing the pack.
		 *
		 * @since 5.4
		 */
		public long treesTraversed;

		/**
		 * Amount of packfile uris sent to the client to download via HTTP.
		 *
		 * @since 5.6
		 */
		public long offloadedPackfiles;

		/**
		 * Total size (in bytes) offloaded to HTTP downloads.
		 *
		 * @since 5.6
		 */
		public long offloadedPackfileSize;

		/**
		 * Statistics about each object type in the pack (commits, tags, trees
		 * and blobs.)
		 */
		public ObjectType.Accumulator[] objectTypes;

		{
			objectTypes = new ObjectType.Accumulator[5];
			objectTypes[OBJ_COMMIT] = new ObjectType.Accumulator();
			objectTypes[OBJ_TREE] = new ObjectType.Accumulator();
			objectTypes[OBJ_BLOB] = new ObjectType.Accumulator();
			objectTypes[OBJ_TAG] = new ObjectType.Accumulator();
		}
	}

	private Accumulator statistics;

	/**
	 * Creates a new {@link org.eclipse.jgit.storage.pack.PackStatistics} object
	 * from the accumulator.
	 *
	 * @param accumulator
	 *            the accumulator of the statistics
	 */
	public PackStatistics(Accumulator accumulator) {
		/*
		 * For efficiency this wraps and serves up the Accumulator object rather
		 * than making a deep clone. Normal usage of PackWriter is to create a
		 * single pack/index/bitmap and only call getStatistics() after all work
		 * is complete.
		 */
		statistics = accumulator;
	}

	/**
	 * Get the count of references in the ref advertisement.
	 *
	 * @return count of refs in the ref advertisement.
	 * @since 4.11
	 */
	public long getAdvertised() {
		return statistics.advertised;
	}

	/**
	 * Get the count of client wants.
	 *
	 * @return count of client wants.
	 * @since 4.11
	 */
	public long getWants() {
		return statistics.wants;
	}

	/**
	 * Get the count of client haves.
	 *
	 * @return count of client haves.
	 * @since 4.11
	 */
	public long getHaves() {
		return statistics.haves;
	}

	/**
	 * Get the count of client wants that were not advertised by the server.
	 *
	 * @return count of client wants that were not advertised by the server.
	 * @since 5.10
	 */
	public long getNotAdvertisedWants() {
		return statistics.notAdvertisedWants;
	}

	/**
	 * Time in ms spent in the negotiation phase. For non-bidirectional
	 * transports (e.g., HTTP), this is only for the final request that sends
	 * back the pack file.
	 *
	 * @return time for ref advertisement in ms.
	 * @since 4.11
	 */
	public long getTimeNegotiating() {
		return statistics.timeNegotiating;
	}

	/**
	 * Get unmodifiable collection of objects to be included in the pack.
	 *
	 * @return unmodifiable collection of objects to be included in the pack.
	 *         May be {@code null} if the pack was hand-crafted in a unit test.
	 */
	public Set<ObjectId> getInterestingObjects() {
		return statistics.interestingObjects;
	}

	/**
	 * Get unmodifiable collection of objects that should be excluded from the
	 * pack
	 *
	 * @return unmodifiable collection of objects that should be excluded from
	 *         the pack, as the peer that will receive the pack already has
	 *         these objects.
	 */
	public Set<ObjectId> getUninterestingObjects() {
		return statistics.uninterestingObjects;
	}

	/**
	 * Get unmodifiable collection of objects that were shallow commits on the
	 * client.
	 *
	 * @return unmodifiable collection of objects that were shallow commits on
	 *         the client.
	 */
	public Set<ObjectId> getClientShallowCommits() {
		return statistics.clientShallowCommits;
	}

	/**
	 * Get unmodifiable list of the cached packs that were reused in the output
	 *
	 * @return unmodifiable list of the cached packs that were reused in the
	 *         output, if any were selected for reuse.
	 */
	public List<CachedPack> getReusedPacks() {
		return statistics.reusedPacks;
	}

	/**
	 * Get unmodifiable collection of the root commits of the history.
	 *
	 * @return unmodifiable collection of the root commits of the history.
	 */
	public Set<ObjectId> getRootCommits() {
		return statistics.rootCommits;
	}

	/**
	 * Get number of objects in the output pack that went through the delta
	 * search process in order to find a potential delta base.
	 *
	 * @return number of objects in the output pack that went through the delta
	 *         search process in order to find a potential delta base.
	 */
	public int getDeltaSearchNonEdgeObjects() {
		return statistics.deltaSearchNonEdgeObjects;
	}

	/**
	 * Get number of objects in the output pack that went through delta base
	 * search and found a suitable base.
	 *
	 * @return number of objects in the output pack that went through delta base
	 *         search and found a suitable base. This is a subset of
	 *         {@link #getDeltaSearchNonEdgeObjects()}.
	 */
	public int getDeltasFound() {
		return statistics.deltasFound;
	}

	/**
	 * Get total number of objects output.
	 *
	 * @return total number of objects output. This total includes the value of
	 *         {@link #getTotalDeltas()}.
	 */
	public long getTotalObjects() {
		return statistics.totalObjects;
	}

	/**
	 * Get the count of objects that needed to be discovered through an object
	 * walk because they were not found in bitmap indices.
	 *
	 * @return the count of objects that needed to be discovered through an
	 *         object walk because they were not found in bitmap indices.
	 *         Returns -1 if no bitmap indices were found.
	 */
	public long getBitmapIndexMisses() {
		return statistics.bitmapIndexMisses;
	}

	/**
	 * Get total number of deltas output.
	 *
	 * @return total number of deltas output. This may be lower than the actual
	 *         number of deltas if a cached pack was reused.
	 */
	public long getTotalDeltas() {
		return statistics.totalDeltas;
	}

	/**
	 * Get number of objects whose existing representation was reused in the
	 * output.
	 *
	 * @return number of objects whose existing representation was reused in the
	 *         output. This count includes {@link #getReusedDeltas()}.
	 */
	public long getReusedObjects() {
		return statistics.reusedObjects;
	}

	/**
	 * Get number of deltas whose existing representation was reused in the
	 * output.
	 *
	 * @return number of deltas whose existing representation was reused in the
	 *         output, as their base object was also output or was assumed
	 *         present for a thin pack. This may be lower than the actual number
	 *         of reused deltas if a cached pack was reused.
	 */
	public long getReusedDeltas() {
		return statistics.reusedDeltas;
	}

	/**
	 * Get total number of bytes written.
	 *
	 * @return total number of bytes written. This size includes the pack
	 *         header, trailer, thin pack, and reused cached pack(s).
	 */
	public long getTotalBytes() {
		return statistics.totalBytes;
	}

	/**
	 * Get size of the thin pack in bytes.
	 *
	 * @return size of the thin pack in bytes, if a thin pack was generated. A
	 *         thin pack is created when the client already has objects and some
	 *         deltas are created against those objects, or if a cached pack is
	 *         being used and some deltas will reference objects in the cached
	 *         pack. This size does not include the pack header or trailer.
	 */
	public long getThinPackBytes() {
		return statistics.thinPackBytes;
	}

	/**
	 * Get information about this type of object in the pack.
	 *
	 * @param typeCode
	 *            object type code, e.g. OBJ_COMMIT or OBJ_TREE.
	 * @return information about this type of object in the pack.
	 */
	public ObjectType byObjectType(int typeCode) {
		return new ObjectType(statistics.objectTypes[typeCode]);
	}

	/**
	 * Whether the resulting pack file was a shallow pack.
	 *
	 * @return {@code true} if the resulting pack file was a shallow pack.
	 */
	public boolean isShallow() {
		return statistics.depth > 0;
	}

	/**
	 * Get depth (in commits) the pack includes if shallow.
	 *
	 * @return depth (in commits) the pack includes if shallow.
	 */
	public int getDepth() {
		return statistics.depth;
	}

	/**
	 * Get time in milliseconds spent enumerating the objects that need to be
	 * included in the output.
	 *
	 * @return time in milliseconds spent enumerating the objects that need to
	 *         be included in the output. This time includes any restarts that
	 *         occur when a cached pack is selected for reuse.
	 */
	public long getTimeCounting() {
		return statistics.timeCounting;
	}

	/**
	 * Get time in milliseconds spent matching existing representations against
	 * objects that will be transmitted.
	 *
	 * @return time in milliseconds spent matching existing representations
	 *         against objects that will be transmitted, or that the client can
	 *         be assumed to already have.
	 */
	public long getTimeSearchingForReuse() {
		return statistics.timeSearchingForReuse;
	}

	/**
	 * Get time in milliseconds spent finding the sizes of all objects that will
	 * enter the delta compression search window.
	 *
	 * @return time in milliseconds spent finding the sizes of all objects that
	 *         will enter the delta compression search window. The sizes need to
	 *         be known to better match similar objects together and improve
	 *         delta compression ratios.
	 */
	public long getTimeSearchingForSizes() {
		return statistics.timeSearchingForSizes;
	}

	/**
	 * Get time in milliseconds spent on delta compression.
	 *
	 * @return time in milliseconds spent on delta compression. This is observed
	 *         wall-clock time and does not accurately track CPU time used when
	 *         multiple threads were used to perform the delta compression.
	 */
	public long getTimeCompressing() {
		return statistics.timeCompressing;
	}

	/**
	 * Get time in milliseconds spent writing the pack output, from start of
	 * header until end of trailer.
	 *
	 * @return time in milliseconds spent writing the pack output, from start of
	 *         header until end of trailer. The transfer speed can be
	 *         approximated by dividing {@link #getTotalBytes()} by this value.
	 */
	public long getTimeWriting() {
		return statistics.timeWriting;
	}

	/**
	 * Get time in milliseconds spent checking if the client has access to the
	 * commits they are requesting.
	 *
	 * @return time in milliseconds spent checking if the client has access to the
	 * commits they are requesting.
	 * @since 5.10
	 */
	public long getReachabilityCheckDuration() {
		return statistics.reachabilityCheckDuration;
	}

	/**
	 * @return number of trees traversed in the walk when writing the pack.
	 * @since 5.4
	 */
	public long getTreesTraversed() {
		return statistics.treesTraversed;
	}

	/**
	 * @return amount of packfiles offloaded (sent as "packfile-uri")/
	 * @since 5.6
	 */
	public long getOffloadedPackfiles() {
		return statistics.offloadedPackfiles;
	}

	/**
	 * @return total size (in bytes) offloaded to HTTP downloads.
	 * @since 5.6
	 */
	public long getOffloadedPackfilesSize() {
		return statistics.offloadedPackfileSize;
	}

	/**
	 * Get total time spent processing this pack.
	 *
	 * @return total time spent processing this pack.
	 */
	public long getTimeTotal() {
		return statistics.timeCounting + statistics.timeSearchingForReuse
				+ statistics.timeSearchingForSizes + statistics.timeCompressing
				+ statistics.timeWriting;
	}

	/**
	 * Get the average output speed in terms of bytes-per-second.
	 *
	 * @return the average output speed in terms of bytes-per-second.
	 *         {@code getTotalBytes() / (getTimeWriting() / 1000.0)}.
	 */
	public double getTransferRate() {
		return getTotalBytes() / (getTimeWriting() / 1000.0);
	}

	/**
	 * Get formatted message string for display to clients.
	 *
	 * @return formatted message string for display to clients.
	 */
	public String getMessage() {
		return MessageFormat.format(JGitText.get().packWriterStatistics,
				Long.valueOf(statistics.totalObjects),
				Long.valueOf(statistics.totalDeltas),
				Long.valueOf(statistics.reusedObjects),
				Long.valueOf(statistics.reusedDeltas));
	}

	/**
	 * Get a map containing ObjectType statistics.
	 *
	 * @return a map containing ObjectType statistics.
	 */
	public Map<Integer, ObjectType> getObjectTypes() {
		HashMap<Integer, ObjectType> map = new HashMap<>();
		map.put(Integer.valueOf(OBJ_BLOB), new ObjectType(
				statistics.objectTypes[OBJ_BLOB]));
		map.put(Integer.valueOf(OBJ_COMMIT), new ObjectType(
				statistics.objectTypes[OBJ_COMMIT]));
		map.put(Integer.valueOf(OBJ_TAG), new ObjectType(
				statistics.objectTypes[OBJ_TAG]));
		map.put(Integer.valueOf(OBJ_TREE), new ObjectType(
				statistics.objectTypes[OBJ_TREE]));
		return map;
	}
}
