/*
 * Copyright (C) 2015, David Ostrovsky <david@ostrovsky.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.archive;

import java.beans.Statement;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.eclipse.jgit.archive.internal.ArchiveText;
import org.eclipse.jgit.util.StringUtils;

/**
 * Base format class
 *
 * @since 4.0
 */
public class BaseFormat {
	/**
	 * Compression-level for the archive file. Only values in [0-9] are allowed.
	 * @since 5.11
	 */
	protected static final String COMPRESSION_LEVEL = "compression-level"; //$NON-NLS-1$

	/**
	 * Apply options to archive output stream
	 *
	 * @param s
	 *            stream to apply options to
	 * @param o
	 *            options map
	 * @return stream with option applied
	 * @throws IOException
	 *             if an IO error occurred
	 */
	protected ArchiveOutputStream applyFormatOptions(ArchiveOutputStream s,
			Map<String, Object> o) throws IOException {
		for (Map.Entry<String, Object> p : o.entrySet()) {
			try {
				if (p.getKey().equals(COMPRESSION_LEVEL)) {
					continue;
				}
				new Statement(s, "set" + StringUtils.capitalize(p.getKey()), //$NON-NLS-1$
						new Object[] { p.getValue() }).execute();
			} catch (Exception e) {
				throw new IOException(MessageFormat.format(
						ArchiveText.get().cannotSetOption, p.getKey()), e);
			}
		}
		return s;
	}

	/**
	 * Removes and returns the {@link #COMPRESSION_LEVEL} key from the input map
	 * parameter if it exists, or -1 if this key does not exist.
	 *
	 * @param o
	 *            options map
	 * @return The compression level if it exists in the map, or -1 instead.
	 * @throws IllegalArgumentException
	 *             if the {@link #COMPRESSION_LEVEL} option does not parse to an
	 *             Integer.
	 * @since 5.11
	 */
	protected int getCompressionLevel(Map<String, Object> o) {
		if (!o.containsKey(COMPRESSION_LEVEL)) {
			return -1;
		}
		Object option = o.get(COMPRESSION_LEVEL);
		try {
			Integer compressionLevel = (Integer) option;
			return compressionLevel.intValue();
		} catch (ClassCastException e) {
			throw new IllegalArgumentException(
					MessageFormat.format(
							ArchiveText.get().invalidCompressionLevel, option),
					e);
		}
	}
}
