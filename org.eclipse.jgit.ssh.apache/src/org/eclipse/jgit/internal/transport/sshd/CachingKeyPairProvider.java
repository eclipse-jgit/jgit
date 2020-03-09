/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;

import javax.security.auth.DestroyFailedException;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.io.resource.IoResource;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.transport.sshd.KeyCache;

/**
 * A {@link FileKeyPairProvider} that uses an external {@link KeyCache}.
 */
public class CachingKeyPairProvider extends FileKeyPairProvider
		implements Iterable<KeyPair> {

	private final KeyCache cache;

	/**
	 * Creates a new {@link CachingKeyPairProvider} using the given
	 * {@link KeyCache}. If the cache is {@code null}, this is a simple
	 * {@link FileKeyPairProvider}.
	 *
	 * @param paths
	 *            to load keys from
	 * @param cache
	 *            to use, may be {@code null} if no external caching is desired
	 */
	public CachingKeyPairProvider(List<Path> paths, KeyCache cache) {
		super(paths);
		this.cache = cache;
	}

	@Override
	public Iterator<KeyPair> iterator() {
		return iterator(null);
	}

	private Iterator<KeyPair> iterator(SessionContext session) {
		Collection<? extends Path> resources = getPaths();
		if (resources.isEmpty()) {
			return Collections.emptyListIterator();
		}
		return new CancellingKeyPairIterator(session, resources);
	}

	@Override
	public Iterable<KeyPair> loadKeys(SessionContext session) {
		return () -> iterator(session);
	}

	private KeyPair loadKey(SessionContext session, Path path)
			throws IOException, GeneralSecurityException {
		if (!Files.exists(path)) {
			log.warn(format(SshdText.get().identityFileNotFound, path));
			return null;
		}
		IoResource<Path> resource = getIoResource(session, path);
		if (cache == null) {
			return loadKey(session, resource, path, getPasswordFinder());
		}
		Throwable[] t = { null };
		KeyPair key = cache.get(path, p -> {
			try {
				return loadKey(session, resource, p, getPasswordFinder());
			} catch (IOException | GeneralSecurityException e) {
				t[0] = e;
				return null;
			}
		});
		if (t[0] != null) {
			if (t[0] instanceof CancellationException) {
				throw (CancellationException) t[0];
			}
			throw new IOException(
					format(SshdText.get().keyLoadFailed, resource), t[0]);
		}
		return key;
	}

	private KeyPair loadKey(SessionContext session, NamedResource resource,
			Path path, FilePasswordProvider passwordProvider)
			throws IOException, GeneralSecurityException {
		try (InputStream stream = Files.newInputStream(path)) {
			Iterable<KeyPair> ids = SecurityUtils.loadKeyPairIdentities(session,
					resource, stream, passwordProvider);
			if (ids == null) {
				throw new InvalidKeyException(
						format(SshdText.get().identityFileNoKey, path));
			}
			Iterator<KeyPair> keys = ids.iterator();
			if (!keys.hasNext()) {
				throw new InvalidKeyException(format(
						SshdText.get().identityFileUnsupportedFormat, path));
			}
			KeyPair result = keys.next();
			if (keys.hasNext()) {
				log.warn(format(SshdText.get().identityFileMultipleKeys, path));
				keys.forEachRemaining(k -> {
					PrivateKey pk = k.getPrivate();
					if (pk != null) {
						try {
							pk.destroy();
						} catch (DestroyFailedException e) {
							// Ignore
						}
					}
				});
			}
			return result;
		}
	}

	private class CancellingKeyPairIterator implements Iterator<KeyPair> {

		private final SessionContext context;

		private final Iterator<Path> paths;

		private KeyPair nextItem;

		private boolean nextSet;

		public CancellingKeyPairIterator(SessionContext session,
				Collection<? extends Path> resources) {
			List<Path> copy = new ArrayList<>(resources.size());
			copy.addAll(resources);
			paths = copy.iterator();
			context = session;
		}

		@Override
		public boolean hasNext() {
			if (nextSet) {
				return nextItem != null;
			}
			nextSet = true;
			while (nextItem == null && paths.hasNext()) {
				try {
					nextItem = loadKey(context, paths.next());
				} catch (CancellationException cancelled) {
					throw cancelled;
				} catch (Exception other) {
					log.warn(other.toString());
				}
			}
			return nextItem != null;
		}

		@Override
		public KeyPair next() {
			if (!nextSet && !hasNext()) {
				throw new NoSuchElementException();
			}
			KeyPair result = nextItem;
			nextItem = null;
			nextSet = false;
			if (result == null) {
				throw new NoSuchElementException();
			}
			return result;
		}

	}
}
