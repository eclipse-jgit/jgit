/*
 * Copyright (C) 2013, Google Inc.
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class Bundler {
	public static void main(String[] argv) throws IOException {
		new Bundler().run(argv);
	}

	@Option(name = "-o", required = true)
	private File out;

	@Option(name = "-s")
	private Map<String, String> srcs = new LinkedHashMap<String, String>();

	@Option(name = "-n")
	private String bundleName;

	@Argument(index = 0)
	private List<File> jars = new ArrayList<File>();

	private long now = System.currentTimeMillis();
	private byte[] buf = new byte[4096];
	private HashMap<String, File> have = new HashMap<String, File>();

	private void run(String[] argv) throws IOException {
		CmdLineParser clp = new CmdLineParser(this);
		try {
			clp.parseArgument(argv);
		} catch (CmdLineException err) {
			System.err.println("error: " + err.getMessage());
			System.exit(1);
		}

		ZipOutputStream os = new ZipOutputStream(
			new BufferedOutputStream(
				new FileOutputStream(out)));

		for (Map.Entry<String, String> pair : srcs.entrySet()) {
			String name = pair.getKey();
			File src = new File(pair.getValue());
			putFileEntry(os, name, new FileInputStream(src), src.length());
			have.put(name, src);
		}
		for (File src : jars)
			appendZip(os, src);
		os.close();
	}

	private void appendZip(ZipOutputStream os, File src) throws ZipException,
			IOException {
		@SuppressWarnings("resource")
		ZipFile srcZip = new ZipFile(src);
		Enumeration<? extends ZipEntry> e = srcZip.entries();
		while (e.hasMoreElements()) {
			ZipEntry srcEntry = e.nextElement();
			String name = srcEntry.getName();
			if (name.equals("META-INF/MANIFEST.MF"))
				continue;
			if (have.containsKey(name))
				throw duplicateEntry(src, srcEntry);

			putFileEntry(os, name,
					srcZip.getInputStream(srcEntry),
					srcEntry.getSize());
			have.put(name, src);
		}
		srcZip.close();
	}

	private void putFileEntry(ZipOutputStream os, String name, InputStream in,
			long size) throws IOException {
		ZipEntry dstEntry = new ZipEntry(name);
		dstEntry.setSize(size);
		dstEntry.setTime(now);
		os.putNextEntry(dstEntry);

		for (int n; (n = in.read(buf)) > 0;)
			os.write(buf, 0, n);
		in.close();
		os.closeEntry();
	}

	private IOException duplicateEntry(File src, ZipEntry srcEntry) {
		return new IOException(String.format("duplicate resource %s\n"
				+ " in %s\n" + "and %s\n", srcEntry.getName(),
				have.get(srcEntry.getName()), src));
	}
}
