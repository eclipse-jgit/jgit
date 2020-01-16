/*
 * Copyright (C) 2009, Daniel Cheng (aka SDiZ) <git@sdiz.net>
 * Copyright (C) 2009, Daniel Cheng (aka SDiZ) <j16sdiz+freenet@gmail.com>
 * Copyright (C) 2015 Thomas Meyer <thomas@m3y3r.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_RevParse")
class RevParse extends TextBuiltin {
	@Option(name = "--all", usage = "usage_RevParseAll")
	boolean all;

	@Option(name = "--verify", usage = "usage_RevParseVerify")
	boolean verify;

	@Argument(index = 0, metaVar = "metaVar_commitish")
	private List<ObjectId> commits = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try {
			if (all) {
				for (Ref r : db.getRefDatabase().getRefs()) {
					ObjectId objectId = r.getObjectId();
					// getRefs skips dangling symrefs, so objectId should never
					// be null.
					if (objectId == null) {
						throw new NullPointerException();
					}
					outw.println(objectId.name());
				}
			} else {
				if (verify && commits.size() > 1) {
					final CmdLineParser clp = new CmdLineParser(this);
					throw new CmdLineException(clp,
							CLIText.format(CLIText.get().needSingleRevision));
				}

				for (ObjectId o : commits) {
					outw.println(o.name());
				}
			}
		} catch (IOException | CmdLineException e) {
			throw die(e.getMessage(), e);
		}
	}
}
