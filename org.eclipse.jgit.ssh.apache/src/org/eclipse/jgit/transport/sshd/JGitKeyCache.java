/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.security.auth.DestroyFailedException;

/**
 * A simple {@link KeyCache}. JGit uses one such cache in its
 * {@link SshdSessionFactory} to avoid loading keys multiple times.
 *
 * @since 5.2
 */
public class JGitKeyCache implements KeyCache {

	private AtomicReference<Map<Path, KeyPair>> cache = new AtomicReference<>(
			new ConcurrentHashMap<>());

	@Override
	public KeyPair get(Path path,
			Function<? super Path, ? extends KeyPair> loader) {
		return cache.get().computeIfAbsent(path, loader);
	}

	@Override
	public void close() {
		Map<Path, KeyPair> map = cache.getAndSet(null);
		if (map == null) {
			return;
		}
		for (KeyPair k : map.values()) {
			PrivateKey p = k.getPrivate();
			try {
				p.destroy();
			} catch (DestroyFailedException e) {
				// Ignore here. We did our best.
			}
		}
		map.clear();
	}
}
