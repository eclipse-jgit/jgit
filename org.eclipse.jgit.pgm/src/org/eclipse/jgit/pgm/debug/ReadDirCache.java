/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import static java.lang.Long.valueOf;

import java.text.MessageFormat;

import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;

@Command(usage = "usage_ReadDirCache")
class ReadDirCache extends TextBuiltin {
	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		final int cnt = 100;
		final long start = System.currentTimeMillis();
		for (int i = 0; i < cnt; i++)
			db.readDirCache();
		final long end = System.currentTimeMillis();
		outw.print(" "); //$NON-NLS-1$
		outw.println(MessageFormat.format(CLIText.get().averageMSPerRead,
				valueOf((end - start) / cnt)));
	}
}
