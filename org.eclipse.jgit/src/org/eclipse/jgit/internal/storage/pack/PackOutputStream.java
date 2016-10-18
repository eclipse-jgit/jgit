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

import static org.eclipse.jgit.lib.Constants.OBJ_OFS_DELTA;
import static org.eclipse.jgit.lib.Constants.OBJ_REF_DELTA;
import static org.eclipse.jgit.lib.Constants.PACK_SIGNATURE;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

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

	private final MessageDigest md = Constants.newMessageDigest();

	private long count;

	private final byte[] headerBuffer = new byte[32];

	private final byte[] copyBuffer = new byte[64 << 10];

	private long checkCancelAt;

	private boolean ofsDelta;

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
	public final void write(final int b) throws IOException {
		count++;
		out.write(b);
		md.update((byte) b);
	}

	@Override
	public final void write(final byte[] b, int off, int len)
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
			md.update(b, off, n);

			off += n;
			len -= n;
		}
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	final void writeFileHeader(int version, long objectCount)
			throws IOException {
		System.arraycopy(PACK_SIGNATURE, 0, headerBuffer, 0, 4);
		NB.encodeInt32(headerBuffer, 4, version);
		NB.encodeInt32(headerBuffer, 8, (int) objectCount);
		write(headerBuffer, 0, 12);
		ofsDelta = packWriter.isDeltaBaseAsOffset();
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
	public final void writeObject(ObjectToPack otp) throws IOException {
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
	public final void writeHeader(ObjectToPack otp, long rawLength)
			throws IOException {
		ObjectToPack b = otp.getDeltaBase();
		if (b != null && (b.isWritten() & ofsDelta)) { // Non-short-circuit logic is intentional
			int n = objectHeader(rawLength, OBJ_OFS_DELTA, headerBuffer);
			n = ofsDelta(count - b.getOffset(), headerBuffer, n);
			write(headerBuffer, 0, n);
		} else if (otp.isDeltaRepresentation()) {
			int n = objectHeader(rawLength, OBJ_REF_DELTA, headerBuffer);
			otp.getDeltaBaseId().copyRawTo(headerBuffer, n);
			write(headerBuffer, 0, n + 20);
		} else {
			int n = objectHeader(rawLength, otp.getType(), headerBuffer);
			write(headerBuffer, 0, n);
		}
	}

	private static final int objectHeader(long len, int type, byte[] buf) {
		byte b = (byte) ((type << 4) | (len & 0x0F));
		int n = 0;
		for (len >>>= 4; len != 0; len >>>= 7) {
			buf[n++] = (byte) (0x80 | b);
			b = (byte) (len & 0x7F);
		}
		buf[n++] = b;
		return n;
	}

	private static final int ofsDelta(long diff, byte[] buf, int p) {
		p += ofsDeltaVarIntLength(diff);
		int n = p;
		buf[--n] = (byte) (diff & 0x7F);
		while ((diff >>>= 7) != 0)
			buf[--n] = (byte) (0x80 | (--diff & 0x7F));
		return p;
	}

	private static final int ofsDeltaVarIntLength(long v) {
		int n = 1;
		for (; (v >>>= 7) != 0; n++)
			--v;
		return n;
	}

	/** @return a temporary buffer writers can use to copy data with. */
	public final byte[] getCopyBuffer() {
		return copyBuffer;
	}

	void endObject() {
		writeMonitor.update(1);
	}

	/** @return total number of bytes written since stream start. */
	public final long length() {
		return count;
	}

	/** @return obtain the current SHA-1 digest. */
	final byte[] getDigest() {
		return md.digest();
	}
}
