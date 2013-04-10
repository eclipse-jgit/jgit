/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.CRC32;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.NB;

/** Custom output stream to support {@link PackWriter}. */
public final class PackOutputStream extends OutputStream {
	private static final int BYTES_TO_WRITE_BEFORE_CANCEL_CHECK = 128 * 1024;

	private final ProgressMonitor writeMonitor;

	private final OutputStream out;

	private final PackWriter packWriter;

	private CRC32 crc = new CRC32();

	private final MessageDigest md = Constants.newMessageDigest();

	private long count;

	private final byte[] headerBuffer = new byte[32];

	private final byte[] copyBuffer = new byte[16 << 10];

	private long checkCancelAt;

	/**
	 * Initialize a pack output stream.
	 * <p>
	 * This constructor is exposed to support debugging the JGit library only.
	 * Application or storage level code should not create a PackOutputStream,
	 * instead use {@link PackWriter}, and let the writer create the stream.
	 *
	 * @param writeMonitor
	 *            monitor to update on object output progress.
	 * @param out
	 *            target stream to receive all object contents.
	 * @param pw
	 *            packer that is going to perform the output.
	 */
	public PackOutputStream(final ProgressMonitor writeMonitor,
			final OutputStream out, final PackWriter pw) {
		this.writeMonitor = writeMonitor;
		this.out = out;
		this.packWriter = pw;
		this.checkCancelAt = BYTES_TO_WRITE_BEFORE_CANCEL_CHECK;
	}

	@Override
	public void write(final int b) throws IOException {
		count++;
		out.write(b);
		if (crc != null)
			crc.update(b);
		md.update((byte) b);
	}

	@Override
	public void write(final byte[] b, int off, int len)
			throws IOException {
		while (0 < len) {
			final int n = Math.min(len, BYTES_TO_WRITE_BEFORE_CANCEL_CHECK);
			count += n;

			if (checkCancelAt <= count) {
				if (writeMonitor.isCancelled()) {
					throw new IOException(
							JGitText.get().packingCancelledDuringObjectsWriting);
				}
				checkCancelAt = count + BYTES_TO_WRITE_BEFORE_CANCEL_CHECK;
			}

			out.write(b, off, n);
			if (crc != null)
				crc.update(b, off, n);
			md.update(b, off, n);

			off += n;
			len -= n;
		}
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	void writeFileHeader(int version, long objectCount) throws IOException {
		System.arraycopy(Constants.PACK_SIGNATURE, 0, headerBuffer, 0, 4);
		NB.encodeInt32(headerBuffer, 4, version);
		NB.encodeInt32(headerBuffer, 8, (int) objectCount);
		write(headerBuffer, 0, 12);
	}

	/**
	 * Write one object.
	 *
	 * If the object was already written, this method does nothing and returns
	 * quickly. This case occurs whenever an object was written out of order in
	 * order to ensure the delta base occurred before the object that needs it.
	 *
	 * @param otp
	 *            the object to write.
	 * @throws IOException
	 *             the object cannot be read from the object reader, or the
	 *             output stream is no longer accepting output. Caller must
	 *             examine the type of exception and possibly its message to
	 *             distinguish between these cases.
	 */
	public void writeObject(ObjectToPack otp) throws IOException {
		packWriter.writeObject(this, otp);
	}

	/**
	 * Commits the object header onto the stream.
	 * <p>
	 * Once the header has been written, the object representation must be fully
	 * output, or packing must abort abnormally.
	 *
	 * @param otp
	 *            the object to pack. Header information is obtained.
	 * @param rawLength
	 *            number of bytes of the inflated content. For an object that is
	 *            in whole object format, this is the same as the object size.
	 *            For an object that is in a delta format, this is the size of
	 *            the inflated delta instruction stream.
	 * @throws IOException
	 *             the underlying stream refused to accept the header.
	 */
	public void writeHeader(ObjectToPack otp, long rawLength)
			throws IOException {
		if (otp.isDeltaRepresentation()) {
			if (packWriter.isDeltaBaseAsOffset()) {
				ObjectToPack baseInPack = otp.getDeltaBase();
				if (baseInPack != null && baseInPack.isWritten()) {
					final long start = count;
					int n = encodeTypeSize(Constants.OBJ_OFS_DELTA, rawLength);
					write(headerBuffer, 0, n);

					long offsetDiff = start - baseInPack.getOffset();
					n = headerBuffer.length - 1;
					headerBuffer[n] = (byte) (offsetDiff & 0x7F);
					while ((offsetDiff >>= 7) > 0)
						headerBuffer[--n] = (byte) (0x80 | (--offsetDiff & 0x7F));
					write(headerBuffer, n, headerBuffer.length - n);
					return;
				}
			}

			int n = encodeTypeSize(Constants.OBJ_REF_DELTA, rawLength);
			otp.getDeltaBaseId().copyRawTo(headerBuffer, n);
			write(headerBuffer, 0, n + Constants.OBJECT_ID_LENGTH);
		} else {
			int n = encodeTypeSize(otp.getType(), rawLength);
			write(headerBuffer, 0, n);
		}
	}

	private int encodeTypeSize(int type, long rawLength) {
		long nextLength = rawLength >>> 4;
		headerBuffer[0] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (type << 4) | (rawLength & 0x0F));
		rawLength = nextLength;
		int n = 1;
		while (rawLength > 0) {
			nextLength >>>= 7;
			headerBuffer[n++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (rawLength & 0x7F));
			rawLength = nextLength;
		}
		return n;
	}

	/** @return a temporary buffer writers can use to copy data with. */
	public byte[] getCopyBuffer() {
		return copyBuffer;
	}

	void endObject() {
		writeMonitor.update(1);
	}

	/** @return total number of bytes written since stream start. */
	public long length() {
		return count;
	}

	void disableCRC32() {
		crc = null;
	}

	/** @return obtain the current CRC32 register. */
	int getCRC32() {
		return crc != null ? (int) crc.getValue() : 0;
	}

	/** Reinitialize the CRC32 register for a new region. */
	void resetCRC32() {
		if (crc != null)
			crc.reset();
	}

	/** @return obtain the current SHA-1 digest. */
	byte[] getDigest() {
		return md.digest();
	}
}
