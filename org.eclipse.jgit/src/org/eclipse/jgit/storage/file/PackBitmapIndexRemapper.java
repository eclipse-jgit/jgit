package org.eclipse.jgit.storage.file;

import javaewah.EWAHCompressedBitmap;
import javaewah.IntIterator;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.storage.file.BasePackBitmapIndex.StoredBitmap;

/**
 * A PackBitmapIndex that remaps the bitmaps in the previous index to the
 * positions in the new pack index. Note, unlike typical PackBitmapIndex
 * implementations this implementation is not thread safe, as it is intended to
 * be used with a PackBitmapIndexBuilder, which is also not thread safe.
 */
public class PackBitmapIndexRemapper extends PackBitmapIndex {

	private final PackBitmapIndex newPackIndex;

	private final PackBitmapIndex oldPackIndex;

	private final ObjectIdOwnerMap<StoredBitmap> convertedBitmaps;

	private int[] prevToNewMapping;

	private int zeroPosition;

	private BitSet inflated;

	/**
	 * A PackBitmapIndex that maps the positions in the prevBitmapIndex to the
	 * ones in the newIndex.
	 *
	 * @param prevBitmapIndex
	 * @param newIndex
	 */
	public PackBitmapIndexRemapper(
			BitmapIndex prevBitmapIndex, PackBitmapIndex newIndex) {
		this.newPackIndex = newIndex;
		if (prevBitmapIndex instanceof BitmapIndexImpl) {
			this.oldPackIndex = ((BitmapIndexImpl) prevBitmapIndex)
					.getPackBitmapIndex();
			this.convertedBitmaps = new ObjectIdOwnerMap<StoredBitmap>();
		} else {
			this.oldPackIndex = null;
			this.convertedBitmaps = null;
		}
	}

	@Override
	public int findPosition(AnyObjectId objectId) {
		return newPackIndex.findPosition(objectId);
	}

	@Override
	public ObjectId getObject(int position) throws IllegalArgumentException {
		return newPackIndex.getObject(position);
	}

	@Override
	public int getObjectCount() {
		return newPackIndex.getObjectCount();
	}

	@Override
	public EWAHCompressedBitmap ofObjectType(
			EWAHCompressedBitmap bitmap, int type) {
		return newPackIndex.ofObjectType(bitmap, type);
	}

	@Override
	public EWAHCompressedBitmap getBitmap(AnyObjectId objectId) {
		EWAHCompressedBitmap bitmap = newPackIndex.getBitmap(objectId);
		if (bitmap != null)
			return bitmap;
		if (oldPackIndex == null)
			return null;

		StoredBitmap stored = convertedBitmaps.get(objectId);
		if (stored != null)
			return stored.getBitmap();

		EWAHCompressedBitmap oldBitmap = oldPackIndex.getBitmap(objectId);
		if (oldBitmap == null)
			return null;

		bitmap = mapBitmap(oldBitmap);
		convertedBitmaps.add(new StoredBitmap(objectId, bitmap, null));
		return bitmap;
	}

	private EWAHCompressedBitmap mapBitmap(EWAHCompressedBitmap oldBitmap) {
		if (inflated == null)
			inflated = new BitSet(newPackIndex.getObjectCount());
		else
			inflated.clear();

		for (IntIterator ii = oldBitmap.intIterator(); ii.hasNext();)
			inflated.set(mapPosition(ii.next()));
		return inflated.toEWAHCompressedBitmap();
	}

	private int mapPosition(int oldPosition) {
		if (prevToNewMapping == null) {
			prevToNewMapping = new int[oldPackIndex.getObjectCount()];
			zeroPosition = oldPackIndex.findPosition(newPackIndex.getObject(0));
		}

		int pos = prevToNewMapping[oldPosition];
		if (pos == 0 && oldPosition != zeroPosition) {
			pos = newPackIndex.findPosition(oldPackIndex.getObject(oldPosition));
			prevToNewMapping[oldPosition] = pos;
		}
		return pos;
	}
}
