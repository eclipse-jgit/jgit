/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
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

package org.eclipse.jgit.junit;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Abstract test util class
 */
public abstract class JGitTestUtil {
	/** Constant <code>CLASSPATH_TO_RESOURCES="org/eclipse/jgit/test/resources/"</code> */
	public static final String CLASSPATH_TO_RESOURCES = "org/eclipse/jgit/test/resources/";

	private JGitTestUtil() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Get name of current test by inspecting stack trace
	 *
	 * @return the name
	 */
	public static String getName() {
		GatherStackTrace stack;
		try {
			throw new GatherStackTrace();
		} catch (GatherStackTrace wanted) {
			stack = wanted;
		}

		try {
			for (StackTraceElement stackTrace : stack.getStackTrace()) {
				String className = stackTrace.getClassName();
				String methodName = stackTrace.getMethodName();
				Method method;
				try {
					method = Class.forName(className) //
							.getMethod(methodName, (Class[]) null);
				} catch (NoSuchMethodException e) {
					// could be private, i.e. not a test method
					// could have arguments, not handled
					continue;
				}

				Test annotation = method.getAnnotation(Test.class);
				if (annotation != null)
					return methodName;
			}
		} catch (ClassNotFoundException shouldNeverOccur) {
			// Fall through and crash.
		}

		throw new AssertionError("Cannot determine name of current test");
	}

	@SuppressWarnings("serial")
	private static class GatherStackTrace extends Exception {
		// Thrown above to collect the stack frame.
	}

	/**
	 * Assert byte arrays are equal
	 *
	 * @param exp
	 *            expected value
	 * @param act
	 *            actual value
	 */
	public static void assertEquals(byte[] exp, byte[] act) {
		Assert.assertEquals(s(exp), s(act));
	}

	private static String s(byte[] raw) {
		return RawParseUtils.decode(raw);
	}

	/**
	 * Get test resource file.
	 *
	 * @param fileName
	 * @return the test resource file
	 */
	public static File getTestResourceFile(final String fileName) {
		if (fileName == null || fileName.length() <= 0) {
			return null;
		}
		final URL url = cl().getResource(CLASSPATH_TO_RESOURCES + fileName);
		if (url == null) {
			// If URL is null then try to load it as it was being
			// loaded previously
			return new File("tst", fileName);
		}
		if ("jar".equals(url.getProtocol())) {
			try {
				File tmp = File.createTempFile("tmp_", "_" + fileName);
				copyTestResource(fileName, tmp);
				return tmp;
			} catch (IOException err) {
				throw new RuntimeException("Cannot create temporary file", err);
			}
		}
		try {
			return new File(url.toURI());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getMessage() + " " + url);
		} catch (URISyntaxException e) {
			return new File(url.getPath());
		}
	}

	/**
	 * Copy test resource.
	 *
	 * @param name
	 * @param dest
	 * @throws IOException
	 */
	public static void copyTestResource(String name, File dest)
			throws IOException {
		URL url = cl().getResource(CLASSPATH_TO_RESOURCES + name);
		if (url == null)
			throw new FileNotFoundException(name);
		try (InputStream in = url.openStream();
				FileOutputStream out = new FileOutputStream(dest)) {
			byte[] buf = new byte[4096];
			for (int n; (n = in.read(buf)) > 0;)
				out.write(buf, 0, n);
		}
	}

	private static ClassLoader cl() {
		return JGitTestUtil.class.getClassLoader();
	}

	/**
	 * Write a trash file.
	 *
	 * @param db
	 * @param name
	 * @param data
	 * @return the trash file
	 * @throws IOException
	 */
	public static File writeTrashFile(final Repository db,
			final String name, final String data) throws IOException {
		File path = new File(db.getWorkTree(), name);
		write(path, data);
		return path;
	}

	/**
	 * Write a trash file.
	 *
	 * @param db
	 * @param subdir
	 * @param name
	 * @param data
	 * @return the trash file
	 * @throws IOException
	 */
	public static File writeTrashFile(final Repository db,
			final String subdir,
			final String name, final String data) throws IOException {
		File path = new File(db.getWorkTree() + "/" + subdir, name);
		write(path, data);
		return path;
	}

	/**
	 * Write a string as a UTF-8 file.
	 *
	 * @param f
	 *            file to write the string to. Caller is responsible for making
	 *            sure it is in the trash directory or will otherwise be cleaned
	 *            up at the end of the test. If the parent directory does not
	 *            exist, the missing parent directories are automatically
	 *            created.
	 * @param body
	 *            content to write to the file.
	 * @throws IOException
	 *             the file could not be written.
	 */
	public static void write(final File f, final String body)
			throws IOException {
		FileUtils.mkdirs(f.getParentFile(), true);
		try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
				UTF_8)) {
			w.write(body);
		}
	}

	/**
	 * Fully read a UTF-8 file and return as a string.
	 *
	 * @param file
	 *            file to read the content of.
	 * @return UTF-8 decoded content of the file, empty string if the file
	 *         exists but has no content.
	 * @throws IOException
	 *             the file does not exist, or could not be read.
	 */
	public static String read(final File file) throws IOException {
		final byte[] body = IO.readFully(file);
		return new String(body, 0, body.length, UTF_8);
	}

	/**
	 * Read a file's content
	 *
	 * @param db
	 * @param name
	 * @return the content of the file
	 * @throws IOException
	 */
	public static String read(final Repository db, final String name)
			throws IOException {
		File file = new File(db.getWorkTree(), name);
		return read(file);
	}

	/**
	 * Check if file exists
	 *
	 * @param db
	 * @param name
	 *            name of the file
	 * @return {@code true} if the file exists
	 */
	public static boolean check(final Repository db, final String name) {
		File file = new File(db.getWorkTree(), name);
		return file.exists();
	}

	/**
	 * Delete a trash file.
	 *
	 * @param db
	 * @param name
	 * @throws IOException
	 */
	public static void deleteTrashFile(final Repository db,
			final String name) throws IOException {
		File path = new File(db.getWorkTree(), name);
		FileUtils.delete(path);
	}

	/**
	 * Write a symbolic link
	 *
	 * @param db
	 *            the repository
	 * @param link
	 *            the path of the symbolic link to create
	 * @param target
	 *            the target of the symbolic link
	 * @return the path to the symbolic link
	 * @throws Exception
	 * @since 4.2
	 */
	public static Path writeLink(Repository db, String link,
			String target) throws Exception {
		return FileUtils.createSymLink(new File(db.getWorkTree(), link),
				target);
	}

	/**
	 * Concatenate byte arrays.
	 *
	 * @param b
	 *            byte arrays to combine together.
	 * @return a single byte array that contains all bytes copied from input
	 *         byte arrays.
	 * @since 4.9
	 */
	public static byte[] concat(byte[]... b) {
		int n = 0;
		for (byte[] a : b) {
			n += a.length;
		}

		byte[] data = new byte[n];
		n = 0;
		for (byte[] a : b) {
			System.arraycopy(a, 0, data, n, a.length);
			n += a.length;
		}
		return data;
	}
}
