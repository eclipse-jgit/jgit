/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.pgm.eclipse;

import java.io.File;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.text.MessageFormat;

import org.eclipse.jgit.iplog.IpLogGenerator;
import org.eclipse.jgit.iplog.SimpleCookieManager;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.pgm.CLIText;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.util.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(name = "eclipse-iplog", common = false, usage = "usage_produceAnEclipseIPLog")
class Iplog extends TextBuiltin {
	@Option(name = "--version", aliases = { "-r" }, metaVar = "metaVar_version", usage = "usage_symbolicVersionForTheProject")
	private String version;

	@Option(name = "--output", aliases = { "-o" }, metaVar = "metaVar_file", usage = "usage_outputFile")
	private File output;

	@Argument(index = 0, metaVar = "metaVar_commitOrTag")
	private ObjectId commitId;

	@Override
	protected void run() throws Exception {
		if (CookieHandler.getDefault() == null)
			CookieHandler.setDefault(new SimpleCookieManager());

		final IpLogGenerator log = new IpLogGenerator();

		if (commitId == null) {
			System.err.println(MessageFormat.format(
				CLIText.get().warningNoCommitGivenOnCommandLine, Constants.HEAD));
			commitId = db.resolve(Constants.HEAD);
		}

		final RevWalk rw = new RevWalk(db);
		final RevObject start = rw.parseAny(commitId);
		if (version == null && start instanceof RevTag)
			version = ((RevTag) start).getTagName();
		else if (version == null)
			throw die(MessageFormat.format(CLIText.get().notATagVersionIsRequired, start.name()));

		log.scan(db, rw.parseCommit(start), version);

		if (output != null) {
			FileUtils.mkdirs(output.getParentFile(), true);
			LockFile lf = new LockFile(output, db.getFS());
			if (!lf.lock())
				throw die(MessageFormat.format(CLIText.get().cannotLock, output));
			try {
				OutputStream os = lf.getOutputStream();
				try {
					log.writeTo(os);
				} finally {
					os.close();
				}
				if (!lf.commit())
					throw die(MessageFormat.format(CLIText.get().cannotWrite, output));
			} finally {
				lf.unlock();
			}
		} else {
			log.writeTo(outs);
			outs.flush();
		}
	}
}
