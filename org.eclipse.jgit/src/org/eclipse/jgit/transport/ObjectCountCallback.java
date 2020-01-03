/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.OutputStream;

import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * A callback to tell caller the count of objects ASAP.
 *
 * @since 4.1
 */
public interface ObjectCountCallback {
	/**
	 * Invoked when the
	 * {@link org.eclipse.jgit.internal.storage.pack.PackWriter} has counted the
	 * objects to be written to pack.
	 * <p>
	 * An {@code ObjectCountCallback} can use this information to decide whether
	 * the
	 * {@link org.eclipse.jgit.internal.storage.pack.PackWriter#writePack(ProgressMonitor, ProgressMonitor, OutputStream)}
	 * operation should be aborted.
	 * <p>
	 * This callback will be called exactly once.
	 *
	 * @param objectCount
	 *            the count of the objects.
	 * @throws org.eclipse.jgit.transport.WriteAbortedException
	 *             to indicate that the write operation should be aborted.
	 */
	void setObjectCount(long objectCount) throws WriteAbortedException;
}
