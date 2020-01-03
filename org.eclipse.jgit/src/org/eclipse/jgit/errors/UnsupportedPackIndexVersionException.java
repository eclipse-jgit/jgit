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
 * Thrown when a PackIndex uses an index version not supported by JGit.
 *
 * @since 4.5
 */
public class UnsupportedPackIndexVersionException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct an exception.
	 *
	 * @param version
	 *            pack index version
	 */
	public UnsupportedPackIndexVersionException(int version) {
		super(MessageFormat.format(JGitText.get().unsupportedPackIndexVersion,
				Integer.valueOf(version)));
	}
}
