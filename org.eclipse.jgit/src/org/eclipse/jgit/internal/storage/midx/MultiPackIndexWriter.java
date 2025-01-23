/*
 * Copyright (C) 2025, Google Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OBJECT_LARGE_OFFSET;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OBJECT_OFFSET;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_PACKFILE_NAMES;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_RIDX;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_LOOKUP_WIDTH;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MULTIPACK_INDEX_FANOUT_SIZE;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MULTIPACK_INDEX_MAGIC;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MULTIPACK_INDEX_VERSION_GENERATED;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.OID_HASH_VERSION;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.io.CancellableDigestOutputStream;
import org.eclipse.jgit.internal.storage.midx.PackIndexMerger.MutableEntry;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.NB;

/**
 * Writes a collection of indexes as a multipack index
 */
public class MultiPackIndexWriter {

	private static final int LIMIT_31_BITS = (1 << 31) - 1;

	private final int hashsz = OBJECT_ID_LENGTH;

	/**
	 * Writes the inputs in the multipack index format in the outputStream.
	 *
	 * @param monitor
	 *            progress monitor
	 * @param outputStream
	 *            stream to write the multipack index file
	 * @param inputs
	 *            pairs of name and index for each pack to include in the
	 *            multipack index.
	 * @throws IOException
	 *             Error writing to the stream
	 */
	public void write(ProgressMonitor monitor, OutputStream outputStream,
			Map<String, PackIndex> inputs) throws IOException {
		PackIndexMerger merger = new PackIndexMerger(inputs);
		List<ChunkHeader> chunkHeaders = calculateChunkHeaders(merger);
		long expectedSize = calculateExpectedSize(chunkHeaders);
		try (CancellableDigestOutputStream out = new CancellableDigestOutputStream(
				monitor, outputStream)) {
			writeHeader(out, chunkHeaders.size(), merger.getPackCount());
			writeChunkLookup(out, chunkHeaders);
			writeChunks(out, chunkHeaders, merger);
			writeCheckSum(out);
			if (expectedSize != out.length()) {
				throw new IllegalStateException(
						String.format(JGitText.get().multiPackIndexUnexpectedSize,
								expectedSize, out.length()));
			}
		} catch (InterruptedIOException e) {
			throw new IOException(JGitText.get().multiPackIndexWritingCancelled,
					e);
		}
	}

	private static long calculateExpectedSize(List<ChunkHeader> chunks) {
		int chunkLookup = (chunks.size() + 1) * CHUNK_LOOKUP_WIDTH;
		long chunkContent = chunks.stream().mapToLong(c -> c.size).sum();
		return /* header */ 12 + chunkLookup + chunkContent + /* CRC */ 20;
	}

	private List<ChunkHeader> calculateChunkHeaders(PackIndexMerger merger) {
		List<ChunkHeader> chunkHeaders = new ArrayList<>();
		chunkHeaders.add(new ChunkHeader(CHUNK_ID_OID_FANOUT,
				MULTIPACK_INDEX_FANOUT_SIZE));
		chunkHeaders.add(new ChunkHeader(CHUNK_ID_OID_LOOKUP,
				(long) merger.getUniqueObjectCount() * hashsz));
		chunkHeaders.add(new ChunkHeader(CHUNK_ID_OBJECT_OFFSET,
				8L * merger.getUniqueObjectCount()));
		if (merger.needsLargeOffsetsChunk()) {
			chunkHeaders.add(new ChunkHeader(CHUNK_ID_OBJECT_LARGE_OFFSET,
					8L * merger.getOffsetsOver31BitsCount()));
		}
		chunkHeaders.add(new ChunkHeader(CHUNK_ID_RIDX,
				4L * merger.getUniqueObjectCount()));

		int packNamesSize = merger.getPackNames().stream()
				.mapToInt(String::length).map(i -> i + 1 /* null at the end */)
				.sum();
		chunkHeaders
				.add(new ChunkHeader(CHUNK_ID_PACKFILE_NAMES, packNamesSize));
		return chunkHeaders;
	}

	private void writeHeader(CancellableDigestOutputStream out, int numChunks,
			int packCount) throws IOException {
		byte[] headerBuffer = new byte[12];
		NB.encodeInt32(headerBuffer, 0, MULTIPACK_INDEX_MAGIC);
		byte[] buff = { MULTIPACK_INDEX_VERSION_GENERATED, OID_HASH_VERSION,
				(byte) numChunks, (byte) 0 };
		System.arraycopy(buff, 0, headerBuffer, 4, 4);
		NB.encodeInt32(headerBuffer, 8, packCount);
		out.write(headerBuffer, 0, headerBuffer.length);
		out.flush();
	}

	private void writeChunkLookup(CancellableDigestOutputStream out,
			List<ChunkHeader> chunkHeaders) throws IOException {

		// first chunk will start at header + this lookup block
		long endPreviousChunk = 12
				+ (long) (chunkHeaders.size() + 1) * CHUNK_LOOKUP_WIDTH;
		byte[] chunkEntry = new byte[12];
		for (ChunkHeader chunkHeader : chunkHeaders) {
			NB.encodeInt32(chunkEntry, 0, chunkHeader.chunkId);
			NB.encodeInt64(chunkEntry, 4, endPreviousChunk);
			out.write(chunkEntry);
			endPreviousChunk += chunkHeader.size;
		}
		NB.encodeInt32(chunkEntry, 0, 0);
		NB.encodeInt64(chunkEntry, 4, endPreviousChunk);
		out.write(chunkEntry);
	}

	private void writeChunks(CancellableDigestOutputStream out,
			List<ChunkHeader> chunkHeaders, PackIndexMerger merger)
			throws IOException {
		LargeOffsets largeOffsets = null;
		for (ChunkHeader chunk : chunkHeaders) {
			int chunkId = chunk.chunkId;

			switch (chunkId) {
			case CHUNK_ID_OID_FANOUT -> writeFanoutTable(out, merger);
			case CHUNK_ID_OID_LOOKUP -> writeOidLookUp(out, merger);
			case CHUNK_ID_OBJECT_OFFSET ->
				largeOffsets = writeObjectOffsets(out, merger);
			case CHUNK_ID_OBJECT_LARGE_OFFSET -> {
				if (largeOffsets == null || largeOffsets.empty()) {
					throw new IllegalStateException(
							"Trying to write large offsets but they haven't been calculated");
				}
				writeObjectLargeOffsets(out, largeOffsets);
			}
			case CHUNK_ID_RIDX -> writeRidx(out, merger);
			case CHUNK_ID_PACKFILE_NAMES -> writePackfileNames(out, merger);
			default -> throw new IllegalStateException(
					"Don't know how to write chunk " + chunkId); //$NON-NLS-1$
			}
		}
	}

	private void writeFanoutTable(CancellableDigestOutputStream out,
			PackIndexMerger merger) throws IOException {
		byte[] tmp = new byte[4];
		int[] fanout = new int[256];
		Iterator<MutableEntry> iterator = merger.bySha1Iterator();
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			fanout[e.oid.getFirstByte() & 0xff]++;
		}
		for (int i = 1; i < fanout.length; i++) {
			fanout[i] += fanout[i - 1];
		}
		for (int n : fanout) {
			NB.encodeInt32(tmp, 0, n);
			out.write(tmp, 0, 4);
		}
	}

	private void writeOidLookUp(CancellableDigestOutputStream out,
			PackIndexMerger merger) throws IOException {
		byte[] tmp = new byte[hashsz];

		Iterator<MutableEntry> iterator = merger.bySha1Iterator();
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			e.oid.copyRawTo(tmp, 0);
			out.write(tmp, 0, hashsz);
		}
	}

	private LargeOffsets writeObjectOffsets(CancellableDigestOutputStream out,
			PackIndexMerger merger) throws IOException {
		byte[] entry = new byte[8];
		LargeOffsets largeOffsets = new LargeOffsets(
				merger.getOffsetsOver31BitsCount());

		Iterator<MutableEntry> iterator = merger.bySha1Iterator();
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			NB.encodeInt32(entry, 0, e.packId);
			if (!merger.needsLargeOffsetsChunk() || fitsIn31bits(e.offset)) {
				NB.encodeInt32(entry, 4, (int) e.offset);
			} else {
				int offloadedPosition = largeOffsets.append(e.offset);
				NB.encodeInt32(entry, 4, offloadedPosition & (1 << 31));
			}
			out.write(entry);
		}
		return largeOffsets;
	}

	private void writeRidx(CancellableDigestOutputStream out,
			PackIndexMerger merger) throws IOException {
		Map<Integer, List<OffsetPosition>> packOffsets = new HashMap<>(
				merger.getPackCount());
		Iterator<MutableEntry> iterator = merger.bySha1Iterator();
		int midxPosition = 0;
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			OffsetPosition op = new OffsetPosition(e.offset, midxPosition);
			midxPosition++;
			packOffsets.computeIfAbsent(e.packId, k -> new ArrayList<>())
					.add(op);
		}

		for (int i = 0; i < merger.getPackCount(); i++) {
			List<OffsetPosition> offsetsForPack = packOffsets.get(i);
			if (offsetsForPack.isEmpty()) {
				continue;
			}
			offsetsForPack.sort(Comparator.comparing(OffsetPosition::offset));
			byte[] ridxForPack = new byte[4 * offsetsForPack.size()];
			for (int j = 0; j < offsetsForPack.size(); j++) {
				NB.encodeInt32(ridxForPack, j * 4,
						offsetsForPack.get(j).position);
			}
			out.write(ridxForPack);
		}
	}

	private record OffsetPosition(long offset, int position) {
	}

	private static boolean fitsIn31bits(long offset) {
		/*
		 * If there is at least one offset value larger than 2^32-1, then the
		 * large offset chunk must exist, and offsets larger than 2^31-1 must be
		 * stored in it instead
		 */
		return offset <= LIMIT_31_BITS;
	}

	private void writeObjectLargeOffsets(CancellableDigestOutputStream out,
			LargeOffsets largeOffsets) throws IOException {
		out.write(largeOffsets.offsets, 0, largeOffsets.pos);
	}

	private void writePackfileNames(CancellableDigestOutputStream out,
			PackIndexMerger merger) throws IOException {
		for (String packName : merger.getPackNames()) {
			out.write(packName.getBytes(StandardCharsets.US_ASCII)); // ??
			out.write(0);
		}
	}

	private void writeCheckSum(CancellableDigestOutputStream out)
			throws IOException {
		out.write(out.getDigest());
		out.flush();
	}

	private static class LargeOffsets {
		private final byte[] offsets;

		private int pos;

		LargeOffsets(int largeOffsetsCount) {
			offsets = new byte[largeOffsetsCount * 8];
			pos = 0;
		}

		int append(long largeOffset) {
			int at = pos;
			NB.encodeInt64(offsets, at, largeOffset);
			pos += 8;
			return at;
		}

		boolean empty() {
			return pos == 0;
		}
	}

	private record ChunkHeader(int chunkId, long size) {
	}
}
