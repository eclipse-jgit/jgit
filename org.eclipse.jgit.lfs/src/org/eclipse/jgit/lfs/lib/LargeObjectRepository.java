package org.eclipse.jgit.lfs.lib;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Abstraction of a repository for storing large objects
 *
 * @since 4.1
 */
public interface LargeObjectRepository {

	/**
	 * @param id
	 *            id of the object
	 * @return {@code true} if the object exists, {@code false} otherwise
	 */
	public boolean exists(AnyLongObjectId id);

	/**
	 * @param id
	 *            id of the object
	 * @return length of the object content in bytes
	 * @throws IOException
	 */
	public long getLength(AnyLongObjectId id) throws IOException;

	/**
	 * Get a channel to read the object's content. The caller is responsible to
	 * close the channel
	 *
	 * @param id
	 *            id of the object to read
	 * @return the channel to read large object byte stream from
	 * @throws IOException
	 */
	public ReadableByteChannel getReadChannel(AnyLongObjectId id)
			throws IOException;

	/**
	 * Get a channel to write the object's content. The caller is responsible to
	 * close the channel.
	 *
	 * @param id
	 *            id of the object to write
	 * @return the channel to write large object byte stream to
	 * @throws IOException
	 */
	public WritableByteChannel getWriteChannel(AnyLongObjectId id)
			throws IOException;

	/**
	 * Call this to abort write
	 */
	public void abortWrite();
}
