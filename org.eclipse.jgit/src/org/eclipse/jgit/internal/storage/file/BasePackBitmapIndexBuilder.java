/**
 *
 */
package org.eclipse.jgit.internal.storage.file;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.BitmapCommit;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;

import com.googlecode.javaewah.EWAHCompressedBitmap;

/**
 * Helper for constructing
 * {@link org.eclipse.jgit.internal.storage.file.PackBitmapIndex}es.
 */
public abstract class BasePackBitmapIndexBuilder extends BasePackBitmapIndex {
	final EWAHCompressedBitmap commits;

	final EWAHCompressedBitmap trees;

	final EWAHCompressedBitmap blobs;

	final EWAHCompressedBitmap tags;

	private final int objectCount;

	BasePackBitmapIndexBuilder(List<ObjectToPack> objects) {
		super(new ObjectIdOwnerMap<>());
		// TODO(delmerico) consider moving the masks code into its own class
		// for composition over inheritance

		this.objectCount = objects.size();

		// 64 objects fit in a single long word (64 bits).
		// On average a repository is 30% commits, 30% trees, 30% blobs.
		// Initialize bitmap capacity for worst case to minimize growing.
		int sizeInWords = Math.max(4, objectCount / 64 / 3);
		commits = new EWAHCompressedBitmap(sizeInWords);
		trees = new EWAHCompressedBitmap(sizeInWords);
		blobs = new EWAHCompressedBitmap(sizeInWords);
		tags = new EWAHCompressedBitmap(sizeInWords);
		for (int i = 0; i < objects.size(); i++) {
			int type = objects.get(i).getType();
			switch (type) {
			case Constants.OBJ_COMMIT:
				commits.set(i);
				break;
			case Constants.OBJ_TREE:
				trees.set(i);
				break;
			case Constants.OBJ_BLOB:
				blobs.set(i);
				break;
			case Constants.OBJ_TAG:
				tags.set(i);
				break;
			default:
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().badObjectType, String.valueOf(type)));
			}
		}
		commits.trim();
		trees.trim();
		blobs.trim();
		tags.trim();
	}

	/**
	 * Stores the bitmap for the objectId.
	 *
	 * @param objectId
	 *            the object id key for the bitmap.
	 * @param bitmap
	 *            the bitmap
	 * @param flags
	 *            the flags to be stored with the bitmap
	 */
	public void addBitmap(AnyObjectId objectId, Bitmap bitmap, int flags) {
		addBitmap(objectId, bitmap.retrieveCompressed(), flags);
	}

	/**
	 * Stores the bitmap for the objectId.
	 *
	 * @param objectId
	 *            the object id key for the bitmap.
	 * @param bitmap
	 *            the bitmap
	 * @param flags
	 *            the flags to be stored with the bitmap
	 */
	public void addBitmap(AnyObjectId objectId, EWAHCompressedBitmap bitmap,
			int flags) {
		bitmap.trim();
		StoredBitmap result = new StoredBitmap(objectId, bitmap, null, flags);
		getBitmaps().add(result);
	}

	/**
	 * Processes a commit and prepares its bitmap to write to the bitmap index
	 * file.
	 *
	 * @param c
	 *            the commit corresponds to the bitmap.
	 * @param bitmap
	 *            the bitmap to be written.
	 * @param flags
	 *            the flags of the commit.
	 */
	public abstract void processBitmapForWrite(BitmapCommit c, Bitmap bitmap,
			int flags);

	/**
	 * Get list of compressed entries that need to be written.
	 *
	 * @return a list of the compressed entries.
	 */

	public abstract List<StoredEntry> getCompressedBitmaps();

	/**
	 * Get set of objects included in the pack.
	 *
	 * @return set of objects included in the pack.
	 */
	public abstract ObjectIdOwnerMap<ObjectIdOwnerMap.Entry> getObjectSet();

	/**
	 * Remove all the bitmaps entries added.
	 *
	 * @param size
	 *            the expected number of bitmap entries to be written.
	 */
	public abstract void resetBitmaps(int size);

	@Override
	public EWAHCompressedBitmap ofObjectType(EWAHCompressedBitmap bitmap,
			int type) {
		switch (type) {
		case Constants.OBJ_BLOB:
			return getBlobs().and(bitmap);
		case Constants.OBJ_TREE:
			return getTrees().and(bitmap);
		case Constants.OBJ_COMMIT:
			return getCommits().and(bitmap);
		case Constants.OBJ_TAG:
			return getTags().and(bitmap);
		}
		throw new IllegalArgumentException();
	}

	@Override
	public int getObjectCount() {
		return objectCount;
	}

	/**
	 * Get the commit object bitmap.
	 *
	 * @return the commit object bitmap.
	 */
	public EWAHCompressedBitmap getCommits() {
		return commits;
	}

	/**
	 * Get the tree object bitmap.
	 *
	 * @return the tree object bitmap.
	 */
	public EWAHCompressedBitmap getTrees() {
		return trees;
	}

	/**
	 * Get the blob object bitmap.
	 *
	 * @return the blob object bitmap.
	 */
	public EWAHCompressedBitmap getBlobs() {
		return blobs;
	}

	/**
	 * Get the tag object bitmap.
	 *
	 * @return the tag object bitmap.
	 */
	public EWAHCompressedBitmap getTags() {
		return tags;
	}

	/** Data object for the on disk representation of a bitmap entry. */
	public static final class StoredEntry {
		private final long objectId;

		private final EWAHCompressedBitmap bitmap;

		private final int xorOffset;

		private final int flags;

		StoredEntry(long objectId, EWAHCompressedBitmap bitmap, int xorOffset,
				int flags) {
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
}
