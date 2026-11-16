/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.jgit.lib.ObjectId;

class FetchHeadRecord {
	ObjectId newValue;

	boolean notForMerge;

	String sourceName;

	URIish sourceURI;

	boolean write(Writer pw) throws IOException {
		if (!isSafeSourceName(sourceName)) {
			// A control character in the source name would forge a
			// FETCH_HEAD line, so refuse the record. The caller warns.
			return false;
		}
		final String type;
		final String name;
		if (sourceName.startsWith(R_HEADS)) {
			type = "branch"; //$NON-NLS-1$
			name = sourceName.substring(R_HEADS.length());
		} else if (sourceName.startsWith(R_TAGS)) {
			type = "tag"; //$NON-NLS-1$
			name = sourceName.substring(R_TAGS.length());
		} else if (sourceName.startsWith(R_REMOTES)) {
			type = "remote branch"; //$NON-NLS-1$
			name = sourceName.substring(R_REMOTES.length());
		} else {
			type = ""; //$NON-NLS-1$
			name = sourceName;
		}

		pw.write(newValue.name());
		pw.write('\t');
		if (notForMerge)
			pw.write("not-for-merge"); //$NON-NLS-1$
		pw.write('\t');
		pw.write(type);
		pw.write(" '"); //$NON-NLS-1$
		pw.write(name);
		pw.write("' of "); //$NON-NLS-1$
		pw.write(sourceURI.toString());
		pw.write("\n"); //$NON-NLS-1$
		return true;
	}

	/**
	 * Whether a source name is safe for the line-based FETCH_HEAD file. A
	 * valid ref name has no control character or space (see
	 * {@link org.eclipse.jgit.lib.Repository#isValidRefName}), which could
	 * otherwise forge a spurious entry.
	 *
	 * @param name
	 *            remote-advertised source ref name.
	 * @return {@code true} if it is safe to write into FETCH_HEAD.
	 */
	private static boolean isSafeSourceName(String name) {
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c <= ' ' || c == 0x7f) {
				return false;
			}
		}
		return true;
	}
}
