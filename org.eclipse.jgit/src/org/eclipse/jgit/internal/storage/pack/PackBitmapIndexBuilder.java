package org.eclipse.jgit.internal.storage.pack;

import java.util.List;

import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;

import com.googlecode.javaewah.EWAHCompressedBitmap;

/**
 * A PackBitmapIndexBuilder that is specifically used for constructing the index
 * for later writing to storage.
 */
public interface PackBitmapIndexBuilder extends PackBitmapIndex {
	/**
	 * Processes a commit and prepares its bitmap to write to the bitmap index
	 * file.
	 *
	 * @param commit
	 *            the commit for which the bitmap pertains
	 * @param bitmap
	 *            the bitmap for the commit
	 * @param flags
	 *            flags to pass along to the
	 *            {@code org.eclipse.jgit.internal.storage.file.BasePackBitmapIndex.StoredBitmap}
	 *            constructor
	 */
	void processBitmapForWrite(BitmapCommit commit, Bitmap bitmap, int flags);

	/**
	 * Stores the bitmap for the objectId.
	 *
	 * @param objectId
	 *            the object id key for the bitmap.
	 * @param bitmap
	 *            bitmap for the object
	 * @param flags
	 *            flags to pass along to the
	 *            {@code org.eclipse.jgit.internal.storage.file.BasePackBitmapIndex.StoredBitmap}
	 *            constructor
	 */
	void addBitmap(AnyObjectId objectId, EWAHCompressedBitmap bitmap,
			int flags);

	/**
	 * Remove all the bitmaps entries added.
	 *
	 * @param size
	 *            the expected number of bitmap entries to be written.
	 */
	void resetBitmaps(int size);

	/**
	 * Get set of objects included in the pack.
	 *
	 * @return set of objects included in the pack.
	 */
	ObjectIdOwnerMap<ObjectIdOwnerMap.Entry> getObjectSet();

	/**
	 * Get the commit object bitmap.
	 *
	 * @return the commit object bitmap.
	 */
	EWAHCompressedBitmap getCommits();

	/**
	 * Get the tree object bitmap.
	 *
	 * @return the tree object bitmap.
	 */
	EWAHCompressedBitmap getTrees();

	/**
	 * Get the blob object bitmap.
	 *
	 * @return the blob object bitmap.
	 */
	EWAHCompressedBitmap getBlobs();

	/**
	 * Get the tag object bitmap.
	 *
	 * @return the tag object bitmap.
	 */
	EWAHCompressedBitmap getTags();

	/**
	 * Get list of compressed entries that need to be written.
	 *
	 * These entries may be xor-compressed depending on the implementation.
	 *
	 * @return a list of the compressed entries.
	 */
	List<StoredEntry> getCompressedBitmaps();

	/** Data object for the on disk representation of a bitmap entry. */
	public static final class StoredEntry {
		private final long objectId;

		private final EWAHCompressedBitmap bitmap;

		private final int xorOffset;

		private final int flags;

		/**
		 * @param objectId
		 *            object id key associated with the bitmap
		 * @param bitmap
		 *            bitmap for the object id
		 * @param xorOffset
		 *            offset of the StoredEntry against which this entry is
		 *            xor-compressed
		 * @param flags
		 *            flags for the bitmap
		 */
		public StoredEntry(long objectId, EWAHCompressedBitmap bitmap,
				int xorOffset, int flags) {
			this.objectId = objectId;
			this.bitmap = bitmap;
			this.xorOffset = xorOffset;
			this.flags = flags;
		}

		/**
		 * Get the bitmap
		 *
		 * @return the bitmap
		 */
		public EWAHCompressedBitmap getBitmap() {
			return bitmap;
		}

		/**
		 * Get the xorOffset
		 *
		 * @return the xorOffset
		 */
		public int getXorOffset() {
			return xorOffset;
		}

		/**
		 * Get the flags
		 *
		 * @return the flags
		 */
		public int getFlags() {
			return flags;
		}

		/**
		 * Get the ObjectId
		 *
		 * @return the ObjectId
		 */
		public long getObjectId() {
			return objectId;
		}
	}


	/**
	 * A functional interface that provides a PackBitmapIndexBuilder when given
	 * a list of pack objects.
	 */
	@FunctionalInterface
	public interface PackBitmapIndexBuilderFactory {
		/**
		 * @param objects
		 *            list of objects in the pack
		 * @return a {@link PackBitmapIndexBuilder} initialized with
		 *         {@code objects}
		 */
		PackBitmapIndexBuilder builder(List<ObjectToPack> objects);
	}
}