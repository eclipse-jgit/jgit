/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;

@Command(usage = "usage_ShowRef")
class ShowRef extends TextBuiltin {
	/** {@inheritDoc} */
	@Override
	protected void run() {
		try {
			for (Ref r : getSortedRefs()) {
				show(r.getObjectId(), r.getName());
				if (r.getPeeledObjectId() != null) {
					show(r.getPeeledObjectId(), r.getName() + "^{}"); //$NON-NLS-1$
				}
			}
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}

	private Iterable<Ref> getSortedRefs() throws IOException {
		List<Ref> all = db.getRefDatabase().getRefs();
		// TODO(jrn) check if we can reintroduce fast-path by e.g. implementing
		// SortedList
		return RefComparator.sort(all);
	}

	private void show(AnyObjectId id, String name)
			throws IOException {
		outw.print(id.name());
		outw.print('\t');
		outw.print(name);
		outw.println();
	}
}
