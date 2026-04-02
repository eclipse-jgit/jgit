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
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_PACKNAMES;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_SIGNATURE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.io.SilentFileInputStream;

/**
 * Read only basic metadata from the midx without loading the whole file in
 * memory.
 * <p>
 * In some cases we need only a bit of information from the midx and we do not
 * need it yet fully loaded in memory. For example:
 * <ul>
 * <li>We need the checksum of the previous midx to delete its bitmaps</li>
 * <li>When opening the repo, we need the names of packs referenced by the midx
 * to check they exist</li>
 * </ul>
 */
public class MidxMetadataReader {

	/**
	 * Metadata useful before loading the full midx
	 *
	 * @param packNames
	 *            list of packs in this midx (in midx order).
	 * @param checksumB64
	 *            checksum of the midx, encoded in Base64
	 */
	public record MidxMetadata(List<String> packNames, String checksumB64) {
		/**
		 * Return the checksum as byte[]
		 *
		 * @return checksum in byte[] format
		 */
		public byte[] checksum() {
			return Base64.decode(checksumB64);
		}
	}

	/**
	 * Open an existing MultiPackIndex file and read its metadata
	 *
	 * @param midxFile
	 *            existing multi-pack-index to read.
	 * @return the metadata
	 * @throws FileNotFoundException
	 *             the file does not exist.
	 * @throws MultiPackIndexFormatException
	 *             MultiPackIndex file's format is different from we expected.
	 * @throws java.io.IOException
	 *             the file exists but could not be read due to security errors
	 *             or unexpected data corruption.
	 */
	public static MidxMetadata read(File midxFile) throws FileNotFoundException,
			MultiPackIndexFormatException, IOException {
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
	 * Read metadata from an existing MultiPackIndex from a buffered stream.
	 *
	 * @param fd
	 *            stream to read the multipack-index file from. The stream must
	 *            be buffered as some small IOs are performed against the
	 *            stream. The caller is responsible for closing the stream.
	 * @return the metadata
	 * @throws MultiPackIndexFormatException
	 *             the MultiPackIndex file's format is different from we
	 *             expected.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 */
	public static MidxMetadata read(InputStream fd)
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
			throw new MultiPackIndexFormatException(MessageFormat.format(
					JGitText.get().unsupportedMIDXVersion, Integer.valueOf(v)));
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

		ChunkIndex chunkIndex = ChunkIndex.read(fd, chunkCount);
		int currentPos = hdr.length + chunkIndex.getBytesRead();
		long packNamesStart = chunkIndex.getStartOffset(MIDX_CHUNKID_PACKNAMES);
		long packNamesEnd = chunkIndex.getEndOffset(MIDX_CHUNKID_PACKNAMES);

		IO.skipFully(fd, packNamesStart - currentPos);
		byte[] packNamesBuffer = new byte[(int) (packNamesEnd
				- packNamesStart)];
		IO.readFully(fd, packNamesBuffer);
		List<String> packNames = List
				.of(new String(packNamesBuffer, UTF_8).split("\u0000"));
		if (packCount != packNames.size()) {
			throw new MultiPackIndexFormatException(MessageFormat.format(
					JGitText.get().multiPackIndexPackCountMismatch, packCount,
					packNames.size()));
		}

		// We should be at packNamesEnd
		long endLastChunk = chunkIndex.getLastOffset();
		IO.skipFully(fd, endLastChunk - packNamesEnd);
		byte[] checksum = new byte[20];
		IO.readFully(fd, checksum, 0, 20);

		return new MidxMetadata(packNames, Base64.encodeBytes(checksum));
	}

	private static final class ChunkIndex {

		private final List<ChunkSegment> chunks;

		private final int byteSize;

		static ChunkIndex read(InputStream fd, int chunkCount)
				throws IOException {
			byte[] lookupBuffer = new byte[CHUNK_LOOKUP_WIDTH
					* (chunkCount + 1)];

			IO.readFully(fd, lookupBuffer, 0, lookupBuffer.length);

			List<ChunkSegment> chunks = new ArrayList<>(chunkCount + 1);
			for (int i = 0; i <= chunkCount; i++) {
				// chunks[chunkCount] is just a marker, in order to record the
				// length of the last chunk.
				int id = NB.decodeInt32(lookupBuffer, i * 12);
				long offset = NB.decodeInt64(lookupBuffer, i * 12 + 4);
				chunks.add(new ChunkSegment(id, offset));
			}
			return new ChunkIndex(chunks, lookupBuffer.length);
		}

		private ChunkIndex(List<ChunkSegment> chunks, int bytesRead) {
			this.chunks = chunks;
			this.byteSize = bytesRead;
		}

		/**
		 * Bytes read from the stream while reading the chunks
		 *
		 * @return number of bytes consumed from the stream
		 */
		int getBytesRead() {
			return byteSize;
		}

		long getStartOffset(int chunkId) {
			for (ChunkSegment c : chunks) {
				if (c.id() == chunkId) {
					return c.offset();
				}
			}
			throw new IllegalArgumentException("Asking for unknown chunk"); //$NON-NLS-1$
		}

		public long getEndOffset(int chunkId) {
			int pos = -1;
			for (int i = 0; i < chunks.size(); i++) {
				if (chunks.get(i).id() == chunkId) {
					pos = i;
					break;
				}
			}
			if (pos == -1) {
				throw new IllegalArgumentException("Asking for unknown chunk"); //$NON-NLS-1$
			}

			return chunks.get(pos + 1).offset();
		}

		public long getLastOffset() {
			return chunks.get(chunks.size() - 1).offset();
		}
	}

	private record ChunkSegment(int id, long offset) {
	}

	/**
	 * Thrown if a MultiPackIndex doesn't have the expected file format
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
