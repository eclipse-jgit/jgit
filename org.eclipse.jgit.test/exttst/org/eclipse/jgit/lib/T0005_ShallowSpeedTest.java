/*
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

import java.io.File;
import java.io.IOException;

import junit.textui.TestRunner;

public class T0005_ShallowSpeedTest extends SpeedTestBase {

	protected void setUp() throws Exception {
		prepare(new String[] { "git", "rev-list", "365bbe0d0caaf2ba74d56556827babf0bc66965d" });
	}

	public void testShallowHistoryScan() throws IOException {
		long start = System.currentTimeMillis();
		Repository db = new Repository(new File(kernelrepo));
		Commit commit = db.mapCommit("365bbe0d0caaf2ba74d56556827babf0bc66965d");
		int n = 1;
		for (;;) {
			ObjectId[] parents = commit.getParentIds();
			if (parents.length == 0)
				break;
			ObjectId parentId = parents[0];
			commit = db.mapCommit(parentId);
			commit.getCommitId().name();
			++n;
		}
		assertEquals(12275, n);
		long stop = System.currentTimeMillis();
		long time = stop - start;
		System.out.println("native="+nativeTime);
		System.out.println("jgit="+time);
		// ~0.750s (hot cache), ok
		/*
native=1795
jgit=722
		 */
		// native git seems to run SLOWER than jgit here, at roughly half the speed
		// creating the git process is not the issue here, btw.
		long factor10 = (nativeTime*150/time+50)/100;
		assertEquals(3, factor10);
	}

	public static void main(String[] args) {
		TestRunner.run(T0005_ShallowSpeedTest.class);
	}
}
