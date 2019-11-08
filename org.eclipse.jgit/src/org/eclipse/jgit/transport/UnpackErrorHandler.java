/*
 * Copyright (c) 2019, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.io.IOException;

/**
 * Exception handler for processing an incoming pack file.
 *
 * @since 5.7
 */
public interface UnpackErrorHandler {
	/**
	 * Handle an exception thrown while unpacking the pack file.
	 *
	 * @param t
	 *            exception thrown
	 * @throws IOException
	 *             thrown when failed to write an error back to the client.
	 */
	void handleUnpackException(Throwable t) throws IOException;
}
