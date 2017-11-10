package org.eclipse.jgit.lfs;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.IO;

/**
 * A {@link ObjectLoader} implementation that reads a media file from the LFS
 * storage.
 */
public class LfsBlobLoader extends ObjectLoader {

	private Path mediaFile;

	private BasicFileAttributes attributes;

	private byte[] cached;

	/**
	 * @param mediaFile
	 * @throws IOException
	 */
	public LfsBlobLoader(Path mediaFile) throws IOException {
		this.mediaFile = mediaFile;
		this.attributes = Files.readAttributes(mediaFile,
				BasicFileAttributes.class);
	}

	@Override
	public int getType() {
		return Constants.OBJ_BLOB;
	}

	@Override
	public long getSize() {
		return attributes.size();
	}

	@Override
	public byte[] getCachedBytes() throws LargeObjectException {
		if (getSize() > PackConfig.DEFAULT_BIG_FILE_THRESHOLD) {
			throw new LargeObjectException();
		}

		if (cached == null) {
			try {
				cached = IO.readFully(mediaFile.toFile());
			} catch (IOException ioe) {
				throw new RuntimeException(ioe); // oups.
			}
		}
		return cached;
	}

	@Override
	public ObjectStream openStream()
			throws MissingObjectException, IOException {
		return new ObjectStream.Filter(getType(), getSize(),
				new FileInputStream(mediaFile.toFile()));
	}

}
