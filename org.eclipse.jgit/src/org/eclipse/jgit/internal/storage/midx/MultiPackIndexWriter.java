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
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_BITMAPPEDPACKS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_LARGEOFFSETS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OBJECTOFFSETS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDFANOUT;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDLOOKUP;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_PACKNAMES;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_REVINDEX;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_SIGNATURE;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_VERSION;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MULTIPACK_INDEX_FANOUT_SIZE;
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
import org.eclipse.jgit.internal.storage.io.CancellableDigestOutputStream;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MutableEntry;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.NB;

/**
 * Writes a collection of indexes as a multipack index.
 * <p>
 * See <a href=
 * "https://git-scm.com/docs/pack-format#_multi_pack_index_midx_files_have_the_following_format">multipack
 * index format spec</a>
 *
 * @since 7.2
 */
public class MultiPackIndexWriter {

	private static final int LIMIT_31_BITS = (1 << 31) - 1;

	private static final int MIDX_HEADER_SIZE = 12;

	/**
	 * Data about the written multipack index
	 *
	 * @param bytesWritten
	 *            byte-size of the multipack index
	 * @param objectCount
	 *            count objects in this midx (i.e. unique objects in the covered
	 *            packs)
	 * @param packNames
	 *            packNames
	 */
	public record Result(long bytesWritten, int objectCount,
			List<String> packNames) {
	}

	/**
	 * Writes the inputs in the multipack index format in the outputStream.
	 *
	 * @param monitor
	 *            progress monitor
	 * @param outputStream
	 *            stream to write the multipack index file
	 * @param data
	 *            a pack index merger with the data sources (in order) for this
	 *            midx
	 * @return data about the write (e.g. bytes written)
	 * @throws IOException
	 *             Error writing to the stream
	 */
	public Result write(ProgressMonitor monitor, OutputStream outputStream,
			PackIndexMerger data) throws IOException {
		// List of chunks in the order they need to be written
		List<ChunkHeader> chunkHeaders = createChunkHeaders(data);
		long expectedSize = calculateExpectedSize(chunkHeaders);
		try (CancellableDigestOutputStream out = new CancellableDigestOutputStream(
				monitor, outputStream)) {
			writeHeader(out, chunkHeaders.size(), data.getPackCount());
			writeChunkLookup(out, chunkHeaders);

			WriteContext ctx = new WriteContext(out, data);
			for (ChunkHeader chunk : chunkHeaders) {
				chunk.writerFn.write(ctx);
			}
			writeCheckSum(out);
			if (expectedSize != out.length()) {
				throw new IllegalStateException(String.format(
						JGitText.get().multiPackIndexUnexpectedSize,
						Long.valueOf(expectedSize),
						Long.valueOf(out.length())));
			}
			return new Result(expectedSize, data.getUniqueObjectCount(),
					data.getPackNames());
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

	private List<ChunkHeader> createChunkHeaders(PackIndexMerger data) {
		List<ChunkHeader> chunkHeaders = new ArrayList<>();
		chunkHeaders.add(new ChunkHeader(MIDX_CHUNKID_OIDFANOUT,
				MULTIPACK_INDEX_FANOUT_SIZE, this::writeFanoutTable));
		chunkHeaders.add(new ChunkHeader(MIDX_CHUNKID_OIDLOOKUP,
				(long) data.getUniqueObjectCount() * OBJECT_ID_LENGTH,
				this::writeOidLookUp));
		chunkHeaders.add(new ChunkHeader(MIDX_CHUNKID_OBJECTOFFSETS,
				8L * data.getUniqueObjectCount(), this::writeObjectOffsets));
		if (data.needsLargeOffsetsChunk()) {
			chunkHeaders.add(new ChunkHeader(MIDX_CHUNKID_LARGEOFFSETS,
					8L * data.getOffsetsOver31BitsCount(),
					this::writeObjectLargeOffsets));
		}
		chunkHeaders.add(new ChunkHeader(MIDX_CHUNKID_REVINDEX,
				4L * data.getUniqueObjectCount(), this::writeRidx));
		chunkHeaders.add(new ChunkHeader(MIDX_CHUNKID_BITMAPPEDPACKS,
				8L * data.getPackCount(), this::writeBitmappedPackfiles));

		int packNamesSize = data.getPackNames().stream()
				.mapToInt(String::length).map(i -> i + 1 /* null at the end */)
				.sum();
		chunkHeaders.add(new ChunkHeader(MIDX_CHUNKID_PACKNAMES, packNamesSize,
				this::writePackfileNames));
		return chunkHeaders;
	}

	/**
	 * Write the first 12 bytes of the multipack index.
	 * <p>
	 * These bytes include things like magic number, version, number of
	 * chunks...
	 *
	 * @param out
	 *            output stream to write
	 * @param numChunks
	 *            number of chunks this multipack index is going to have
	 * @param packCount
	 *            number of packs covered by this multipack index
	 * @throws IOException
	 *             error writing to the output stream
	 */
	private void writeHeader(CancellableDigestOutputStream out, int numChunks,
			int packCount) throws IOException {
		byte[] headerBuffer = new byte[MIDX_HEADER_SIZE];
		NB.encodeInt32(headerBuffer, 0, MIDX_SIGNATURE);
		byte[] buff = { MIDX_VERSION, OID_HASH_VERSION, (byte) numChunks,
				(byte) 0 };
		System.arraycopy(buff, 0, headerBuffer, 4, 4);
		NB.encodeInt32(headerBuffer, 8, packCount);
		out.write(headerBuffer, 0, headerBuffer.length);
		out.flush();
	}

	/**
	 * Write a table of "chunkId, start-offset", with a special value "0,
	 * end-of-previous_chunk", to mark the end.
	 *
	 * @param out
	 *            output stream to write
	 * @param chunkHeaders
	 *            list of chunks in the order they are expected to be written
	 * @throws IOException
	 *             error writing to the output stream
	 */
	private void writeChunkLookup(CancellableDigestOutputStream out,
			List<ChunkHeader> chunkHeaders) throws IOException {

		// first chunk will start at header + this lookup block
		long chunkStart = MIDX_HEADER_SIZE
				+ (long) (chunkHeaders.size() + 1) * CHUNK_LOOKUP_WIDTH;
		byte[] chunkEntry = new byte[CHUNK_LOOKUP_WIDTH];
		for (ChunkHeader chunkHeader : chunkHeaders) {
			NB.encodeInt32(chunkEntry, 0, chunkHeader.chunkId);
			NB.encodeInt64(chunkEntry, 4, chunkStart);
			out.write(chunkEntry);
			chunkStart += chunkHeader.size;
		}
		// Terminating label for the block
		// (chunkid 0, offset where the next block would start)
		NB.encodeInt32(chunkEntry, 0, 0);
		NB.encodeInt64(chunkEntry, 4, chunkStart);
		out.write(chunkEntry);
	}

	/**
	 * Write the fanout table for the object ids
	 * <p>
	 * Table with 256 entries (one byte), where the ith entry, F[i], stores the
	 * number of OIDs with first byte at most i. Thus, F[255] stores the total
	 * number of objects.
	 *
	 * @param ctx
	 *            write context
	 * @throws IOException
	 *             error writing to the output stream
	 */

	private void writeFanoutTable(WriteContext ctx) throws IOException {
		byte[] tmp = new byte[4];
		int[] fanout = new int[256];
		Iterator<MutableEntry> iterator = ctx.data.bySha1Iterator();
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			fanout[e.getObjectId().getFirstByte() & 0xff]++;
		}
		for (int i = 1; i < fanout.length; i++) {
			fanout[i] += fanout[i - 1];
		}
		for (int n : fanout) {
			NB.encodeInt32(tmp, 0, n);
			ctx.out.write(tmp, 0, 4);
		}
	}

	/**
	 * Write the OID lookup chunk
	 * <p>
	 * A list of OIDs in sha1 order.
	 *
	 * @param ctx
	 *            write context
	 * @throws IOException
	 *             error writing to the output stream
	 */
	private void writeOidLookUp(WriteContext ctx) throws IOException {
		byte[] tmp = new byte[OBJECT_ID_LENGTH];

		Iterator<MutableEntry> iterator = ctx.data.bySha1Iterator();
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			e.getObjectId().copyRawTo(tmp, 0);
			ctx.out.write(tmp, 0, OBJECT_ID_LENGTH);
		}
	}

	/**
	 * Write the object offsets chunk
	 * <p>
	 * A list of offsets, parallel to the list of OIDs. If the offset is too
	 * large (see {@link #fitsIn31bits(long)}), this contains the position in
	 * the large offsets list (marked with a 1 in the most significant bit).
	 *
	 * @param ctx
	 *            write context
	 * @throws IOException
	 *             error writing to the output stream
	 */
	private void writeObjectOffsets(WriteContext ctx) throws IOException {
		byte[] entry = new byte[8];
		Iterator<MutableEntry> iterator = ctx.data.bySha1Iterator();
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			NB.encodeInt32(entry, 0, e.getPackId());
			if (!ctx.data.needsLargeOffsetsChunk()
					|| fitsIn31bits(e.getOffset())) {
				NB.encodeInt32(entry, 4, (int) e.getOffset());
			} else {
				int offloadedPosition = ctx.largeOffsets.append(e.getOffset());
				NB.encodeInt32(entry, 4, offloadedPosition | (1 << 31));
			}
			ctx.out.write(entry);
		}
	}

	/**
	 * Writes the reverse index chunk
	 * <p>
	 * This stores the position of the objects in the main index, ordered first
	 * by pack and then by offset
	 *
	 * @param ctx
	 *            write context
	 * @throws IOException
	 *             erorr writing to the output stream
	 */
	private void writeRidx(WriteContext ctx) throws IOException {
		Map<Integer, List<OffsetPosition>> packOffsets = new HashMap<>(
				ctx.data.getPackCount());
		// TODO(ifrade): Brute force solution loading all offsets/packs in
		// memory. We could also iterate reverse indexes looking up
		// their position in the midx (and discarding if the pack doesn't
		// match).
		Iterator<MutableEntry> iterator = ctx.data.bySha1Iterator();
		int midxPosition = 0;
		while (iterator.hasNext()) {
			MutableEntry e = iterator.next();
			OffsetPosition op = new OffsetPosition(e.getOffset(), midxPosition);
			midxPosition++;
			packOffsets.computeIfAbsent(e.getPackId(), k -> new ArrayList<>())
					.add(op);
		}

		for (int i = 0; i < ctx.data.getPackCount(); i++) {
			List<OffsetPosition> offsetsForPack = packOffsets.get(i);
			if (offsetsForPack == null) {
				continue;
			}
			offsetsForPack.sort(Comparator.comparing(OffsetPosition::offset));
			byte[] ridxForPack = new byte[4 * offsetsForPack.size()];
			for (int j = 0; j < offsetsForPack.size(); j++) {
				NB.encodeInt32(ridxForPack, j * 4,
						offsetsForPack.get(j).position);
			}
			ctx.out.write(ridxForPack);
		}
	}

	private void writeBitmappedPackfiles(WriteContext ctx) throws IOException {
		int[] objsPerPack = ctx.data.getObjectsPerPack();

		byte[] buffer = new byte[8 * objsPerPack.length];
		int bufferPos = 0;
		int accruedBitmapPositions = 0;
		for (int pack = 0; pack < objsPerPack.length; pack++) {
			NB.encodeInt32(buffer, bufferPos, accruedBitmapPositions);
			NB.encodeInt32(buffer, bufferPos + 4, objsPerPack[pack]);
			accruedBitmapPositions += objsPerPack[pack];
			bufferPos += 8;
		}
		ctx.out.write(buffer);
	}

	/**
	 * Write the large offset chunk
	 * <p>
	 * A list of large offsets (long). The regular offset chunk will point to a
	 * position here.
	 *
	 * @param ctx
	 *            writer context
	 * @throws IOException
	 *             error writing to the output stream
	 */
	private void writeObjectLargeOffsets(WriteContext ctx) throws IOException {
		ctx.out.write(ctx.largeOffsets.offsets, 0,
				ctx.largeOffsets.bytePosition);
	}

	/**
	 * Write the list of packfiles chunk
	 * <p>
	 * List of packfiles (in lexicographical order) with an \0 at the end
	 *
	 * @param ctx
	 *            writer context
	 * @throws IOException
	 *             error writing to the output stream
	 */
	private void writePackfileNames(WriteContext ctx) throws IOException {
		for (String packName : ctx.data.getPackNames()) {
			// Spec doesn't talk about encoding.
			ctx.out.write(packName.getBytes(StandardCharsets.UTF_8));
			ctx.out.write(0);
		}
	}

	/**
	 * Write final checksum of the data written to the stream
	 *
	 * @param out
	 *            output stream used to write
	 * @throws IOException
	 *             error writing to the output stream
	 */
	private void writeCheckSum(CancellableDigestOutputStream out)
			throws IOException {
		out.write(out.getDigest());
		out.flush();
	}

	private record OffsetPosition(long offset, int position) {
	}

	/**
	 * If there is at least one offset value larger than 2^32-1, then the large
	 * offset chunk must exist, and offsets larger than 2^31-1 must be stored in
	 * it instead
	 *
	 * @param offset
	 *            object offset
	 *
	 * @return true if the offset fits in 31 bits
	 */
	private static boolean fitsIn31bits(long offset) {
		return offset <= LIMIT_31_BITS;
	}

	private static class LargeOffsets {
		private final byte[] offsets;

		private int bytePosition;

		LargeOffsets(int largeOffsetsCount) {
			offsets = new byte[largeOffsetsCount * 8];
			bytePosition = 0;
		}

		/**
		 * Add an offset to the large offset chunk
		 *
		 * @param largeOffset
		 *            a large offset
		 * @return the position of the just inserted offset (as in number of
		 *         offsets, NOT in bytes)
		 */
		int append(long largeOffset) {
			int at = bytePosition;
			NB.encodeInt64(offsets, at, largeOffset);
			bytePosition += 8;
			return at / 8;
		}
	}

	private record ChunkHeader(int chunkId, long size, ChunkWriter writerFn) {
	}

	@FunctionalInterface
	private interface ChunkWriter {
		void write(WriteContext ctx) throws IOException;
	}

	private static class WriteContext {
		final CancellableDigestOutputStream out;

		final PackIndexMerger data;

		final LargeOffsets largeOffsets;

		WriteContext(CancellableDigestOutputStream out, PackIndexMerger data) {
			this.out = out;
			this.data = data;
			this.largeOffsets = new LargeOffsets(
					data.getOffsetsOver31BitsCount());
		}
	}
}
