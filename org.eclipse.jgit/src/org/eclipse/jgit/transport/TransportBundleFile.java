/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import org.eclipse.jgit.util.FS;

class TransportBundleFile extends Transport implements TransportBundle {
	static final TransportProtocol PROTO_BUNDLE = new TransportProtocol() {
		private final String[] schemeNames = { "bundle", "file" }; //$NON-NLS-1$ //$NON-NLS-2$

		private final Set<String> schemeSet = Collections
				.unmodifiableSet(new LinkedHashSet<>(Arrays
						.asList(schemeNames)));

		@Override
		public String getName() {
			return JGitText.get().transportProtoBundleFile;
		}

		@Override
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
			if ("bundle".equals(uri.getScheme())) { //$NON-NLS-1$
				File path = FS.DETECTED.resolve(new File("."), uri.getPath()); //$NON-NLS-1$
				return new TransportBundleFile(local, uri, path);
			}

			// This is an ambiguous reference, it could be a bundle file
			// or it could be a Git repository. Allow TransportLocal to
			// resolve the path and figure out which type it is by testing
			// the target.
			//
			return TransportLocal.PROTO_LOCAL.open(uri, local, remoteName);
		}

		@Override
		public Transport open(URIish uri) throws NotSupportedException,
				TransportException {
			if ("bundle".equals(uri.getScheme())) { //$NON-NLS-1$
				File path = FS.DETECTED.resolve(new File("."), uri.getPath()); //$NON-NLS-1$
				return new TransportBundleFile(uri, path);
			}
			return TransportLocal.PROTO_LOCAL.open(uri);
		}
	};

	private final File bundle;

	TransportBundleFile(Repository local, URIish uri, File bundlePath) {
		super(local, uri);
		bundle = bundlePath;
	}

	/**
	 * Constructor for TransportBundleFile.
	 *
	 * @param uri
	 *            a {@link org.eclipse.jgit.transport.URIish} object.
	 * @param bundlePath
	 *            transport bundle path
	 */
	public TransportBundleFile(URIish uri, File bundlePath) {
		super(uri);
		bundle = bundlePath;
	}

	/** {@inheritDoc} */
	@Override
	public FetchConnection openFetch() throws NotSupportedException,
			TransportException {
		final InputStream src;
		try {
			src = new FileInputStream(bundle);
		} catch (FileNotFoundException err) {
			TransportException te = new TransportException(uri,
					JGitText.get().notFound);
			te.initCause(err);
			throw te;
		}
		return new BundleFetchConnection(this, src);
	}

	/** {@inheritDoc} */
	@Override
	public PushConnection openPush() throws NotSupportedException {
		throw new NotSupportedException(
				JGitText.get().pushIsNotSupportedForBundleTransport);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		// Resources must be established per-connection.
	}

}
