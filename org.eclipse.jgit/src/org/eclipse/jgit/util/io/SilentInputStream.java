/*
 * Copyright (C) 2025 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link InputStream} that swallows exceptions on {@link #close()}.
 *
 * @since 7.4
 */
public class SilentInputStream extends FilterInputStream {

	private static final Logger LOG = LoggerFactory
			.getLogger(SilentInputStream.class);

	/**
	 * Wraps an existing {@link InputStream}.
	 *
	 * @param in
	 *            {@link InputStream} to wrap
	 */
	public SilentInputStream(InputStream in) {
		super(in);
	}

	@Override
	public void close() throws IOException {
		try {
			super.close();
		} catch (IOException e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Exception ignored while closing input stream", e); //$NON-NLS-1$
			}
		}
	}
}
