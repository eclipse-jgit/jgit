/*
 * Copyright (C) 2015, David Ostrovsky <david@ostrovsky.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
