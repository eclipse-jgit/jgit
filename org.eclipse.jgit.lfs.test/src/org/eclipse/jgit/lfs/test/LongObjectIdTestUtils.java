/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.test;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;

public class LongObjectIdTestUtils {

	/**
	 * Create id as hash of the given string.
	 *
	 * @param s
	 *            the string to hash
	 * @return id calculated by hashing string
	 */
	public static LongObjectId hash(String s) {
		MessageDigest md = Constants.newMessageDigest();
		md.update(s.getBytes(UTF_8));
		return LongObjectId.fromRaw(md.digest());
	}

	/**
	 * Create id as hash of a file content
	 *
	 * @param file
	 *            the file to hash
	 * @return id calculated by hashing file content
	 * @throws FileNotFoundException
	 *             if file doesn't exist
	 * @throws IOException
	 */
	public static LongObjectId hash(Path file)
			throws FileNotFoundException, IOException {
		MessageDigest md = Constants.newMessageDigest();
		try (InputStream is = new BufferedInputStream(
				Files.newInputStream(file))) {
			final byte[] buffer = new byte[4096];
			for (int read = 0; (read = is.read(buffer)) != -1;) {
				md.update(buffer, 0, read);
			}
		}
		return LongObjectId.fromRaw(md.digest());
	}
}
