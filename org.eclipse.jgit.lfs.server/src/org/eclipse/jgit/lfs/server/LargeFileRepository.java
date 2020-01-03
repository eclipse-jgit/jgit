/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
	Response.Action getDownloadAction(AnyLongObjectId id);

	/**
	 * Get upload action
	 *
	 * @param id
	 *            id of the object to upload
	 * @param size
	 *            size of the object to be uploaded
	 * @return Action for uploading the object
	 */
	Response.Action getUploadAction(AnyLongObjectId id, long size);

	/**
	 * Get verify action
	 *
	 * @param id
	 *            id of the object to be verified
	 * @return Action for verifying the object, or {@code null} if the server
	 *         doesn't support or require verification
	 */
	@Nullable
	Response.Action getVerifyAction(AnyLongObjectId id);

	/**
	 * Get size of an object
	 *
	 * @param id
	 *            id of the object
	 * @return length of the object content in bytes, -1 if the object doesn't
	 *         exist
	 * @throws java.io.IOException
	 */
	long getSize(AnyLongObjectId id) throws IOException;
}
