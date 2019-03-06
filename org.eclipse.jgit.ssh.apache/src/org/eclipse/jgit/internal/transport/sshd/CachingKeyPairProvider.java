/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.internal.transport.sshd;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;

import org.eclipse.jgit.transport.sshd.KeyCache;

/**
 * A {@link EncryptedFileKeyPairProvider} that uses an external
 * {@link KeyCache}.
 */
public class CachingKeyPairProvider extends EncryptedFileKeyPairProvider
		implements Iterable<KeyPair> {

	private final KeyCache cache;

	/**
	 * Creates a new {@link CachingKeyPairProvider} using the given
	 * {@link KeyCache}. If the cache is {@code null}, this is a simple
	 * {@link EncryptedFileKeyPairProvider}.
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
		Collection<? extends Path> resources = getPaths();
		if (resources.isEmpty()) {
			return Collections.emptyListIterator();
		}
		return new CancellingKeyPairIterator(resources);
	}

	@Override
	public Iterable<KeyPair> loadKeys() {
		return this;
	}

	@Override
	protected KeyPair doLoadKey(Path resource)
			throws IOException, GeneralSecurityException {
		if (!Files.exists(resource)) {
			log.warn(format(SshdText.get().identityFileNotFound, resource));
			return null;
		}
		// By calling doLoadKey(String, Path, FilePasswordProvider) instead of
		// super.doLoadKey(Path) we can bypass the key caching in
		// AbstractResourceKeyPairProvider, over which we have no real control.
		String resourceId = resource.toString();
		if (cache == null) {
			return doLoadKey(resourceId, resource, getPasswordFinder());
		}
		Throwable t[] = { null };
		KeyPair key = cache.get(resource, p -> {
			try {
				return doLoadKey(resourceId, p, getPasswordFinder());
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

	private class CancellingKeyPairIterator implements Iterator<KeyPair> {

		private final Iterator<Path> paths;

		private KeyPair nextItem;

		private boolean nextSet;

		public CancellingKeyPairIterator(Collection<? extends Path> resources) {
			List<Path> copy = new ArrayList<>(resources.size());
			copy.addAll(resources);
			paths = copy.iterator();
		}

		@Override
		public boolean hasNext() {
			if (nextSet) {
				return nextItem != null;
			}
			nextSet = true;
			while (nextItem == null && paths.hasNext()) {
				try {
					nextItem = doLoadKey(paths.next());
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
