/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;

class TransportBundleFile extends Transport implements TransportBundle {
	static final TransportProtocol PROTO_BUNDLE = new TransportProtocol() {
		private final String[] schemeNames = { "bundle", "file" }; //$NON-NLS-1$ //$NON-NLS-2$

		private final Set<String> schemeSet = Collections
				.unmodifiableSet(new LinkedHashSet<String>(Arrays
						.asList(schemeNames)));

		@Override
		public String getName() {
			return JGitText.get().transportProtoBundleFile;
		}

		public Set<String> getSchemes() {
			return schemeSet;
		}

		@Override
		public boolean canHandle(URIish uri, Repository local, String remoteName) {
			if (uri.getPath() == null
					|| uri.getPort() > 0
					|| uri.getUser() != null
					|| uri.getPass() != null
					|| uri.getHost() != null
					|| (uri.getScheme() != null && !getSchemes().contains(uri.getScheme())))
				return false;
			return true;
		}

		@Override
		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException, TransportException {
			if ("bundle".equals(uri.getScheme())) {
				File path = local.getFS().resolve(new File("."), uri.getPath());
				return new TransportBundleFile(local, uri, path);
			}

			// This is an ambiguous reference, it could be a bundle file
			// or it could be a Git repository. Allow TransportLocal to
			// resolve the path and figure out which type it is by testing
			// the target.
			//
			return TransportLocal.PROTO_LOCAL.open(uri, local, remoteName);
		}
	};

	private final File bundle;

	TransportBundleFile(Repository local, URIish uri, File bundlePath) {
		super(local, uri);
		bundle = bundlePath;
	}

	@Override
	public FetchConnection openFetch() throws NotSupportedException,
			TransportException {
		final InputStream src;
		try {
			src = new FileInputStream(bundle);
		} catch (FileNotFoundException err) {
			throw new TransportException(uri, JGitText.get().notFound);
		}
		return new BundleFetchConnection(this, src);
	}

	@Override
	public PushConnection openPush() throws NotSupportedException {
		throw new NotSupportedException(
				JGitText.get().pushIsNotSupportedForBundleTransport);
	}

	@Override
	public void close() {
		// Resources must be established per-connection.
	}

}
