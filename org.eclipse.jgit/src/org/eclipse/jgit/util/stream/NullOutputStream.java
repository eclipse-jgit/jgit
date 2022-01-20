/*
 * Copyright (C) 2011, Stefan Lay <stefan.lay@.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.stream;

import java.io.OutputStream;

/**
 * An OutputStream which ignores everything written to it.
 */
public class NullOutputStream extends OutputStream {

	/** The canonical instance. */
	public static final NullOutputStream INSTANCE = new NullOutputStream();

	private NullOutputStream() {
		// Do nothing, but we want to hide our constructor to prevent
		// more than one instance from being created.
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) {
		// Discard.
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf) {
		// Discard.
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int pos, int cnt) {
		// Discard.
	}
}
