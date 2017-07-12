/*
 * Copyright (C) 2017, Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command
class WriteReftable extends TextBuiltin {
	private static final int KIB = 1 << 10;
	private static final int MIB = 1 << 20;

	@Option(name = "--block-size")
	private int blockSize = 8 * KIB;

	@Option(name = "--restart-interval")
	private int restartInterval;

	@Argument(index = 0)
	private String in;

	@Argument(index = 1)
	private String out;

	@SuppressWarnings({ "nls", "boxing" })
	@Override
	protected void run() throws Exception {
		List<Ref> refs = read(in);

		ReftableWriter.Stats stats;
		try (OutputStream os = new BufferedOutputStream(
				new FileOutputStream(out))) {
			ReftableWriter w = new ReftableWriter();
			w.setBlockSize(blockSize);
			w.setRestartInterval(restartInterval);
			w.begin(os);
			for (Ref r : refs) {
				w.write(r);
			}
			stats = w.finish().getStats();
		}

		int indexKeys = stats.indexKeys();
		long totalBlocks = stats.blockCount();
		long totalPadding = stats.paddingBytes();
		int fileMiB = (int) Math.round(((double) stats.totalBytes()) / MIB);

		printf("Summary:");
		printf("  block sz: %d", stats.blockSize());
		printf("  restarts: %d", stats.restartInterval());
		printf("  refs    : %d", refs.size());
		printf("  file sz : %d MiB (%d bytes)", fileMiB, stats.totalBytes());
		printf("  blocks  : %d", totalBlocks);
		if (indexKeys > 0) {
			int idxSize = (int) Math.round(((double) stats.indexSize()) / KIB);
			printf("  idx keys: %d", indexKeys);
			printf("  idx sz  : %d KiB", idxSize);
			printf("  avg idx : %d", stats.indexSize() / stats.indexKeys());
		}
		printf("  lookup  : %.1f", stats.diskSeeksPerRead());

		printf("  padding : %d KiB", totalPadding / KIB);
		printf("  avg pad : %d bytes", totalPadding / totalBlocks);
		printf("  avg ref : %d bytes", stats.totalBytes() / refs.size());
		printf("  refs/blk: %d", refs.size() / totalBlocks);
		errw.println();
	}

	private void printf(String fmt, Object... args) throws IOException {
		errw.println(String.format(fmt, args));
	}

	static List<Ref> read(String inputFile) throws IOException {
		List<Ref> refs = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(inputFile), UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				ObjectId id = ObjectId.fromString(line.substring(0, 40));
				String name = line.substring(41, line.length());
				if (name.endsWith("^{}")) { //$NON-NLS-1$
					int lastIdx = refs.size() - 1;
					Ref last = refs.get(lastIdx);
					refs.set(lastIdx, new ObjectIdRef.PeeledTag(PACKED,
							last.getName(), last.getObjectId(), id));
					continue;
				}

				Ref ref;
				if (name.equals(HEAD)) {
					ref = new SymbolicRef(name, new ObjectIdRef.Unpeeled(NEW,
							R_HEADS + MASTER, null));
				} else {
					ref = new ObjectIdRef.PeeledNonTag(PACKED, name, id);
				}
				refs.add(ref);
			}
		}
		Collections.sort(refs, (a, b) -> a.getName().compareTo(b.getName()));
		return refs;
	}
}
