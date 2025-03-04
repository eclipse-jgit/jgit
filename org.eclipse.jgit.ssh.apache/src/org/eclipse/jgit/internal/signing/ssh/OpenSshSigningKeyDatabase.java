/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.signing.ssh.CachingSigningKeyDatabase;
import org.eclipse.jgit.signing.ssh.VerificationException;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;

/**
 * A {@link CachingSigningKeyDatabase} using the OpenSSH allowed signers file
 * and the OpenSSH key revocation list.
 */
public class OpenSshSigningKeyDatabase implements CachingSigningKeyDatabase {

	// Keep caches of allowed signers and KRLs. Cache by canonical path.

	private static final int DEFAULT_CACHE_SIZE = 5;

	private AtomicInteger cacheSize = new AtomicInteger(DEFAULT_CACHE_SIZE);

	private class LRU<K, V> extends LinkedHashMap<K, V> {

		private static final long serialVersionUID = 1L;

		LRU() {
			super(DEFAULT_CACHE_SIZE, 0.75f, true);
		}

		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
			return size() > cacheSize.get();
		}
	}

	private final HashMap<Path, AllowedSigners> allowedSigners = new LRU<>();

	private final HashMap<Path, OpenSshKrl> revocations = new LRU<>();

	@Override
	public boolean isRevoked(Repository repository, GpgConfig config,
			PublicKey key) throws IOException {
		String fileName = config.getSshRevocationFile();
		if (StringUtils.isEmptyOrNull(fileName)) {
			return false;
		}
		File file = getFile(repository, fileName);
		OpenSshKrl revocationList;
		synchronized (revocations) {
			revocationList = revocations.computeIfAbsent(file.toPath(),
					OpenSshKrl::new);
		}
		return revocationList.isRevoked(key);
	}

	@Override
	public String isAllowed(Repository repository, GpgConfig config,
			PublicKey key, String namespace, PersonIdent ident)
			throws IOException, VerificationException {
		String fileName = config.getSshAllowedSignersFile();
		if (StringUtils.isEmptyOrNull(fileName)) {
			// No file configured. Git would error out.
			return null;
		}
		File file = getFile(repository, fileName);
		AllowedSigners allowed;
		synchronized (allowedSigners) {
			allowed = allowedSigners.computeIfAbsent(file.toPath(),
					AllowedSigners::new);
		}
		Instant gitTime = null;
		if (ident != null) {
			gitTime = ident.getWhenAsInstant();
		}
		return allowed.isAllowed(key, namespace, null, gitTime);
	}

	private File getFile(@NonNull Repository repository, String fileName)
			throws IOException {
		File file;
		if (fileName.startsWith("~/") //$NON-NLS-1$
				|| fileName.startsWith('~' + File.separator)) {
			file = FS.DETECTED.resolve(FS.DETECTED.userHome(),
					fileName.substring(2));
		} else {
			file = new File(fileName);
			if (!file.isAbsolute()) {
				file = new File(repository.getWorkTree(), fileName);
			}
		}
		return file.getCanonicalFile();
	}

	@Override
	public int getCacheSize() {
		return cacheSize.get();
	}

	@Override
	public void setCacheSize(int size) {
		if (size > 0) {
			cacheSize.set(size);
			pruneCache(size);
		}
	}

	private void pruneCache(int size) {
		prune(allowedSigners, size);
		prune(revocations, size);
	}

	private void prune(HashMap<?, ?> map, int size) {
		synchronized (map) {
			if (map.size() <= size) {
				return;
			}
			Iterator<?> iter = map.entrySet().iterator();
			int i = 0;
			while (iter.hasNext() && i < size) {
				iter.next();
				i++;
			}
			while (iter.hasNext()) {
				iter.next();
				iter.remove();
			}
		}
	}

	@Override
	public void clearCache() {
		synchronized (allowedSigners) {
			allowedSigners.clear();
		}
		synchronized (revocations) {
			revocations.clear();
		}
	}

}
