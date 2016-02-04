/*
 * Copyright (C) 2016, Yuriy Rotmistrov
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
package org.eclipse.jgit.patch.binary;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Filter output stream intended for encoding to base85
 */
/*
 * Attention was paid to create streaming approach potentially allowing
 * processing of huge files. In future both Apply and Diff commands may be
 * modified to benefit from streaming.
 */
// Reference implementation:
// https://github.com/git/git/blob/master/diff.c
public class GitBinaryPatchOutputStream extends FilterOutputStream
{
	private static final int BYTES_PER_LINE = 52;
	private byte[] outputBuffer = new byte[BYTES_PER_LINE];
	private int buffered;

	/**
	 * @param out
	 *            stream to encode to base85
	 */
	public GitBinaryPatchOutputStream(OutputStream out)
	{
		super(out);
	}

	public void write(byte b[], int off, int len) throws IOException
	{
		while (len > 0)
		{
			int length = Math.min(len, BYTES_PER_LINE - buffered);
			System.arraycopy(b, off, outputBuffer, buffered, length);
			off += length;
			len -= length;
			buffered += length;

			if (buffered == BYTES_PER_LINE)
			{
				writeLine();
			}
		}
	}

	public void write(int b) throws IOException
	{
		buffered++;
		if (buffered == BYTES_PER_LINE)
		{
			writeLine();
		}
	}

	public void flush() throws IOException {
		writeLine();
		out.write('\n');
		out.flush();
	}

	private void writeLine() throws IOException
	{
		if (buffered > 0)
		{
			int lineLength;
			if (buffered <= 26)
			{
				lineLength = buffered + 'A' - 1;
			}
			else
			{
				lineLength = buffered - 26 + 'a' - 1;
			}
			byte[] buffer = buffered == BYTES_PER_LINE ? outputBuffer : Arrays.copyOf(outputBuffer, buffered);
			byte[] encodedBytes = GitBase85Codec.encodeBase85(buffer);
			out.write(lineLength);
			out.write(encodedBytes);
			out.write('\n');
			buffered = 0;
		}
	}
}
