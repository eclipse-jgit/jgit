/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import static java.lang.Integer.valueOf;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_ShowDirCache")
class ShowDirCache extends TextBuiltin {

	@Option(name = "--millis", aliases = { "-m" }, usage = "usage_showTimeInMilliseconds")
	private boolean millis = false;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		final DateTimeFormatter fmt = DateTimeFormatter
				.ofPattern("yyyy-MM-dd,HH:mm:ss.nnnnnnnnn") //$NON-NLS-1$
				.withLocale(Locale.getDefault())
				.withZone(ZoneId.systemDefault());

		final DirCache cache = db.readDirCache();
		for (int i = 0; i < cache.getEntryCount(); i++) {
			final DirCacheEntry ent = cache.getEntry(i);
			final FileMode mode = FileMode.fromBits(ent.getRawMode());
			final int len = ent.getLength();
			Instant mtime = ent.getLastModifiedInstant();
			final int stage = ent.getStage();

			outw.print(mode);
			outw.format(" %6d", valueOf(len)); //$NON-NLS-1$
			outw.print(' ');
			if (millis) {
				outw.print(mtime.toEpochMilli());
			} else {
				outw.print(fmt.format(mtime));
			}
			outw.print(' ');
			outw.print(ent.getObjectId().name());
			outw.print(' ');
			outw.print(stage);
			outw.print('\t');
			outw.print(ent.getPathString());
			outw.println();
		}
	}
}
