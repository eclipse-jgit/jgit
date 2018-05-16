/*
 * Copyright (C) 2009, Daniel Cheng (aka SDiZ) <git@sdiz.net>
 * Copyright (C) 2009, Daniel Cheng (aka SDiZ) <j16sdiz+freenet@gmail.com>
 * Copyright (C) 2015 Thomas Meyer <thomas@m3y3r.de>
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

package org.eclipse.jgit.pgm;

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
	protected void run() throws Exception {
		if (all) {
			for (Ref r : db.getRefDatabase().getRefs()) {
				ObjectId objectId = r.getObjectId();
				// getRefs skips dangling symrefs, so objectId should never be
				// null.
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
	}
}
