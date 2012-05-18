/*
 * Copyright (C) 2012, Google Inc.
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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.PubSubConfig;
import org.eclipse.jgit.transport.PubSubConfig.Publisher;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import org.kohsuke.args4j.Argument;

@Command(common = false, usage = "usage_UnsubscribeFromRemoteRepository")
class Unsubscribe extends TextBuiltin {
	@Argument(index = 0, metaVar = "metaVar_remoteName")
	private String remote = Constants.DEFAULT_REMOTE_NAME;

	@Override
	protected void run() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, remote);
		PubSubConfig pubsubConfig = SubscribeDaemon.getConfig();
		List<URIish> uris = remoteConfig.getURIs();
		if (uris.isEmpty())
			throw die(MessageFormat.format(
					CLIText.get().noRemoteUriUnsubscribe, remote));

		String uriRoot = PubSubConfig.getUriRoot(uris.get(0));
		String dir = db.getDirectory().getAbsolutePath();

		Publisher p = pubsubConfig.getPublisher(uriRoot);
		if (p == null || !p.removeSubscriber(remote, dir))
			throw die(MessageFormat.format(
					CLIText.get().subscriptionDoesNotExist, remote));

		// See if this publisher is now empty
		if (p.getSubscribers().size() == 0)
			pubsubConfig.removePublisher(p);

		try {
			SubscribeDaemon.updateConfig(pubsubConfig);
		} catch (IOException e) {
			throw die(MessageFormat.format(CLIText.get().cannotWrite,
					SubscribeDaemon.getConfigFile()));
		}

		out.println(MessageFormat.format(
				CLIText.get().didUnsubscribe, remote, uriRoot));
		// TODO(wetherbeei): reload the SubscribeDaemon
	}
}