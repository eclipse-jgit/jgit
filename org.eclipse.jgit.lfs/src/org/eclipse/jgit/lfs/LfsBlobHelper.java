package org.eclipse.jgit.lfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;

/**
 * Provides transparently either a stream to the blob or a LFS media file if
 * managed by LFS.
 */
public class LfsBlobHelper {

	/**
	 * @param db
	 *            the repo
	 * @param loader
	 *            the loader for the blob
	 * @return either the original loader, or a loader for the LFS media file if
	 *         managed by LFS. Files are downloaded on demand if required.
	 * @throws IOException
	 *             in case of an error
	 */
	public static ObjectLoader smudgeLfsBlob(Repository db, ObjectLoader loader)
			throws IOException {
		if (loader.getSize() > LfsPointer.SIZE_THRESHOLD) {
			return loader;
		}

		try (InputStream is = loader.openStream()) {
			LfsPointer ptr = LfsPointer.parseLfsPointer(is);
			if (ptr != null) {
				Lfs lfs = new Lfs(db);
				AnyLongObjectId oid = ptr.getOid();
				Path mediaFile = lfs.getMediaFile(oid);
				if (!Files.exists(mediaFile)) {
					SmudgeFilter.downloadLfsResource(lfs, db, ptr);
				}

				return new LfsBlobLoader(mediaFile);
			}
		}

		return loader;
	}

	/**
	 * @param db
	 * @param originalContent
	 * @return a stream to the LFS pointer content
	 * @throws IOException
	 */
	public static InputStream cleanLfsBlob(Repository db,
			InputStream originalContent) throws IOException {
		LocalFile buffer = new TemporaryBuffer.LocalFile(null);
		CleanFilter f = new CleanFilter(db, originalContent, buffer);
		while (f.run() != -1) {
			// loop as long as command.run() tells there is work to do
		}
		return buffer.openInputStream();
	}

}
