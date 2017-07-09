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

package org.eclipse.jgit.internal.storage.reftable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.reftable.RefBlockWriter.commonPrefix;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_MAGIC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.util.IntList;

/**
 * Constructs the index to output for {@link ReftableWriter}.
 * <p>
 * The index block records the last reference of the other blocks, supporting
 * near constant time lookup.
 */
class IndexBlockWriter {
	private final List<BlockHeader> blocks = new ArrayList<>();
	private final Set<BlockHeader> restarts = new HashSet<>();
	private final int restartInterval;

	private int bytesInIndex;

	IndexBlockWriter(int ri) {
		restartInterval = ri;
	}

	boolean shouldWriteIndex() {
		return blocks.size() > 4;
	}

	int keysInIndex() {
		return blocks.size();
	}

	int bytesInIndex() {
		return bytesInIndex;
	}

	void addBlock(RefBlockWriter refBlock) {
		BlockHeader b = new BlockHeader(refBlock.lastRefName());
		byte[] name = b.lastRef;
		boolean restart = nextShouldBeRestart();
		if (!restart) {
			byte[] prior = blocks.get(blocks.size() - 1).lastRef;
			int pfx = commonPrefix(prior, prior.length, name);
			if (pfx == 0) {
				restart = true;
			}
		}

		blocks.add(b);
		if (restart) {
			restarts.add(b);
		}
	}

	private boolean nextShouldBeRestart() {
		int cnt = blocks.size();
		return cnt == 0 || ((cnt + 1) % restartInterval) == 0;
	}

	void writeTo(ReftableOutputStream os) throws IOException {
		os.write(INDEX_MAGIC);

		IntList restartOffsets = new IntList(restarts.size());
		for (int blkIdx = 0; blkIdx < blocks.size(); blkIdx++) {
			BlockHeader b = blocks.get(blkIdx);
			byte[] name = b.lastRef;
			boolean restart = restarts.contains(b);
			if (restart) {
				restartOffsets.add(os.bytesWrittenInBlock());
				os.writeVarint(0);
				os.writeVarint(name.length << 2);
				os.write(name);
			} else {
				byte[] prior = blocks.get(blkIdx - 1).lastRef;
				int pfx = commonPrefix(prior, prior.length, name);
				int sfx = name.length - pfx;
				os.writeVarint(pfx);
				os.writeVarint(sfx << 2);
				os.write(name, pfx, sfx);
			}
			os.writeVarint(blkIdx);
		}
		int keysEnd = os.bytesWrittenInBlock();

		for (int i = 0; i < restartOffsets.size(); i++) {
			os.writeInt32(restartOffsets.get(i));
		}
		os.writeInt32(keysEnd);
		os.writeInt32(restartOffsets.size());
		bytesInIndex = os.bytesWrittenInBlock();
	}

	private static class BlockHeader {
		final byte[] lastRef;

		BlockHeader(String name) {
			lastRef = name.getBytes(UTF_8);
		}
	}
}
