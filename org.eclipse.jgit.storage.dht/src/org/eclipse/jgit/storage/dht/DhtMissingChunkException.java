package org.eclipse.jgit.storage.dht;

import java.text.MessageFormat;

/** Indicates a {@link PackChunk} doesn't exist in the database. */
public class DhtMissingChunkException extends DhtException {
	private static final long serialVersionUID = 1L;

	/**
	 * Initialize a new missing chunk exception.
	 *
	 * @param key
	 *            the key of the chunk that is not found.
	 */
	public DhtMissingChunkException(ChunkKey key) {
		super(MessageFormat.format(DhtText.get().missingChunk, key));
	}

	/**
	 * Initialize a new missing chunk exception.
	 *
	 * @param key
	 *            the key of the chunk that is not found.
	 * @param why
	 *            reason the chunk is missing. This may be an explanation about
	 *            low-level data corruption in the database.
	 */
	public DhtMissingChunkException(ChunkKey key, Throwable why) {
		this(key);
		initCause(why);
	}
}
