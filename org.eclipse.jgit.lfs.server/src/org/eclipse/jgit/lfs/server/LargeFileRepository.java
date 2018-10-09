/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.lfs.server;

import java.io.IOException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;

/**
 * Abstraction of a repository for storing large objects
 *
 * @since 4.3
 */
public interface LargeFileRepository {

	/**
	 * Get download action
	 *
	 * @param id
	 *            id of the object to download
	 * @return Action for downloading the object
	 */
	public Response.Action getDownloadAction(AnyLongObjectId id);

	/**
	 * Get upload action
	 *
	 * @param id
	 *            id of the object to upload
	 * @param size
	 *            size of the object to be uploaded
	 * @return Action for uploading the object
	 */
	public Response.Action getUploadAction(AnyLongObjectId id, long size);

	/**
	 * Get verify action
	 *
	 * @param id
	 *            id of the object to be verified
	 * @return Action for verifying the object, or {@code null} if the server
	 *         doesn't support or require verification
	 */
	@Nullable
	public Response.Action getVerifyAction(AnyLongObjectId id);

	/**
	 * Get size of an object
	 *
	 * @param id
	 *            id of the object
	 * @return length of the object content in bytes, -1 if the object doesn't
	 *         exist
	 * @throws java.io.IOException
	 */
	public long getSize(AnyLongObjectId id) throws IOException;
}
