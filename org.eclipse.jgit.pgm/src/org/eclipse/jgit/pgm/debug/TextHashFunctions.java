/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.pgm.debug;

import static java.lang.Integer.valueOf;
import static java.lang.Long.valueOf;

import java.io.File;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.NB;
import org.kohsuke.args4j.Option;

/**
 * Scan repository to compute maximum number of collisions for hash functions.
 *
 * This is a test suite to help benchmark the collision rate of hash functions
 * when applied to file contents in a Git repository. The test scans all text
 * files in the HEAD revision of the repository it is run within. For each file
 * it finds the unique lines, and then inserts those lines into a hash table to
 * determine collision rates under the selected hash functions.
 *
 * To add another hash function to the test suite, declare a new instance member
 * field of type {@link Hash} and implement the hashRegion method. The test
 * suite will automatically pick up the new function through reflection.
 *
 * To add another folding function (method of squashing a 32 bit hash code into
 * the hash tables smaller array index space), declare a new instance field of
 * type {@link Fold} and implement the logic. The test suite will automatically
 * pick up the new function through reflection.
 */
@Command(usage = "usage_TextHashFunctions")
class TextHashFunctions extends TextBuiltin {

	/** Standard SHA-1 on the line, using the first 4 bytes as the hash code. */
	final Hash sha1 = new Hash() {
		private final MessageDigest md = Constants.newMessageDigest();

		@Override
		protected int hashRegion(byte[] raw, int ptr, int end) {
			md.reset();
			md.update(raw, ptr, end - ptr);
			return NB.decodeInt32(md.digest(), 0);
		}
	};

	/** Professor Daniel J. Bernstein's rather popular string hash. */
	final Hash djb = new Hash() {
		@Override
		protected int hashRegion(byte[] raw, int ptr, int end) {
			int hash = 5381;
			for (; ptr < end; ptr++)
				hash = ((hash << 5) + hash) + (raw[ptr] & 0xff);
			return hash;
		}
	};

	/** Hash function commonly used by java.lang.String. */
	final Hash string_hash31 = new Hash() {
		@Override
		protected int hashRegion(byte[] raw, int ptr, int end) {
			int hash = 0;
			for (; ptr < end; ptr++)
				hash = 31 * hash + (raw[ptr] & 0xff);
			return hash;
		}
	};

	/** The Rabin polynomial hash that is used by our own DeltaIndex. */
	final Hash rabin_DeltaIndex = new Hash() {
		private final byte[] buf16 = new byte[16];

		@Override
		protected int hashRegion(byte[] raw, int ptr, int end) {
			if (end - ptr < 16) {
				Arrays.fill(buf16, (byte) 0);
				System.arraycopy(raw, ptr, buf16, 0, end - ptr);
				return rabin(buf16, 0);
			}
			return rabin(raw, ptr);
		}

		private int rabin(byte[] raw, int ptr) {
			int hash;

			// The first 4 steps collapse out into a 4 byte big-endian decode,
			// with a larger right shift as we combined shift lefts together.
			//
			hash = ((raw[ptr] & 0xff) << 24) //
					| ((raw[ptr + 1] & 0xff) << 16) //
					| ((raw[ptr + 2] & 0xff) << 8) //
					| (raw[ptr + 3] & 0xff);
			hash ^= T[hash >>> 31];

			hash = ((hash << 8) | (raw[ptr + 4] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 5] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 6] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 7] & 0xff)) ^ T[hash >>> 23];

			hash = ((hash << 8) | (raw[ptr + 8] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 9] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 10] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 11] & 0xff)) ^ T[hash >>> 23];

			hash = ((hash << 8) | (raw[ptr + 12] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 13] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 14] & 0xff)) ^ T[hash >>> 23];
			hash = ((hash << 8) | (raw[ptr + 15] & 0xff)) ^ T[hash >>> 23];

			return hash;
		}

		private final int[] T = { 0x00000000, 0xd4c6b32d, 0x7d4bd577,
				0xa98d665a, 0x2e5119c3, 0xfa97aaee, 0x531accb4, 0x87dc7f99,
				0x5ca23386, 0x886480ab, 0x21e9e6f1, 0xf52f55dc, 0x72f32a45,
				0xa6359968, 0x0fb8ff32, 0xdb7e4c1f, 0x6d82d421, 0xb944670c,
				0x10c90156, 0xc40fb27b, 0x43d3cde2, 0x97157ecf, 0x3e981895,
				0xea5eabb8, 0x3120e7a7, 0xe5e6548a, 0x4c6b32d0, 0x98ad81fd,
				0x1f71fe64, 0xcbb74d49, 0x623a2b13, 0xb6fc983e, 0x0fc31b6f,
				0xdb05a842, 0x7288ce18, 0xa64e7d35, 0x219202ac, 0xf554b181,
				0x5cd9d7db, 0x881f64f6, 0x536128e9, 0x87a79bc4, 0x2e2afd9e,
				0xfaec4eb3, 0x7d30312a, 0xa9f68207, 0x007be45d, 0xd4bd5770,
				0x6241cf4e, 0xb6877c63, 0x1f0a1a39, 0xcbcca914, 0x4c10d68d,
				0x98d665a0, 0x315b03fa, 0xe59db0d7, 0x3ee3fcc8, 0xea254fe5,
				0x43a829bf, 0x976e9a92, 0x10b2e50b, 0xc4745626, 0x6df9307c,
				0xb93f8351, 0x1f8636de, 0xcb4085f3, 0x62cde3a9, 0xb60b5084,
				0x31d72f1d, 0xe5119c30, 0x4c9cfa6a, 0x985a4947, 0x43240558,
				0x97e2b675, 0x3e6fd02f, 0xeaa96302, 0x6d751c9b, 0xb9b3afb6,
				0x103ec9ec, 0xc4f87ac1, 0x7204e2ff, 0xa6c251d2, 0x0f4f3788,
				0xdb8984a5, 0x5c55fb3c, 0x88934811, 0x211e2e4b, 0xf5d89d66,
				0x2ea6d179, 0xfa606254, 0x53ed040e, 0x872bb723, 0x00f7c8ba,
				0xd4317b97, 0x7dbc1dcd, 0xa97aaee0, 0x10452db1, 0xc4839e9c,
				0x6d0ef8c6, 0xb9c84beb, 0x3e143472, 0xead2875f, 0x435fe105,
				0x97995228, 0x4ce71e37, 0x9821ad1a, 0x31accb40, 0xe56a786d,
				0x62b607f4, 0xb670b4d9, 0x1ffdd283, 0xcb3b61ae, 0x7dc7f990,
				0xa9014abd, 0x008c2ce7, 0xd44a9fca, 0x5396e053, 0x8750537e,
				0x2edd3524, 0xfa1b8609, 0x2165ca16, 0xf5a3793b, 0x5c2e1f61,
				0x88e8ac4c, 0x0f34d3d5, 0xdbf260f8, 0x727f06a2, 0xa6b9b58f,
				0x3f0c6dbc, 0xebcade91, 0x4247b8cb, 0x96810be6, 0x115d747f,
				0xc59bc752, 0x6c16a108, 0xb8d01225, 0x63ae5e3a, 0xb768ed17,
				0x1ee58b4d, 0xca233860, 0x4dff47f9, 0x9939f4d4, 0x30b4928e,
				0xe47221a3, 0x528eb99d, 0x86480ab0, 0x2fc56cea, 0xfb03dfc7,
				0x7cdfa05e, 0xa8191373, 0x01947529, 0xd552c604, 0x0e2c8a1b,
				0xdaea3936, 0x73675f6c, 0xa7a1ec41, 0x207d93d8, 0xf4bb20f5,
				0x5d3646af, 0x89f0f582, 0x30cf76d3, 0xe409c5fe, 0x4d84a3a4,
				0x99421089, 0x1e9e6f10, 0xca58dc3d, 0x63d5ba67, 0xb713094a,
				0x6c6d4555, 0xb8abf678, 0x11269022, 0xc5e0230f, 0x423c5c96,
				0x96faefbb, 0x3f7789e1, 0xebb13acc, 0x5d4da2f2, 0x898b11df,
				0x20067785, 0xf4c0c4a8, 0x731cbb31, 0xa7da081c, 0x0e576e46,
				0xda91dd6b, 0x01ef9174, 0xd5292259, 0x7ca44403, 0xa862f72e,
				0x2fbe88b7, 0xfb783b9a, 0x52f55dc0, 0x8633eeed, 0x208a5b62,
				0xf44ce84f, 0x5dc18e15, 0x89073d38, 0x0edb42a1, 0xda1df18c,
				0x739097d6, 0xa75624fb, 0x7c2868e4, 0xa8eedbc9, 0x0163bd93,
				0xd5a50ebe, 0x52797127, 0x86bfc20a, 0x2f32a450, 0xfbf4177d,
				0x4d088f43, 0x99ce3c6e, 0x30435a34, 0xe485e919, 0x63599680,
				0xb79f25ad, 0x1e1243f7, 0xcad4f0da, 0x11aabcc5, 0xc56c0fe8,
				0x6ce169b2, 0xb827da9f, 0x3ffba506, 0xeb3d162b, 0x42b07071,
				0x9676c35c, 0x2f49400d, 0xfb8ff320, 0x5202957a, 0x86c42657,
				0x011859ce, 0xd5deeae3, 0x7c538cb9, 0xa8953f94, 0x73eb738b,
				0xa72dc0a6, 0x0ea0a6fc, 0xda6615d1, 0x5dba6a48, 0x897cd965,
				0x20f1bf3f, 0xf4370c12, 0x42cb942c, 0x960d2701, 0x3f80415b,
				0xeb46f276, 0x6c9a8def, 0xb85c3ec2, 0x11d15898, 0xc517ebb5,
				0x1e69a7aa, 0xcaaf1487, 0x632272dd, 0xb7e4c1f0, 0x3038be69,
				0xe4fe0d44, 0x4d736b1e, 0x99b5d833 };
	};

	/** Bitwise-and to extract only the low bits. */
	final Fold truncate = new Fold() {
		@Override
		public int fold(int hash, int bits) {
			return hash & ((1 << bits) - 1);
		}
	};

	/** Applies the golden ratio and takes the upper bits. */
	final Fold golden_ratio = new Fold() {
		@Override
		public int fold(int hash, int bits) {
			/* 2^31 + 2^29 - 2^25 + 2^22 - 2^19 - 2^16 + 1 */
			return (hash * 0x9e370001) >>> (32 - bits);
		}
	};

	// -----------------------------------------------------------------------
	//
	// Implementation of the suite lives below this line.
	//
	//

	@Option(name = "--hash", metaVar = "NAME", usage = "Enable hash function(s)")
	List<String> hashFunctions = new ArrayList<>();

	@Option(name = "--fold", metaVar = "NAME", usage = "Enable fold function(s)")
	List<String> foldFunctions = new ArrayList<>();

	@Option(name = "--text-limit", metaVar = "LIMIT", usage = "Maximum size in KiB to scan")
	int textLimit = 15 * 1024; // 15 MiB as later we do * 1024.

	@Option(name = "--repository", aliases = { "-r" }, metaVar = "GIT_DIR", usage = "Repository to scan")
	List<File> gitDirs = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected boolean requiresRepository() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		if (gitDirs.isEmpty()) {
			RepositoryBuilder rb = new RepositoryBuilder() //
					.setGitDir(new File(gitdir)) //
					.readEnvironment() //
					.findGitDir();
			if (rb.getGitDir() == null)
				throw die(CLIText.get().cantFindGitDirectory);
			gitDirs.add(rb.getGitDir());
		}

		for (File dir : gitDirs) {
			RepositoryBuilder rb = new RepositoryBuilder();
			if (RepositoryCache.FileKey.isGitRepository(dir, FS.DETECTED))
				rb.setGitDir(dir);
			else
				rb.findGitDir(dir);

			try (Repository repo = rb.build()) {
				run(repo);
			}
		}
	}

	private void run(Repository repo) throws Exception {
		List<Function> all = init();

		long fileCnt = 0;
		long lineCnt = 0;
		try (ObjectReader or = repo.newObjectReader();
			RevWalk rw = new RevWalk(or);
			TreeWalk tw = new TreeWalk(or)) {
			final MutableObjectId id = new MutableObjectId();
			tw.reset(rw.parseTree(repo.resolve(Constants.HEAD)));
			tw.setRecursive(true);

			while (tw.next()) {
				FileMode fm = tw.getFileMode(0);
				if (!FileMode.REGULAR_FILE.equals(fm)
						&& !FileMode.EXECUTABLE_FILE.equals(fm))
					continue;

				byte[] raw;
				try {
					tw.getObjectId(id, 0);
					raw = or.open(id).getCachedBytes(textLimit * 1024);
				} catch (LargeObjectException tooBig) {
					continue;
				}

				if (RawText.isBinary(raw))
					continue;

				RawText txt = new RawText(raw);
				int[] lines = new int[txt.size()];
				int cnt = 0;
				HashSet<Line> u = new HashSet<>();
				for (int i = 0; i < txt.size(); i++) {
					if (u.add(new Line(txt, i)))
						lines[cnt++] = i;
				}

				fileCnt++;
				lineCnt += cnt;

				for (Function fun : all)
					testOne(fun, txt, lines, cnt);
			}
		}

		File directory = repo.getDirectory();
		if (directory != null) {
			String name = directory.getName();
			File parent = directory.getParentFile();
			if (name.equals(Constants.DOT_GIT) && parent != null)
				name = parent.getName();
			outw.println(name + ":"); //$NON-NLS-1$
		}
		outw.format("  %6d files; %5d avg. unique lines/file\n", //$NON-NLS-1$
				valueOf(fileCnt), //
				valueOf(lineCnt / fileCnt));
		outw.format("%-20s %-15s %9s\n", "Hash", "Fold", "Max Len"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		outw.println("-----------------------------------------------"); //$NON-NLS-1$
		String lastHashName = null;
		for (Function fun : all) {
			String hashName = fun.hash.name;
			if (hashName.equals(lastHashName))
				hashName = ""; //$NON-NLS-1$
			outw.format("%-20s %-15s %9d\n", // //$NON-NLS-1$
					hashName, //
					fun.fold.name, //
					valueOf(fun.maxChainLength));
			lastHashName = fun.hash.name;
		}
		outw.println();
		outw.flush();
	}

	private static void testOne(Function fun, RawText txt, int[] elements,
			int cnt) {
		final Hash cmp = fun.hash;
		final Fold fold = fun.fold;

		final int bits = tableBits(cnt);
		final int[] buckets = new int[1 << bits];
		for (int i = 0; i < cnt; i++)
			buckets[fold.fold(cmp.hash(txt, elements[i]), bits)]++;

		int maxChainLength = 0;
		for (int i = 0; i < buckets.length; i++)
			maxChainLength = Math.max(maxChainLength, buckets[i]);
		fun.maxChainLength = Math.max(fun.maxChainLength, maxChainLength);
	}

	private List<Function> init() {
		List<Hash> hashes = new ArrayList<>();
		List<Fold> folds = new ArrayList<>();

		try {
			for (Field f : TextHashFunctions.class.getDeclaredFields()) {
				if (f.getType() == Hash.class) {
					f.setAccessible(true);
					Hash cmp = (Hash) f.get(this);
					cmp.name = f.getName();
					hashes.add(cmp);

				} else if (f.getType() == Fold.class) {
					f.setAccessible(true);
					Fold fold = (Fold) f.get(this);
					fold.name = f.getName();
					folds.add(fold);
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException("Cannot determine names", e); //$NON-NLS-1$
		}

		List<Function> all = new ArrayList<>();
		for (Hash cmp : hashes) {
			if (include(cmp.name, hashFunctions)) {
				for (Fold f : folds) {
					if (include(f.name, foldFunctions)) {
						all.add(new Function(cmp, f));
					}
				}
			}
		}
		return all;
	}

	private static boolean include(String name, List<String> want) {
		if (want.isEmpty())
			return true;
		for (String s : want) {
			if (s.equalsIgnoreCase(name))
				return true;
		}
		return false;
	}

	private static class Function {
		final Hash hash;

		final Fold fold;

		int maxChainLength;

		Function(Hash cmp, Fold fold) {
			this.hash = cmp;
			this.fold = fold;
		}
	}

	/** Base class for any hashCode function to be tested. */
	private static abstract class Hash extends RawTextComparator {
		String name;

		@Override
		public boolean equals(RawText a, int ai, RawText b, int bi) {
			return RawTextComparator.DEFAULT.equals(a, ai, b, bi);
		}
	}

	/** Base class for any hashCode folding function to be tested. */
	private static abstract class Fold {
		String name;

		/**
		 * Fold the given 32-bit hash code into only {@code bits} of space.
		 *
		 * @param hash
		 *            the 32 bit hash code to be folded into a smaller value.
		 * @param bits
		 *            total number of bits that can appear in the output. The
		 *            output value must be in the range {@code [0, 1 << bits)}.
		 *            When bits = 2, valid outputs are 0, 1, 2, 3.
		 * @return the folded hash, squeezed into only {@code bits}.
		 */
		abstract int fold(int hash, int bits);
	}

	/** Utility to help us identify unique lines in a file. */
	private static class Line {
		private final RawText txt;

		private final int pos;

		Line(RawText txt, int pos) {
			this.txt = txt;
			this.pos = pos;
		}

		@Override
		public int hashCode() {
			return RawTextComparator.DEFAULT.hash(txt, pos);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Line) {
				Line e = (Line) obj;
				return RawTextComparator.DEFAULT.equals(txt, pos, e.txt, e.pos);
			}
			return false;
		}
	}

	private static int tableBits(int sz) {
		int bits = 31 - Integer.numberOfLeadingZeros(sz);
		if (bits == 0)
			bits = 1;
		if (1 << bits < sz)
			bits++;
		return bits;
	}
}
