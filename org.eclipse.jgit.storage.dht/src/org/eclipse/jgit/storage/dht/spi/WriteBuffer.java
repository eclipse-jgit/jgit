/*
 * Copyright (C) 2011, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.storage.dht.spi;

import org.eclipse.jgit.storage.dht.DhtException;

/** Potentially buffers writes until full, or until flush. */
public interface WriteBuffer {
	/**
	 * Flush any pending writes, and wait for them to complete.
	 *
	 * @throws DhtException
	 *             one or more writes failed. As writes may occur in any order,
	 *             the exact state of the database is unspecified.
	 */
	public void flush() throws DhtException;

	/**
	 * Abort pending writes, and wait for acknowledgment.
	 * <p>
	 * Once a buffer has been aborted, it cannot be reused. Application code
	 * must discard the buffer instance and use a different buffer to issue
	 * subsequent operations.
	 * <p>
	 * If writes have not been started yet, they should be discarded and not
	 * submitted to the storage system.
	 * <p>
	 * If writes have already been started asynchronously in the background,
	 * this method may try to cancel them, but must wait for the operation to
	 * either complete or abort before returning. This allows callers to clean
	 * up by scanning the storage system and making corrections to clean up any
	 * partial writes.
	 *
	 * @throws DhtException
	 *             one or more already started writes failed.
	 */
	public void abort() throws DhtException;
}
