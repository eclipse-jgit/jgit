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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.io.ModifiableFileWatcher;
import org.eclipse.jgit.util.IO;

/**
 * An implementation of an OpenSSH key revocation list (KRL), either a binary
 * KRL or a simple list of public keys.
 */
class OpenSshKrl extends ModifiableFileWatcher {

	private static record State(Set<String> keys, OpenSshBinaryKrl krl) {
		// Empty
	}

	private State state;

	public OpenSshKrl(Path path) {
		super(path);
		state = new State(Set.of(), null);
	}

	public boolean isRevoked(PublicKey key) throws IOException {
		State current = refresh();
		return isRevoked(current, key);
	}

	private boolean isRevoked(State current, PublicKey key) {
		if (key instanceof OpenSshCertificate cert) {
			OpenSshBinaryKrl krl = current.krl();
			if (krl != null && krl.isRevoked(cert)) {
				return true;
			}
			if (isRevoked(current, cert.getCaPubKey())
					|| isRevoked(current, cert.getCertPubKey())) {
				return true;
			}
			return false;
		}
		OpenSshBinaryKrl krl = current.krl();
		if (krl != null) {
			return krl.isRevoked(key);
		}
		return current.keys().contains(PublicKeyEntry.toString(key));
	}

	private synchronized State refresh() throws IOException {
		if (checkReloadRequired()) {
			updateReloadAttributes();
			try {
				state = reload(getPath());
			} catch (NoSuchFileException e) {
				// File disappeared
				resetReloadAttributes();
				state = new State(Set.of(), null);
			}
		}
		return state;
	}

	private static State reload(Path path) throws IOException {
		try (BufferedInputStream in = new BufferedInputStream(
				Files.newInputStream(path))) {
			byte[] magic = new byte[OpenSshBinaryKrl.MAGIC.length];
			in.mark(magic.length);
			IO.readFully(in, magic);
			if (Arrays.equals(magic, OpenSshBinaryKrl.MAGIC)) {
				return new State(null, OpenSshBinaryKrl.load(in, true));
			}
			// Otherwise try reading it textually
			in.reset();
			return loadTextKrl(in);
		}
	}

	private static State loadTextKrl(InputStream in) throws IOException {
		Set<String> keys = new HashSet<>();
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			for (;;) {
				line = r.readLine();
				if (line == null) {
					break;
				}
				line = line.strip();
				if (line.isEmpty() || line.charAt(0) == '#') {
					continue;
				}
				keys.add(AllowedSigners.parsePublicKey(line, 0));
			}
		}
		return new State(keys, null);
	}
}
