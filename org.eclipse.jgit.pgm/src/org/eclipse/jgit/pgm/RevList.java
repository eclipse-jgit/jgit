/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;

@Command(usage = "usage_RevList")
class RevList extends RevWalkTextBuiltin {
	/** {@inheritDoc} */
	@Override
	protected void show(RevCommit c) throws Exception {
		if (c.has(RevFlag.UNINTERESTING))
			outw.print('-');
		c.getId().copyTo(outbuffer, outw);
		if (parents)
			for (int i = 0; i < c.getParentCount(); i++) {
				outw.print(' ');
				c.getParent(i).getId().copyTo(outbuffer, outw);
			}
		outw.println();
	}

	/** {@inheritDoc} */
	@Override
	protected void show(ObjectWalk ow, RevObject obj)
			throws Exception {
		if (obj.has(RevFlag.UNINTERESTING))
			outw.print('-');
		obj.getId().copyTo(outbuffer, outw);
		final String path = ow.getPathString();
		if (path != null) {
			outw.print(' ');
			outw.print(path);
		} else if (obj instanceof RevTree)
			outw.print(' ');
		outw.println();

	}
}
