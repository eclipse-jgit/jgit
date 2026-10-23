/*
 * Copyright (C) 2026, Kyln contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.io.IOException;
import java.io.InputStream;

/**
 * Content of one promised object, opened by a
 * {@link PromisedObjectResolver}.
 * <p>
 * The content length is declared before the stream is opened, so the consumer
 * can bound disk and memory use before reading. The stream must produce
 * exactly {@link #getLength()} bytes before end of stream.
 * <p>
 * This interface is part of an experimental prototyped feature (JGit promisor
 * resolution); the API is not yet frozen and may change without notice.
 *
 * @since 8.0
 */
public interface PromisedObjectContent extends AutoCloseable {

	/**
	 * Get the declared content length in bytes.
	 *
	 * @return content length in bytes; must not be negative.
	 */
	long getLength();

	/**
	 * Opens the content stream.
	 * <p>
	 * The returned stream must produce exactly {@link #getLength()} bytes.
	 * Closing the stream is the caller's responsibility; implementations must
	 * also release all resources when {@link #close()} is called, whether or
	 * not a stream was ever opened.
	 *
	 * @return stream over the raw content of the promised object, without any
	 *         Git object header.
	 * @throws IOException
	 *             if the stream cannot be opened.
	 */
	InputStream openStream() throws IOException;

	/**
	 * Releases resources held by this content.
	 * <p>
	 * This method must not throw; implementations should log or suppress
	 * close failures.
	 */
	@Override
	void close();
}
