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

/**
 * Thrown if {@link org.eclipse.jgit.internal.storage.reftable.ReftableWriter}
 * cannot fit a reference.
 */
public class BlockSizeTooSmallException extends IOException {
	private static final long serialVersionUID = 1L;

	private final int minBlockSize;

	BlockSizeTooSmallException(int b) {
		minBlockSize = b;
	}

	/**
	 * Get minimum block size in bytes reftable requires to write a ref.
	 *
	 * @return minimum block size in bytes reftable requires to write a ref.
	 */
	public int getMinimumBlockSize() {
		return minBlockSize;
	}
}
