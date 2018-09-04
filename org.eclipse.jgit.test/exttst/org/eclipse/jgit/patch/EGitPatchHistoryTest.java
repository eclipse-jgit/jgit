/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.patch;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Test;

public class EGitPatchHistoryTest {
	@Test
	public void testParseHistory() throws Exception {
		final NumStatReader numstat = new NumStatReader();
		numstat.read();

		final HashMap<String, HashMap<String, StatInfo>> stats = numstat.stats;
		assertEquals(1211, stats.size());

		new PatchReader(stats).read();
	}

	static class StatInfo {
		int added, deleted;
	}

	static class PatchReader extends CommitReader {
		final HashSet<String> offBy1;

		final HashMap<String, HashMap<String, StatInfo>> stats;

		int errors;

		PatchReader(HashMap<String, HashMap<String, StatInfo>> s)
				throws IOException {
			super(new String[] { "-p" });
			stats = s;

			offBy1 = new HashSet<>();
			offBy1.add("9bda5ece6806cd797416eaa47c7b927cc6e9c3b2");
		}

		@Override
		void onCommit(String cid, byte[] buf) {
			final HashMap<String, StatInfo> files = stats.remove(cid);
			assertNotNull("No files for " + cid, files);

			final Patch p = new Patch();
			p.parse(buf, 0, buf.length - 1);
			assertEquals("File count " + cid, files.size(), p.getFiles().size());
			if (!p.getErrors().isEmpty()) {
				for (FormatError e : p.getErrors()) {
					System.out.println("error " + e.getMessage());
					System.out.println("  at " + e.getLineText());
				}
				dump(buf);
				fail("Unexpected error in " + cid);
			}

			for (FileHeader fh : p.getFiles()) {
				final String fileName;
				if (fh.getChangeType() != FileHeader.ChangeType.DELETE)
					fileName = fh.getNewPath();
				else
					fileName = fh.getOldPath();
				final StatInfo s = files.remove(fileName);
				final String nid = fileName + " in " + cid;
				assertNotNull("No " + nid, s);
				int added = 0, deleted = 0;
				for (HunkHeader h : fh.getHunks()) {
					added += h.getOldImage().getLinesAdded();
					deleted += h.getOldImage().getLinesDeleted();
				}

				if (s.added == added) {
					//
				} else if (s.added == added + 1 && offBy1.contains(cid)) {
					//
				} else {
					dump(buf);
					assertEquals("Added diff in " + nid, s.added, added);
				}

				if (s.deleted == deleted) {
					//
				} else if (s.deleted == deleted + 1 && offBy1.contains(cid)) {
					//
				} else {
					dump(buf);
					assertEquals("Deleted diff in " + nid, s.deleted, deleted);
				}
			}
			assertTrue("Missed files in " + cid, files.isEmpty());
		}

		private static void dump(byte[] buf) {
			String str;
			try {
				str = new String(buf, 0, buf.length - 1, "ISO-8859-1");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			System.out.println("<<" + str + ">>");
		}
	}

	static class NumStatReader extends CommitReader {
		final HashMap<String, HashMap<String, StatInfo>> stats = new HashMap<>();

		NumStatReader() throws IOException {
			super(new String[] { "--numstat" });
		}

		@Override
		void onCommit(String commitId, byte[] buf) {
			final HashMap<String, StatInfo> files = new HashMap<>();
			final MutableInteger ptr = new MutableInteger();
			while (ptr.value < buf.length) {
				if (buf[ptr.value] == '\n')
					break;
				final StatInfo i = new StatInfo();
				i.added = RawParseUtils.parseBase10(buf, ptr.value, ptr);
				i.deleted = RawParseUtils.parseBase10(buf, ptr.value + 1, ptr);
				final int eol = RawParseUtils.nextLF(buf, ptr.value);
				final String name = RawParseUtils.decode(UTF_8,
						buf, ptr.value + 1, eol - 1);
				files.put(name, i);
				ptr.value = eol;
			}
			stats.put(commitId, files);
		}
	}

	static abstract class CommitReader {
		private Process proc;

		CommitReader(String[] args) throws IOException {
			final String[] realArgs = new String[3 + args.length + 1];
			realArgs[0] = "git";
			realArgs[1] = "log";
			realArgs[2] = "--pretty=format:commit %H";
			System.arraycopy(args, 0, realArgs, 3, args.length);
			realArgs[3 + args.length] = "a4b98ed15ea5f165a7aa0f2fd2ea6fcce6710925";

			proc = Runtime.getRuntime().exec(realArgs);
			proc.getOutputStream().close();
			proc.getErrorStream().close();
		}

		void read() throws IOException, InterruptedException {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(proc.getInputStream(), ISO_8859_1))) {
				String commitId = null;
				TemporaryBuffer buf = null;
				for (;;) {
					String line = in.readLine();
					if (line == null)
						break;
					if (line.startsWith("commit ")) {
						if (buf != null) {
							buf.close();
							onCommit(commitId, buf.toByteArray());
							buf.destroy();
						}
						commitId = line.substring("commit ".length());
						buf = new TemporaryBuffer.LocalFile(null);
					} else if (buf != null) {
						buf.write(line.getBytes(ISO_8859_1));
						buf.write('\n');
					}
				}
			}
			assertEquals(0, proc.waitFor());
			proc = null;
		}

		abstract void onCommit(String commitId, byte[] buf);
	}
}
