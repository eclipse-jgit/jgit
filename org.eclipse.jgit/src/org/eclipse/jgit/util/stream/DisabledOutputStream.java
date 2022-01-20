/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.stream;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.internal.JGitText;

/**
 * An OutputStream which always throws IllegalStateExeption during write.
 */
public final class DisabledOutputStream extends OutputStream {
	/** The canonical instance which always throws IllegalStateException. */
	public static final DisabledOutputStream INSTANCE = new DisabledOutputStream();

	private DisabledOutputStream() {
		// Do nothing, but we want to hide our constructor to prevent
		// more than one instance from being created.
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) throws IOException {
		// We shouldn't be writing output at this stage, there
		// is nobody listening to us.
		//
		throw new IllegalStateException(JGitText.get().writingNotPermitted);
	}
}
