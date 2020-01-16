/*
 * Copyright (C) 2017, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Thrown when a PackFile uses a pack version not supported by JGit.
 *
 * @since 4.5
 */
public class UnsupportedPackVersionException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct an exception.
	 *
	 * @param version
	 *            pack version
	 */
	public UnsupportedPackVersionException(long version) {
		super(MessageFormat.format(JGitText.get().unsupportedPackVersion,
				Long.valueOf(version)));
	}
}
