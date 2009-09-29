/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Base class for performance unit test.
 */
public abstract class SpeedTestBase extends TestCase {

	/**
	 * The time used by native git as this is our reference.
	 */
	protected long nativeTime;

	/**
	 * Reference to the location of the Linux kernel repo.
	 */
	protected String kernelrepo;

	/**
	 * Prepare test by running a test against the Linux kernel repo first.
	 *
	 * @param refcmd
	 *            git command to execute
	 *
	 * @throws Exception
	 */
	protected void prepare(String[] refcmd) throws Exception {
		try {
			BufferedReader bufferedReader = new BufferedReader(new FileReader("kernel.ref"));
			try {
				kernelrepo = bufferedReader.readLine();
			} finally {
				bufferedReader.close();
			}
			timeNativeGit(kernelrepo, refcmd);
			nativeTime = timeNativeGit(kernelrepo, refcmd);
		} catch (Exception e) {
			System.out.println("Create a file named kernel.ref and put the path to the Linux kernels repository there");
			throw e;
		}
	}

	private static long timeNativeGit(String kernelrepo, String[] refcmd) throws IOException,
			InterruptedException, Exception {
		long start = System.currentTimeMillis();
		Process p = Runtime.getRuntime().exec(refcmd, null, new File(kernelrepo,".."));
		InputStream inputStream = p.getInputStream();
		InputStream errorStream = p.getErrorStream();
		byte[] buf=new byte[1024*1024];
		for (;;)
			if (inputStream.read(buf) < 0)
				break;
		if (p.waitFor()!=0) {
			int c;
			while ((c=errorStream.read())!=-1)
				System.err.print((char)c);
			throw new Exception("git log failed");
		}
		inputStream.close();
		errorStream.close();
		long stop = System.currentTimeMillis();
		return stop - start;
	}
}
