/*
 * Copyright (C) 2026 Shawn Hartsock <shartsock@nvidia.com> and others
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
 * The length is declared before the stream is opened, so the consumer can
 * bound disk and memory use. The stream must produce exactly
 * {@link #getLength()} bytes.
 * <p>
 * Experimental API: not yet frozen and may change without notice.
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
	 * The caller closes the stream. {@link #close()} must still release every
	 * resource, whether or not a stream was opened.
	 *
	 * @return stream over the raw content of the promised object, without any
	 *         Git object header.
	 * @throws IOException
	 *             if the stream cannot be opened.
	 */
	InputStream openStream() throws IOException;

	/**
	 * Releases resources held by this content. Must not throw.
	 */
	@Override
	void close();
}
