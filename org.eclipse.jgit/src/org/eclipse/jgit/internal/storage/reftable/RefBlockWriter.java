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
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.RAW_IDLEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableOutputStream.computeVarintSize;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.IntList;

/** Constructs a block of output for {@link ReftableWriter}. */
class RefBlockWriter {
	private final List<Ref> refs = new ArrayList<>();
	private final Set<Ref> restarts = new HashSet<>();
	private final int blockSize;
	private final int restartInterval;

	private int bytesInKeyTable;
	private int bytesInRestartTable;

	RefBlockWriter(int bs, int ri, Ref ref) throws BlockSizeTooSmallException {
		blockSize = bs;
		restartInterval = ri;
		bytesInRestartTable = 4 + 4;

		if (!tryAdd(ref, true)) {
			// Insanely long reference names need a larger block size.
			throw blockSizeTooSmall(ref);
		}
	}

	String lastRefName() {
		return refs.get(refs.size() - 1).getName();
	}

	boolean tryAdd(Ref ref) {
		if (tryAdd(ref, true)) {
			return true;
		} else if (nextShouldBeRestart()) {
			// It was time for another restart, but the ref doesn't fit
			// with its complete name, as the block is nearly full. Try to
			// force it to fit with prefix compression rather than waste
			// the tail of the block with padding.
			return tryAdd(ref, false);
		}
		return false;
	}

	private boolean tryAdd(Ref ref, boolean tryRestart) {
		byte[] name = nameUtf8(ref);
		int prefixLen = 0;
		int suffixLen = name.length;
		boolean restart = tryRestart && nextShouldBeRestart();
		if (!restart) {
			byte[] prior = nameUtf8(refs.get(refs.size() - 1));
			prefixLen = commonPrefix(prior, prior.length, name);
			if (prefixLen > 0) {
				suffixLen = name.length - prefixLen;
			} else {
				restart = true;
			}
		}

		int entrySize = computeEntrySize(prefixLen, suffixLen, ref);
		if (computeBlockSize(entrySize, restart) > blockSize) {
			return false;
		}

		bytesInKeyTable += entrySize;
		refs.add(ref);
		if (restart) {
			bytesInRestartTable += 4;
			restarts.add(ref);
		}
		return true;
	}

	private boolean nextShouldBeRestart() {
		int cnt = refs.size();
		return cnt == 0 || ((cnt + 1) % restartInterval) == 0;
	}

	private int computeBlockSize(int key, boolean restart) {
		return bytesInKeyTable + key + bytesInRestartTable + (restart ? 4 : 0);
	}

	void writeTo(ReftableOutputStream os, boolean padBlock)
			throws IOException {
		IntList restartOffsets = new IntList(restarts.size());
		for (int refIdx = 0; refIdx < refs.size(); refIdx++) {
			Ref ref = refs.get(refIdx);
			byte[] name = nameUtf8(ref);
			boolean restart = restarts.contains(ref);
			if (restart) {
				restartOffsets.add(os.bytesWrittenInBlock());
				os.writeVarint(0);
				os.writeVarint(encodeLenAndType(name.length, ref));
				os.write(name);
			} else {
				byte[] prior = nameUtf8(refs.get(refIdx - 1));
				int pfx = commonPrefix(prior, prior.length, name);
				int sfx = name.length - pfx;
				os.writeVarint(pfx);
				os.writeVarint(encodeLenAndType(sfx, ref));
				os.write(name, pfx, sfx);
			}
			if (ref.isSymbolic()) {
				writeSymref(os, ref.getTarget());
			} else {
				writeId(os, ref);
			}
		}
		int keysEnd = os.bytesWrittenInBlock();

		int padLen = os.bytesAvailableInBlock() - bytesInRestartTable;
		if (padBlock && padLen > 0) {
			os.pad(padLen);
		}

		for (int i = 0; i < restartOffsets.size(); i++) {
			os.writeInt32(restartOffsets.get(i));
		}
		os.writeInt32(keysEnd);
		os.writeInt32(restartOffsets.size());
		os.finishBlock(padBlock);
	}

	private void writeSymref(ReftableOutputStream os, Ref target)
			throws IOException {
		byte[] name = nameUtf8(target);
		os.writeVarint(name.length);
		os.write(name);
	}

	private void writeId(ReftableOutputStream os, Ref ref) throws IOException {
		ObjectId id1 = ref.getObjectId();
		if (id1 == null) {
			if (ref.getStorage() == NEW) {
				return;
			} else {
				throw new IOException(JGitText.get().invalidId0);
			}
		} else if (!ref.isPeeled()) {
			throw new IOException(JGitText.get().peeledRefIsRequired);
		}
		os.write(id1);

		ObjectId id2 = ref.getPeeledObjectId();
		if (id2 != null) {
			os.write(id2);
		}
	}

	private BlockSizeTooSmallException blockSizeTooSmall(Ref ref) {
		// Compute size required to fit this reference by itself.
		byte[] name = nameUtf8(ref);
		int entry = computeEntrySize(0, name.length, ref);
		int min = computeBlockSize(entry, true);
		return new BlockSizeTooSmallException(min);
	}

	private static int computeEntrySize(int prefixLen, int suffixLen, Ref ref) {
		return computeVarintSize(prefixLen)
				+ computeVarintSize(encodeLenAndType(suffixLen, ref))
				+ suffixLen
				+ computeValueSize(ref);
	}

	private static int encodeLenAndType(int nameLen, Ref ref) {
		return (nameLen << 2) | type(ref);
	}

	private static int type(Ref ref) {
		if (ref.isSymbolic()) {
			return 0x03;
		} else if (ref.getStorage() == NEW && ref.getObjectId() == null) {
			return 0x00;
		} else if (ref.getPeeledObjectId() != null) {
			return 0x02;
		} else {
			return 0x01;
		}
	}

	private static int computeValueSize(Ref ref) {
		if (ref.isSymbolic()) {
			return nameUtf8(ref.getTarget()).length + 1;
		} else if (ref.getStorage() == NEW && ref.getObjectId() == null) {
			return 0;
		}
		return ref.getPeeledObjectId() != null ? 2 * RAW_IDLEN : RAW_IDLEN;
	}

	static int commonPrefix(byte[] a, int n, byte[] b) {
		int len = Math.min(n, Math.min(a.length, b.length));
		for (int i = 0; i < len; i++) {
			if (a[i] != b[i]) {
				return i;
			}
		}
		return len;
	}

	private static byte[] nameUtf8(Ref ref) {
		return ref.getName().getBytes(UTF_8);
	}
}
