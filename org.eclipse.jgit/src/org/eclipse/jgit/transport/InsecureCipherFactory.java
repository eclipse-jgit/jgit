/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

/**
 * <b>DO NOT USE</b> Factory to create any cipher.
 * <p>
 * This is a hack for {@link WalkEncryption} to create any cipher configured by
 * the end-user. Using this class allows JGit to violate ErrorProne's security
 * recommendations (<a
 * href="https://errorprone.info/bugpattern/InsecureCryptoUsage"
 * >InsecureCryptoUsage</a>), which is not secure.
 */
class InsecureCipherFactory {
	static Cipher create(String algo)
			throws NoSuchAlgorithmException, NoSuchPaddingException {
		return Cipher.getInstance(algo);
	}
}
