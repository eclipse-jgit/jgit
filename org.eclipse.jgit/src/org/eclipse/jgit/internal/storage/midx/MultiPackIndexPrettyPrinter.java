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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_LOOKUP_WIDTH;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Prints a multipack index file in a human-readable format.
 *
 * @since 7.2
 */
@SuppressWarnings({ "boxing", "nls" })
public class MultiPackIndexPrettyPrinter {

	/**
	 * Writes to out, in human-readable format, the multipack index in rawMidx
	 *
	 * @param rawMidx the bytes of a multipack index
	 * @param out a writer
	 */
	public static void prettyPrint(byte[] rawMidx, PrintWriter out) {
		// Header (12 bytes)
		out.println("[ 0] Magic: " + new String(rawMidx, 0, 4, UTF_8));
		out.println("[ 4] Version number: " + (int) rawMidx[4]);
		out.println("[ 5] OID version: " + (int) rawMidx[5]);
		int chunkCount = rawMidx[6];
		out.println("[ 6] # of chunks: " + chunkCount);
		out.println("[ 7] # of bases: " + (int) rawMidx[7]);
		int numberOfPacks = NB.decodeInt32(rawMidx, 8);
		out.println("[ 8] # of packs: " + numberOfPacks);

		// Chunk lookup table
		List<ChunkSegment> chunkSegments = new ArrayList<>();
		int current = printChunkLookup(out, rawMidx, chunkCount, chunkSegments);

		for (int i = 0; i < chunkSegments.size() - 1; i++) {
			ChunkSegment segment = chunkSegments.get(i);
			if (current != segment.startOffset()) {
				throw new IllegalStateException(String.format(
						"We are at byte %d, but segment should start at %d",
						current, segment.startOffset()));
			}
			out.printf("Starting chunk: %s @ %d%n", segment.chunkName(),
					segment.startOffset());
			switch (segment.chunkName()) {
				case "OIDF" -> current = printOIDF(out, rawMidx, current);
				case "OIDL" -> current = printOIDL(out, rawMidx, current,
						chunkSegments.get(i + 1).startOffset);
				case "OOFF" -> current = printOOFF(out, rawMidx, current,
						chunkSegments.get(i + 1).startOffset);
				case "PNAM" -> current = printPNAM(out, rawMidx, current,
						chunkSegments.get(i + 1).startOffset);
				case "RIDX" -> current = printRIDX(out, rawMidx, current,
						chunkSegments.get(i + 1).startOffset);
				default -> {
					out.printf(
							"Skipping %s (don't know how to print it yet)%n",
							segment.chunkName());
					current = (int) chunkSegments.get(i + 1).startOffset();
				}
			}
		}
		// Checksum is a SHA-1, use ObjectId to parse it
		out.printf("[ %d] Checksum %s%n", current,
				ObjectId.fromRaw(rawMidx, current).name());
		out.printf("Total size: " + (current + 20));
	}

	private static int printChunkLookup(PrintWriter out, byte[] rawMidx, int chunkCount,
			List<ChunkSegment> chunkSegments) {
		out.println("Starting chunk lookup @ 12");
		int current = 12;
		for (int i = 0; i < chunkCount; i++) {
			String chunkName = new String(rawMidx, current, 4, UTF_8);
			long offset = NB.decodeInt64(rawMidx, current + 4);
			out.printf("[ %d] |%8s|%8d|%n", current, chunkName, offset);
			current += CHUNK_LOOKUP_WIDTH;
			chunkSegments.add(new ChunkSegment(chunkName, offset));
		}
		String chunkName = "0000";
		long offset = NB.decodeInt64(rawMidx, current + 4);
		out.printf("[ %d] |%8s|%8d|%n", current, chunkName, offset);
		current += CHUNK_LOOKUP_WIDTH;
		chunkSegments.add(new ChunkSegment(chunkName, offset));
		return current;
	}

	private static int printOIDF(PrintWriter out, byte[] rawMidx, int start) {
		int current = start;
		for (short i = 0; i < 256; i++) {
			out.printf("[ %d] (%02X) %d%n", current, i,
					NB.decodeInt32(rawMidx, current));
			current += 4;
		}
		return current;
	}

	private static int printOIDL(PrintWriter out, byte[] rawMidx, int start, long end) {
		int i = start;
		while (i < end) {
			out.printf("[ %d] %s%n", i,
					ObjectId.fromRaw(rawMidx, i).name());
			i += 20;
		}
		return i;
	}

	private static int printOOFF(PrintWriter out, byte[] rawMidx, int start, long end) {
		int i = start;
		while (i < end) {
			out.printf("[ %d] %d %d%n", i, NB.decodeInt32(rawMidx, i),
					NB.decodeInt32(rawMidx, i + 4));
			i += 8;
		}
		return i;
	}

	private static int printRIDX(PrintWriter out, byte[] rawMidx, int start, long end) {
		int i = start;
		while (i < end) {
			out.printf("[ %d] %d%n", i, NB.decodeInt32(rawMidx, i));
			i += 4;
		}
		return (int) end;
	}

	private static int printPNAM(PrintWriter out, byte[] rawMidx, int start, long end) {
		int nameStart = start;
		for (int i = start; i < end; i++) {
			if (rawMidx[i] == 0) {
				out
						.println(new String(rawMidx, nameStart, i - nameStart));
				nameStart = i + 1;
			}
		}
		return (int) end;
	}

	private record ChunkSegment(String chunkName, long startOffset) {
	}
}
