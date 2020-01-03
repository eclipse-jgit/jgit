/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;

import org.eclipse.jgit.lib.ReflogEntry;

/**
 * Iterator over logs inside a
 * {@link org.eclipse.jgit.internal.storage.reftable.Reftable}.
 */
public abstract class LogCursor implements AutoCloseable {
	/**
	 * Check if another log record is available.
	 *
	 * @return {@code true} if there is another result.
	 * @throws java.io.IOException
	 *             logs cannot be read.
	 */
	public abstract boolean next() throws IOException;

	/**
	 * Get name of the current reference.
	 *
	 * @return name of the current reference.
	 */
	public abstract String getRefName();

	/**
	 * Get identifier of the transaction that created the log record.
	 *
	 * @return identifier of the transaction that created the log record.
	 */
	public abstract long getUpdateIndex();

	/**
	 * Get current log entry.
	 *
	 * @return current log entry.
	 */
	public abstract ReflogEntry getReflogEntry();

	/** {@inheritDoc} */
	@Override
	public abstract void close();
}
