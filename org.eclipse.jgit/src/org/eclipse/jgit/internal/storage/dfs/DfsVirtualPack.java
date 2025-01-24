package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.Comparator;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * Make n-packs look like a single pack.
 * <p>
 * This hiddes from the caller if it is dealing with a single pack or n-packs
 * tied together with a multipack index.
 */
public interface DfsVirtualPack {

	/**
	 * Order virtual packs with more recent first
	 */
	Comparator<DfsVirtualPack> DEFAULT_COMPARATOR = Comparator
			.comparing(DfsVirtualPack::getLastModified).reversed();

	/**
	 * Pair of pack and offset in the pack of an object
	 */
	final class DfsPackOffset {
		private DfsPackFile pack;

		private long offset;

		DfsPackOffset() {
		}

		DfsPackOffset setValues(DfsPackFile pack, long offset) {
			this.pack = pack;
			this.offset = offset;
			return this;
		}

		public DfsPackFile getPack() {
			return pack;
		}

		public long getOffset() {
			return offset;
		}
	}

	/**
	 * Pack description of this pack
	 *
	 * @return pack description
	 */
	DfsPackDescription getPackDescription();

	/**
	 * Last modified time for this pack, to sort the stack of virtual packs.
	 * <p>
	 * This may not match the last modified in the pack description when
	 * grouping multiple packs.
	 *
	 * @return last modified time for this pack.
	 */
	long getLastModified();

	/**
	 * True if this pack contains the object
	 *
	 * @param ctx
	 *            a reader
	 * @param id
	 *            an object id to lookup
	 * @return true if any of the packs included contains the object
	 * @throws IOException
	 *             an error reading
	 */
	boolean hasObject(DfsReader ctx, AnyObjectId id) throws IOException;

	/**
	 * Return the pack and offset there for the object.
	 * <p>
	 * The returned object is mutable, caller should not hold to it.
	 *
	 * @param ctx
	 *            a reader
	 * @param id
	 *            an object id to lookup
	 * @return a mutable pair of pack and offset
	 * @throws IOException
	 *             an error reading
	 */
	@Nullable
	DfsPackOffset find(DfsReader ctx, AnyObjectId id) throws IOException;

	/**
	 * Get an object from this pack.
	 *
	 * @param ctx
	 *            temporary working space associated with the calling thread.
	 * @param id
	 *            the object to obtain from the pack. Must not be null.
	 * @return the object loader for the requested object if it is contained in
	 *         this pack; null if the object was not found.
	 * @throws IOException
	 *             the pack file or the index could not be read.
	 */
	@Nullable
	ObjectLoader get(DfsReader ctx, AnyObjectId id) throws IOException;

	/**
	 * Find all entries starting with an abbreviated object id
	 *
	 * @param ctx
	 *            a reader
	 * @param matches
	 *            resulting matches
	 * @param id
	 *            abbreviated id to expand
	 * @param matchLimit
	 *            max amount of result to return
	 * @throws IOException
	 *             an error reading the pack or its index
	 */
	void resolve(DfsReader ctx, Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException;

	/**
	 * Get the bitmap index for the pack
	 *
	 * @param ctx
	 *            a reader
	 * @return the pack bitmap index with the bitmaps for this pack or null if
	 *         not available
	 * @throws IOException
	 *             an error reading from storage
	 */
	@Nullable
	PackBitmapIndex getBitmapIndex(DfsReader ctx) throws IOException;

	/**
	 * Get the commit graph in this pack
	 *
	 * @param ctx
	 *            a reader
	 * @return the commit graph of this pack, null if not available
	 * @throws IOException
	 *             an error reading from storage
	 */
	@Nullable
	CommitGraph getCommitGraph(DfsReader ctx) throws IOException;

	/**
	 * Get the size of the object
	 *
	 * @param ctx
	 *            a reader
	 * @param id
	 *            an object id
	 * @return the size in bytes
	 * @throws IOException
	 *             an error reading the object or index
	 */
	long getObjectSize(DfsReader ctx, AnyObjectId id) throws IOException;

	/**
	 * True if this pack contains only unreachable objects
	 *
	 * @return true if this pack contains only unreachable objects
	 */
	boolean isUnreachableGarbage();
}
