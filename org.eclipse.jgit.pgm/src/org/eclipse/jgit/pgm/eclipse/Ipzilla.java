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
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.PasswordAuthentication;
import java.net.URL;

import org.eclipse.jgit.iplog.IpLogMeta;
import org.eclipse.jgit.iplog.SimpleCookieManager;
import org.eclipse.jgit.pgm.CLIText;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Option;

@Command(name = "eclipse-ipzilla", common = false, usage = "usage_synchronizeIPZillaData")
class Ipzilla extends TextBuiltin {
	@Option(name = "--url", metaVar = "metaVar_url", usage = "usage_IPZillaURL")
	private String url = "https://dev.eclipse.org/ipzilla/";

	@Option(name = "--username", metaVar = "metaVar_user", usage = "usage_IPZillaUsername")
	private String username;

	@Option(name = "--password", metaVar = "metaVar_pass", usage = "usage_IPZillaPassword")
	private String password;

	@Option(name = "--file", aliases = { "-f" }, metaVar = "metaVar_file", usage = "usage_inputOutputFile")
	private File output;

	@Override
	protected void run() throws Exception {
		if (CookieHandler.getDefault() == null)
			CookieHandler.setDefault(new SimpleCookieManager());

		final URL ipzilla = new URL(url);
		if (username == null) {
			final PasswordAuthentication auth = Authenticator
					.requestPasswordAuthentication(ipzilla.getHost(), //
							null, //
							ipzilla.getPort(), //
							ipzilla.getProtocol(), //
							CLIText.get().IPZillaPasswordPrompt, //
							ipzilla.getProtocol(), //
							ipzilla, //
							Authenticator.RequestorType.SERVER);
			username = auth.getUserName();
			password = new String(auth.getPassword());
		}

		if (output == null)
			output = new File(db.getWorkTree(), IpLogMeta.IPLOG_CONFIG_FILE);

		IpLogMeta meta = new IpLogMeta();
		meta.syncCQs(output, db.getFS(), ipzilla, username, password);
	}
}
