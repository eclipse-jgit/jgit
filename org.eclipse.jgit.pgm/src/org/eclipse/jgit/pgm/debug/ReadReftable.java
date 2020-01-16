/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Argument;

@Command
class ReadReftable extends TextBuiltin {
	@Argument(index = 0)
	private String input;

	@Argument(index = 1, required = false)
	private String ref;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		try (FileInputStream in = new FileInputStream(input);
				BlockSource src = BlockSource.from(in);
				ReftableReader reader = new ReftableReader(src)) {
			try (RefCursor rc = ref != null
					? reader.seekRefsWithPrefix(ref)
					: reader.allRefs()) {
				while (rc.next()) {
					write(rc.getRef());
				}
			}
		}
	}

	private void write(Ref r) throws IOException {
		if (r.isSymbolic()) {
			outw.println(r.getTarget().getName() + '\t' + r.getName());
			return;
		}

		ObjectId id1 = r.getObjectId();
		if (id1 != null) {
			outw.println(id1.name() + '\t' + r.getName());
		}

		ObjectId id2 = r.getPeeledObjectId();
		if (id2 != null) {
			outw.println('^' + id2.name());
		}
	}
}
