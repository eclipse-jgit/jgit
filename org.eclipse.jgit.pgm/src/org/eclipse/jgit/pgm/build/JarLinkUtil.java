/*
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

package org.eclipse.jgit.pgm.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.MapOptionHandler;

/**
 * Combines multiple JAR and directory sources into a single JAR file.
 * <p>
 * This is a crude command line utility to combine multiple JAR files into a
 * single JAR file, without first needing to unpack the individual JARs.
 * <p>
 * The output ZIP stream is sent to standard out and can be redirected onto the
 * end of a shell script which starts the JRE.
 */
public class JarLinkUtil {
	/**
	 * Combine multiple JARs.
	 *
	 * @param argv
	 *            the command line arguments indicating the files to pack.
	 * @throws IOException
	 *             a source file could not be read.
	 */
	public static void main(final String[] argv) throws IOException {
		final JarLinkUtil util = new JarLinkUtil();
		final CmdLineParser clp = new CmdLineParser(util);
		try {
			clp.parseArgument(argv);
		} catch (CmdLineException e) {
			clp.printSingleLineUsage(System.err);
			System.exit(1);
		}
		util.run();
	}

	@Option(name = "-include", required = true)
	private List<File> includes = new ArrayList<File>();

	@Option(name = "-file", handler = MapOptionHandler.class)
	private Map<String, String> files = new HashMap<String, String>();

	private final Map<String, File> chosenSources = new HashMap<String, File>();

	private long creationTime;

	private ZipOutputStream zos;

	private JarLinkUtil() {
		// Command line utility only.
	}

	private void run() throws IOException {
		for (final File src : includes) {
			if (src.isFile())
				scanJar(src);
			else
				scanDirectory(src, src, "");
		}
		for (final Map.Entry<String, String> e : files.entrySet())
			chosenSources.put(e.getKey(), new File(e.getValue()));

		creationTime = System.currentTimeMillis();
		zos = new ZipOutputStream(System.out);
		zos.setLevel(9);

		for (final File src : includes) {
			if (src.isFile())
				appendJar(src);
			else
				appendDirectory(src, src, "");
		}
		for (final String name : files.keySet())
			appendFile(chosenSources.get(name), name);

		zos.close();
	}

	private void scanJar(final File jarPath) throws IOException {
		final ZipFile zf = new ZipFile(jarPath);
		final Enumeration<? extends ZipEntry> e = zf.entries();
		while (e.hasMoreElements())
			chosenSources.put(e.nextElement().getName(), jarPath);
		zf.close();
	}

	private void scanDirectory(final File rootPath, final File dirPath,
			final String pfx) throws IOException {
		final File[] entries = dirPath.listFiles();
		if (entries == null)
			return;
		for (final File e : entries) {
			if (e.getName().equals(".") || e.getName().equals(".."))
				continue;

			if (e.isDirectory())
				scanDirectory(rootPath, e, pfx + e.getName() + "/");
			else
				chosenSources.put(pfx + e.getName(), rootPath);
		}
	}

	private void appendJar(final File jarPath) throws IOException {
		final ZipFile zf = new ZipFile(jarPath);
		final Enumeration<? extends ZipEntry> e = zf.entries();
		while (e.hasMoreElements()) {
			final ZipEntry ze = e.nextElement();
			final String name = ze.getName();
			if (chosenSources.get(name) == jarPath)
				appendEntry(name, ze.getSize(), ze.getTime(), zf
						.getInputStream(ze));
		}
		zf.close();
	}

	private void appendDirectory(final File rootDir, final File dirPath,
			final String pfx) throws IOException {
		final File[] entries = dirPath.listFiles();
		if (entries == null)
			return;
		for (final File e : entries) {
			if (e.getName().equals(".") || e.getName().equals(".."))
				continue;

			if (e.isDirectory())
				appendDirectory(rootDir, e, pfx + e.getName() + "/");
			else if (chosenSources.get(pfx + e.getName()) == rootDir)
				appendFile(e, pfx + e.getName());
		}
	}

	private void appendFile(final File path, final String name)
			throws IOException {
		final long len = path.length();
		final InputStream is = new FileInputStream(path);
		appendEntry(name, len, creationTime, is);
	}

	private void appendEntry(final String name, final long len,
			final long time, final InputStream is) throws IOException {
		final ZipEntry ze = new ZipEntry(name);
		ze.setSize(len);
		ze.setTime(time);
		zos.putNextEntry(ze);
		try {
			final byte[] buf = new byte[4096];
			int n;
			while ((n = is.read(buf)) >= 0)
				zos.write(buf, 0, n);
		} finally {
			is.close();
		}
		zos.closeEntry();
	}
}
