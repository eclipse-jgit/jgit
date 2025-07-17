package org.eclipse.jgit.internal.storage.file;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper to write the object size index for exiting packs (in disk)
 */
public class PackObjectSizeIndexHelper {

	private final static Logger LOG = LoggerFactory
			.getLogger(PackObjectSizeIndexHelper.class);

	/**
	 * Add an object size index to all the packs without one in this object
	 * database.
	 * 
	 * @param db
	 *            object db for packs
	 * @throws IOException
	 *             an error reading the packs or writing the object size index
	 *             file
	 */
	public static void forAllPacks(ObjectDirectory db, ProgressMonitor pm)
			throws IOException {
		WindowCursor wc = (WindowCursor) db.newReader();
		for (Pack pack : db.getPacks()) {
			LOG.info("Checking " + pack.getPackName()); //$NON-NLS-1$
			if (pack.hasObjectSizeIndex()) {
				LOG.debug("    has object size index"); //$NON-NLS-1$
				continue;
			}

			List<PackedObjectInfo> objectsInPack = getObjectsInPack(wc, pack,
					pm);
			LOG.debug(String.format("    index has %d objects", //$NON-NLS-1$
					objectsInPack.size()));
			if (objectsInPack.isEmpty()) {
				continue;
			}

			LOG.info("    start writing object size index");
			PackFile packFile = pack.getPackFile()
					.create(PackExt.OBJECT_SIZE_INDEX);
			long start = System.currentTimeMillis();
			try (FileOutputStream out = new FileOutputStream(packFile)) {
				PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
						.createWriter(out, 0);
				writer.write(objectsInPack);
			}
			LOG.info(String.format("     done writing. Took %d ms",
					System.currentTimeMillis() - start));
		}
	}

	private static List<PackedObjectInfo> getObjectsInPack(WindowCursor wc,
			Pack pack, ProgressMonitor pm) throws IOException {
		PackIndex idx = pack.getIndex();
		PackReverseIndex ridx = new PackReverseIndexComputed(idx);
		pm.beginTask("Listing objects", (int) idx.getObjectCount());
		List<PackedObjectInfo> objs = new ArrayList<>(
				(int) idx.getObjectCount());
		for (int i = 0; i < idx.getObjectCount(); i++) {
			ObjectId oid = ridx.findObjectByPosition(i);
			PackedObjectInfo poi = new PackedObjectInfo(oid);
			long offset = idx.findOffset(oid);
			poi.setFullSize(pack.getObjectSize(wc, offset));
			poi.setType(pack.getObjectType(wc, offset));
			objs.add(poi);
			pm.update(1);
		}
		pm.endTask();
		return objs;
	}

	private PackObjectSizeIndexHelper() {
	}
}
