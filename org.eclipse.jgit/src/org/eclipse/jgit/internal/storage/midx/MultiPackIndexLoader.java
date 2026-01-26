/*
 * Copyright (C) 2024, GerritForge Inc. and others
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
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_BITMAPPEDPACKS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_LARGEOFFSETS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OBJECTOFFSETS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDFANOUT;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDLOOKUP;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_PACKNAMES;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_REVINDEX;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_SIGNATURE;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.io.SilentFileInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The loader returns the representation of the MultiPackIndex file content.
 */
public class MultiPackIndexLoader {
	private final static Logger LOG = LoggerFactory
			.getLogger(MultiPackIndexLoader.class);

	/**
	 * Open an existing MultiPackIndex file for reading.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param midxFile
	 *            existing multi-pack-index to read.
	 * @return a copy of the multi-pack-index file in memory
	 * @throws FileNotFoundException
	 *             the file does not exist.
	 * @throws MultiPackIndexFormatException
	 *             MultiPackIndex file's format is different from we expected.
	 * @throws java.io.IOException
	 *             the file exists but could not be read due to security errors
	 *             or unexpected data corruption.
	 */
	public static MultiPackIndex open(File midxFile)
			throws FileNotFoundException, MultiPackIndexFormatException,
			IOException {
		try (SilentFileInputStream fd = new SilentFileInputStream(midxFile)) {
			try {
				return read(fd);
			} catch (MultiPackIndexFormatException fe) {
				throw fe;
			} catch (IOException ioe) {
				throw new IOException(
						MessageFormat.format(JGitText.get().unreadableMIDX,
								midxFile.getAbsolutePath()),
						ioe);
			}
		}
	}

	/**
	 * Read an existing MultiPackIndex file from a buffered stream.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param fd
	 *            stream to read the multipack-index file from. The stream must be
	 *            buffered as some small IOs are performed against the stream.
	 *            The caller is responsible for closing the stream.
	 * @return a copy of the MultiPackIndex file in memory
	 * @throws MultiPackIndexFormatException
	 *             the MultiPackIndex file's format is different from we
	 *             expected.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 */
	public static MultiPackIndex read(InputStream fd)
			throws MultiPackIndexFormatException, IOException {
		byte[] hdr = new byte[12];
		IO.readFully(fd, hdr, 0, hdr.length);

		int magic = NB.decodeInt32(hdr, 0);

		if (magic != MIDX_SIGNATURE) {
			throw new MultiPackIndexFormatException(JGitText.get().notAMIDX);
		}

		// Check MultiPackIndex version
		int v = hdr[4];
		if (v != 1) {
			throw new MultiPackIndexFormatException(MessageFormat
					.format(JGitText.get().unsupportedMIDXVersion,
							Integer.valueOf(v)));
		}

		// Read the object Id version (1 byte)
		// 1 => SHA-1
		// 2 => SHA-256
		// TODO: If the hash type does not match the repository's hash
		// algorithm,
		// the multi-pack-index file should be ignored with a warning
		// presented to the user.
		int commitIdVersion = hdr[5];
		if (commitIdVersion != 1) {
			throw new MultiPackIndexFormatException(
					JGitText.get().incorrectOBJECT_ID_LENGTH);
		}

		// Read the number of "chunkOffsets" (1 byte)
		int chunkCount = hdr[6];

		// Read the number of multi-pack-index files (1 byte)
		// This value is currently always zero.
		// TODO populate this
		// int numberOfMultiPackIndexFiles = hdr[7];

		// Number of packfiles (4 bytes)
		int packCount = NB.decodeInt32(hdr, 8);

		byte[] lookupBuffer = new byte[CHUNK_LOOKUP_WIDTH * (chunkCount + 1)];

		IO.readFully(fd, lookupBuffer, 0, lookupBuffer.length);

		List<ChunkSegment> chunks = new ArrayList<>(chunkCount + 1);
		for (int i = 0; i <= chunkCount; i++) {
			// chunks[chunkCount] is just a marker, in order to record the
			// length of the last chunk.
			int id = NB.decodeInt32(lookupBuffer, i * 12);
			long offset = NB.decodeInt64(lookupBuffer, i * 12 + 4);
			chunks.add(new ChunkSegment(id, offset));
		}

		MultiPackIndexBuilder builder = MultiPackIndexBuilder.builder();
		builder.setPackCount(packCount);
		for (int i = 0; i < chunkCount; i++) {
			long chunkOffset = chunks.get(i).offset;
			int chunkId = chunks.get(i).id;
			long len = chunks.get(i + 1).offset - chunkOffset;

			if (len > Integer.MAX_VALUE - 8) { // http://stackoverflow.com/a/8381338
				throw new MultiPackIndexFormatException(
						JGitText.get().multiPackIndexFileIsTooLargeForJgit);
			}

			byte[] buffer = new byte[(int) len];
			IO.readFully(fd, buffer, 0, buffer.length);

			switch (chunkId) {
			case MIDX_CHUNKID_OIDFANOUT:
				builder.addOidFanout(buffer);
				break;
			case MIDX_CHUNKID_OIDLOOKUP:
				builder.addOidLookUp(buffer);
				break;
			case MIDX_CHUNKID_PACKNAMES:
				builder.addPackNames(buffer);
				break;
			case MIDX_CHUNKID_BITMAPPEDPACKS:
				builder.addBitmappedPacks(buffer);
				break;
			case MIDX_CHUNKID_OBJECTOFFSETS:
				builder.addObjectOffsets(buffer);
				break;
			case MIDX_CHUNKID_LARGEOFFSETS:
				builder.addObjectLargeOffsets(buffer);
				break;
			case MIDX_CHUNKID_REVINDEX:
				builder.addReverseIndex(buffer);
				break;
			default:
				LOG.warn(MessageFormat.format(JGitText.get().midxChunkUnknown,
						Integer.toHexString(chunkId)));
			}
		}
		byte[] checksum = new byte[20];
		IO.readFully(fd, checksum, 0, 20);
		builder.addChecksum(checksum);
		return builder.build();
	}

	private record ChunkSegment(int id, long offset) {}

	/**
	 * Accumulate byte[] of the different chunks, to build a multipack index
	 */
	// Visible for testing
	static class MultiPackIndexBuilder {

		private final int hashLength;

		private int packCount;

		private byte[] oidFanout;

		private byte[] oidLookup;

		private String[] packNames;

		private byte[] bitmappedPackfiles;

		private byte[] objectOffsets;

		// Optional
		private byte[] largeObjectOffsets;

		// Optional
		private byte[] bitmapPackOrder;

		private byte[] checksum;

		private MultiPackIndexBuilder(int hashLength) {
			this.hashLength = hashLength;
		}

		/**
		 * Create builder
		 *
		 * @return A builder of {@link MultiPackIndex}.
		 */
		static MultiPackIndexBuilder builder() {
			return new MultiPackIndexBuilder(OBJECT_ID_LENGTH);
		}

		MultiPackIndexBuilder setPackCount(int packCount) {
			this.packCount = packCount;
			return this;
		}

		MultiPackIndexBuilder addOidFanout(byte[] buffer)
				throws MultiPackIndexFormatException {
			assertChunkNotSeenYet(oidFanout, MIDX_CHUNKID_OIDFANOUT);
			oidFanout = buffer;
			return this;
		}

		MultiPackIndexBuilder addOidLookUp(byte[] buffer)
				throws MultiPackIndexFormatException {
			assertChunkNotSeenYet(oidLookup, MIDX_CHUNKID_OIDLOOKUP);
			oidLookup = buffer;
			return this;
		}

		MultiPackIndexBuilder addPackNames(byte[] buffer)
				throws MultiPackIndexFormatException {
			assertChunkNotSeenYet(packNames, MIDX_CHUNKID_PACKNAMES);
			packNames = new String(buffer, UTF_8).split("\u0000"); //$NON-NLS-1$
			return this;
		}

		MultiPackIndexBuilder addBitmappedPacks(byte[] buffer)
				throws MultiPackIndexFormatException {
			assertChunkNotSeenYet(bitmappedPackfiles,
					MIDX_CHUNKID_BITMAPPEDPACKS);
			bitmappedPackfiles = buffer;
			return this;
		}

		MultiPackIndexBuilder addObjectOffsets(byte[] buffer)
				throws MultiPackIndexFormatException {
			assertChunkNotSeenYet(objectOffsets, MIDX_CHUNKID_OBJECTOFFSETS);
			objectOffsets = buffer;
			return this;
		}

		MultiPackIndexBuilder addObjectLargeOffsets(byte[] buffer)
				throws MultiPackIndexFormatException {
			assertChunkNotSeenYet(largeObjectOffsets,
					MIDX_CHUNKID_LARGEOFFSETS);
			largeObjectOffsets = buffer;
			return this;
		}

		MultiPackIndexBuilder addReverseIndex(byte[] buffer)
				throws MultiPackIndexFormatException {
			assertChunkNotSeenYet(bitmapPackOrder, MIDX_CHUNKID_REVINDEX);
			bitmapPackOrder = buffer;
			return this;
		}

		MultiPackIndex build() throws MultiPackIndexFormatException {
			assertChunkNotNull(oidFanout, MIDX_CHUNKID_OIDFANOUT);
			assertChunkNotNull(oidLookup, MIDX_CHUNKID_OIDLOOKUP);
			assertChunkNotNull(packNames, MIDX_CHUNKID_PACKNAMES);
			assertChunkNotNull(objectOffsets, MIDX_CHUNKID_OBJECTOFFSETS);

			assertPackCounts(packCount, packNames.length);
			return new MultiPackIndexV1(hashLength, oidFanout, oidLookup,
					packNames, bitmappedPackfiles, objectOffsets,
					largeObjectOffsets, bitmapPackOrder, checksum);
		}

		private static void assertChunkNotNull(Object object, int chunkId)
				throws MultiPackIndexFormatException {
			if (object == null) {
				throw new MultiPackIndexFormatException(
						MessageFormat.format(JGitText.get().midxChunkNeeded,
								Integer.toHexString(chunkId)));
			}
		}

		private static void assertChunkNotSeenYet(Object object, int chunkId)
				throws MultiPackIndexFormatException {
			if (object != null) {
				throw new MultiPackIndexFormatException(
						MessageFormat.format(JGitText.get().midxChunkRepeated,
								Integer.toHexString(chunkId)));
			}
		}

		private static void assertPackCounts(int headerCount,
				int packfileNamesCount) throws MultiPackIndexFormatException {
			if (headerCount != packfileNamesCount) {
				throw new MultiPackIndexFormatException(MessageFormat.format(
						JGitText.get().multiPackIndexPackCountMismatch,
						Integer.valueOf(headerCount),
						Integer.valueOf(packfileNamesCount)));
			}
		}

		public void addChecksum(byte[] checksum) {
			this.checksum = checksum;
		}
	}

	/**
	 * Thrown when a MultiPackIndex file's format is different from we expected
	 */
	public static class MultiPackIndexFormatException extends IOException {

		private static final long serialVersionUID = 1L;

		/**
		 * Construct an exception.
		 *
		 * @param why
		 *            description of the type of error.
		 */
		MultiPackIndexFormatException(String why) {
			super(why);
		}
	}
}
