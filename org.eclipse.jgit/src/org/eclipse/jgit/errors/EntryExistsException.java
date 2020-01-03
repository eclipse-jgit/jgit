/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
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
 * Attempt to add an entry to a tree that already exists.
 */
public class EntryExistsException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct an EntryExistsException when the specified name already
	 * exists in a tree.
	 *
	 * @param name workdir relative file name
	 */
	public EntryExistsException(String name) {
		super(MessageFormat.format(JGitText.get().treeEntryAlreadyExists, name));
	}
}
