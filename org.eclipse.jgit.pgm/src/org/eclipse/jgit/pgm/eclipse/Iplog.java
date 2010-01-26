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
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.PasswordAuthentication;
import java.net.URL;

import org.eclipse.jgit.iplog.IpLogGenerator;
import org.eclipse.jgit.iplog.SimpleCookieManager;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.LockFile;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(name = "eclipse-iplog", common = false, usage = "Produce an Eclipse IP log")
class Iplog extends TextBuiltin {
	@Option(name = "--ipzilla", usage = "Obtain CQs from IPZilla")
	private boolean use_ipzilla;

	@Option(name = "--ipzilla-url", metaVar = "URL", usage = "IPZilla URL")
	private String ipzilla_url = "https://dev.eclipse.org/ipzilla/";

	@Option(name = "--ipzilla-username", metaVar = "USER", usage = "IPZilla Username")
	private String ipzilla_username;

	@Option(name = "--ipzilla-password", metaVar = "PASS", usage = "IPZilla Password")
	private String ipzilla_password;

	@Option(name = "--output", aliases = { "-o" }, metaVar = "FILE", usage = "Output file")
	private File output;

	@Argument(index = 0, metaVar = "COMMIT|TAG")
	private RevCommit commit;

	@Override
	protected void run() throws Exception {
		if (CookieHandler.getDefault() == null)
			CookieHandler.setDefault(new SimpleCookieManager());

		final URL ipzilla = new URL(ipzilla_url);
		if (use_ipzilla && ipzilla_username == null) {
			final PasswordAuthentication auth = Authenticator
					.requestPasswordAuthentication(ipzilla.getHost(), //
							null, //
							ipzilla.getPort(), //
							ipzilla.getProtocol(), //
							"IPZilla Password", //
							ipzilla.getProtocol(), //
							ipzilla, //
							Authenticator.RequestorType.SERVER);
			ipzilla_username = auth.getUserName();
			ipzilla_password = new String(auth.getPassword());
		}

		final IpLogGenerator log = new IpLogGenerator();

		if (commit == null) {
			System.err.println("warning: No commit given on command line,"
					+ " assuming " + Constants.HEAD);
			commit = new RevWalk(db).parseCommit(db.resolve(Constants.HEAD));
		}

		log.scan(db, commit);
		if (use_ipzilla)
			log.loadCQs(ipzilla, ipzilla_username, ipzilla_password);

		if (output != null) {
			if (!output.getParentFile().exists())
				output.getParentFile().mkdirs();
			LockFile lf = new LockFile(output);
			if (!lf.lock())
				throw die("Cannot lock " + output);
			try {
				OutputStream os = lf.getOutputStream();
				try {
					log.writeTo(os);
				} finally {
					os.close();
				}
				if (!lf.commit())
					throw die("Cannot write " + output);
			} finally {
				lf.unlock();
			}
		} else {
			log.writeTo(System.out);
			System.out.flush();
		}
	}
}
