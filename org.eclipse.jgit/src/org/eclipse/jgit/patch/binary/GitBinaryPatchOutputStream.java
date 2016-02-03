/*******************************************************************************
 * Copyright (c) 2016 Rewritten for JGIT from native GIT code by Yuriy Rotmistrov
 * Attention was paid to create streaming approach potentially allowing processing of huge files.
 * In future both Apply and Diff commands may be modified to benefit from streaming.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *    https://github.com/git/git/blob/master/diff.c - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.jgit.patch.binary;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Filter output stream intended for encoding to base85
 */
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
