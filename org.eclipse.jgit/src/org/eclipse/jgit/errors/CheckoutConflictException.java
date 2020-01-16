/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org> and others
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
 * Exception thrown if a conflict occurs during a merge checkout.
 */
public class CheckoutConflictException extends IOException {
	private static final long serialVersionUID = 1L;

	private final String[] conflicting;

	/**
	 * Construct a CheckoutConflictException for the specified file
	 *
	 * @param file
	 *            relative path of a file
	 */
	public CheckoutConflictException(String file) {
		super(MessageFormat.format(JGitText.get().checkoutConflictWithFile, file));
		conflicting = new String[] { file };
	}

	/**
	 * Construct a CheckoutConflictException for the specified set of files
	 *
	 * @param files
	 *            an array of relative file paths
	 */
	public CheckoutConflictException(String[] files) {
		super(MessageFormat.format(JGitText.get().checkoutConflictWithFiles, buildList(files)));
		conflicting = files;
	}

	/**
	 * Get the relative paths of the conflicting files
	 *
	 * @return the relative paths of the conflicting files (relative to the
	 *         working directory root).
	 * @since 4.4
	 */
	public String[] getConflictingFiles() {
		return conflicting;
	}

	private static String buildList(String[] files) {
		StringBuilder builder = new StringBuilder();
		for (String f : files) {
			builder.append("\n"); //$NON-NLS-1$
			builder.append(f);
		}
		return builder.toString();
	}
}
