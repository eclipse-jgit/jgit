/*
 * Copyright (C) 2025, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_LOOKUP_WIDTH;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OBJECTOFFSETS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDFANOUT;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDLOOKUP;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_PACKNAMES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.internal.storage.file.PackFile;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.util.NB;
import org.junit.Test;

public class CgitMidxCompatibilityTest extends SampleDataRepositoryTestCase {

	@Test
	public void jgitMidx_verifyByCgit()
			throws IOException, InterruptedException {
		byte[] jgitMidxBytes = generateJGitMidx();
		writeMidx(jgitMidxBytes);
		assertEquals("cgit exit code", 0, run_cgit_multipackindex_verify());
	}

	@Test
	public void compareBasicChunkSizes()
			throws IOException, InterruptedException {
		// We cannot compare byte-by-byte because there are optional chunks and
		// it is not guaranteed what cgit and jgit will generate
		byte[] jgitMidxBytes = generateJGitMidx();
		assertEquals("cgit exit code", 0, run_cgit_multipackindex_write());
		byte[] cgitMidxBytes = readCgitMidx();

		RawMultiPackIndex jgitMidx = new RawMultiPackIndex(jgitMidxBytes);
		RawMultiPackIndex cgitMidx = new RawMultiPackIndex(cgitMidxBytes);

		// This is a fixed sized chunk
		assertEquals(256 * 4, cgitMidx.getChunkSize(MIDX_CHUNKID_OIDFANOUT));
		assertArrayEquals(cgitMidx.getRawChunk(MIDX_CHUNKID_OIDFANOUT),
				jgitMidx.getRawChunk(MIDX_CHUNKID_OIDFANOUT));

		assertArrayEquals(cgitMidx.getRawChunk(MIDX_CHUNKID_OIDLOOKUP),
				jgitMidx.getRawChunk(MIDX_CHUNKID_OIDLOOKUP));

		// The spec has changed from padding packnames to a multile of four, to
		// move the packname chunk to the end of the file.
		// git 2.48 pads the packs names to a multiple of 4
		// jgit puts the chunk at the end
		byte[] cgitPacknames = trimPadding(
				cgitMidx.getRawChunk(MIDX_CHUNKID_PACKNAMES));
		assertArrayEquals(cgitPacknames,
				jgitMidx.getRawChunk(MIDX_CHUNKID_PACKNAMES));

		assertArrayEquals(cgitMidx.getRawChunk(MIDX_CHUNKID_OBJECTOFFSETS),
				jgitMidx.getRawChunk(MIDX_CHUNKID_OBJECTOFFSETS));

	}

	@Test
	public void jgit_loadsCgitMidx()
			throws IOException, InterruptedException {
		assertEquals("cgit exit code", 0, run_cgit_multipackindex_write());
		byte[] cgitMidxBytes = readCgitMidx();
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(cgitMidxBytes));
		assertEquals(7, midx.getPackNames().length);
	}

	private byte[] generateJGitMidx() throws IOException {
		PackIndexMerger.Builder builder = PackIndexMerger.builder();
		for (Pack pack : db.getObjectDatabase().getPacks()) {
			PackFile packFile = pack.getPackFile().create(PackExt.INDEX);
			builder.addPack(packFile.getName(), pack.getIndex());
		}

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, builder.build());
		return out.toByteArray();
	}

	private int run_cgit_multipackindex_write()
			throws IOException, InterruptedException {
		String[] command = new String[] { "git", "multi-pack-index", "write" };
		Process proc = Runtime.getRuntime().exec(command, new String[0],
				db.getDirectory());
		return proc.waitFor();
	}

	private int run_cgit_multipackindex_verify()
			throws IOException, InterruptedException {
		String[] command = new String[] { "git", "multi-pack-index", "verify" };
		Process proc = Runtime.getRuntime().exec(command, new String[0],
				db.getDirectory());
		return proc.waitFor();
	}

	private byte[] readCgitMidx() throws IOException {
		File midx = getMIdxStandardLocation();
		assertTrue("cgit multi-pack-index exists", midx.exists());
		return Files.readAllBytes(midx.toPath());
	}

	private void writeMidx(byte[] midx) throws IOException {
		File midxFile = getMIdxStandardLocation();
		Files.write(midxFile.toPath(), midx);
	}

	private File getMIdxStandardLocation() {
		return new File(db.getObjectDatabase().getPackDirectory(),
				"multi-pack-index");
	}

	private byte[] trimPadding(byte[] data) {
		// Chunk MUST have one \0, we want to remove any extra \0
		int newEnd = data.length - 1;
		while (newEnd - 1 >= 0 && data[newEnd - 1] == 0) {
			newEnd--;
		}

		if (newEnd == data.length - 1) {
			return data;
		}
		return Arrays.copyOfRange(data, 0, newEnd + 1);
	}

	private static class RawMultiPackIndex {
		private final List<ChunkSegment> chunks;

		private final byte[] midx;

		private RawMultiPackIndex(byte[] midx) {
			this.chunks = readChunks(midx);
			this.midx = midx;
		}

		long getChunkSize(int chunkId) {
			int chunkPos = findChunkPosition(chunks, chunkId);
			return chunks.get(chunkPos + 1).offset
					- chunks.get(chunkPos).offset;
		}

		long getOffset(int chunkId) {
			return chunks.get(findChunkPosition(chunks, chunkId)).offset;
		}

		private long getNextOffset(int chunkId) {
			return chunks.get(findChunkPosition(chunks, chunkId) + 1).offset;
		}

		byte[] getRawChunk(int chunkId) {
			int start = (int) getOffset(chunkId);
			int end = (int) getNextOffset(chunkId);
			return Arrays.copyOfRange(midx, start, end);
		}

		private static int findChunkPosition(List<ChunkSegment> chunks,
				int id) {
			int chunkPos = -1;
			for (int i = 0; i < chunks.size(); i++) {
				if (chunks.get(i).id() == id) {
					chunkPos = i;
					break;
				}
			}
			if (chunkPos == -1) {
				throw new IllegalStateException("Chunk doesn't exist");
			}
			return chunkPos;
		}

		private List<ChunkSegment> readChunks(byte[] midx) {
			// Read the number of "chunkOffsets" (1 byte)
			int chunkCount = midx[6];
			byte[] lookupBuffer = new byte[CHUNK_LOOKUP_WIDTH
					* (chunkCount + 1)];
			System.arraycopy(midx, 12, lookupBuffer, 0, lookupBuffer.length);

			List<ChunkSegment> chunks = new ArrayList<>(chunkCount + 1);
			for (int i = 0; i <= chunkCount; i++) {
				// chunks[chunkCount] is just a marker, in order to record the
				// length of the last chunk.
				int id = NB.decodeInt32(lookupBuffer, i * 12);
				long offset = NB.decodeInt64(lookupBuffer, i * 12 + 4);
				chunks.add(new ChunkSegment(id, offset));
			}
			return chunks;
		}
	}

	private record ChunkSegment(int id, long offset) {
	}
}
