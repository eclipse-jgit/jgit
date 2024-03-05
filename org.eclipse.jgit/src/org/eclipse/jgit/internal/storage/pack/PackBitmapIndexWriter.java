package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;

/**
	 * PackBitmapIndexWriter is passed to the PackWriter to allow overriding the
	 * writing implementation.
	 */
	@FunctionalInterface
	public interface PackBitmapIndexWriter {
		/**
		 * @param bitmaps
		 *            list of bitmaps to be written to a bitmap index
		 * @param packChecksum
		 *            checksum of the pack that the bitmap index refers to
		 * @throws IOException
		 *             thrown in case of IO errors while writing the bitmap
		 *             index
		 */
		public void write(PackBitmapIndexBuilder bitmaps, byte[] packChecksum)
				throws IOException;
  }