/*
 * Copyright (C) 2013, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.List;

/**
 * Utility for reading reflog entries
 *
 * @since 3.0
 */
public interface ReflogReader {

	/**
	 * Get the last entry in the reflog
	 *
	 * @return the latest reflog entry, or null if no log
	 * @throws java.io.IOException
	 */
	ReflogEntry getLastEntry() throws IOException;

	/**
	 * Get all reflog entries in reverse order
	 *
	 * @return all reflog entries in reverse order
	 * @throws java.io.IOException
	 */
	List<ReflogEntry> getReverseEntries() throws IOException;

	/**
	 * Get specific entry in the reflog relative to the last entry which is
	 * considered entry zero.
	 *
	 * @param number a int.
	 * @return reflog entry or null if not found
	 * @throws java.io.IOException
	 */
	ReflogEntry getReverseEntry(int number) throws IOException;

	/**
	 * Get all reflog entries in reverse order
	 *
	 * @param max
	 *            max number of entries to read
	 * @return all reflog entries in reverse order
	 * @throws java.io.IOException
	 */
	List<ReflogEntry> getReverseEntries(int max) throws IOException;
}
