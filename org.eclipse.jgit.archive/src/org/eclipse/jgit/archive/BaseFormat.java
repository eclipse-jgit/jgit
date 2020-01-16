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
	 * Apply options to archive output stream
	 *
	 * @param s
	 *            stream to apply options to
	 * @param o
	 *            options map
	 * @return stream with option applied
	 * @throws IOException
	 */
	protected ArchiveOutputStream applyFormatOptions(ArchiveOutputStream s,
			Map<String, Object> o) throws IOException {
		for (Map.Entry<String, Object> p : o.entrySet()) {
			try {
				new Statement(s, "set" + StringUtils.capitalize(p.getKey()), //$NON-NLS-1$
						new Object[] { p.getValue() }).execute();
			} catch (Exception e) {
				throw new IOException(MessageFormat.format(
						ArchiveText.get().cannotSetOption, p.getKey()), e);
			}
		}
		return s;
	}
}
