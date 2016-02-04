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

import java.io.IOException;

/**
 * Base 85 Codec used in git binary patch
 */
// Reference implementation -
// https://github.com/git/git/blob/8d530c4d64ffcc853889f7b385f554d53db375ed/base85.c
public class GitBase85Codec
{
	private static final byte[] ENCODE_TABLE;
	private static final byte[] DECODE_TABLE;

	static
	{
		ENCODE_TABLE = new byte[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
				'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b',
				'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
				'x', 'y', 'z', '!', '#', '$', '%', '&', '(', ')', '*', '+', '-', ';', '<', '=', '>', '?', '@', '^', '_',
				'`', '{', '|', '}', '~' };

		DECODE_TABLE = new byte[256];
		for (int i = 0; i < DECODE_TABLE.length; ++i)
		{
			DECODE_TABLE[i] = -1;
		}

		for (int i = 0; i < ENCODE_TABLE.length; ++i)
		{
			DECODE_TABLE[(ENCODE_TABLE[i] & 0xFF)] = (byte) i;
		}
	}

	/**
	 * Decodes input bytes
	 *
	 * @param encoded
	 *            base85 encoded bytes
	 * @param len
	 *            expected number of bytes in decoded sequence
	 * @return decoded bytes
	 * @throws IOException
	 *             if input bytes contains characters or sequences out of base85
	 *             alphabet
	 */
	public static byte[] decodeBase85(String encoded, int len)
			throws IOException
	{
		byte[] result = new byte[len];
		int i = 0;
		int j = 0;
		while (len > 0)
		{
			long acc = 0;
			byte de;
			int cnt = 4;
			char ch;
			do
			{
				ch = encoded.charAt(i++);
				de = DECODE_TABLE[ch];
				if (de < 0)
				{
					throw new IOException(
							String.format("invalid base85 alphabet %c", //$NON-NLS-1$
									Character.valueOf(ch)));
				}
				acc = acc * 85 + de;
			} while (--cnt > 0);
			ch = encoded.charAt(i++);
			de = DECODE_TABLE[ch];
			if (de < 0)
			{
				throw new IOException(String.format(
						"invalid base85 alphabet %c", Character.valueOf(ch))); //$NON-NLS-1$
			}
			/* Detect overflow. */
			if (0xffffffffL / 85 < acc || 0xffffffffL - de < (acc *= 85))
			{
				throw new IOException(
						String.format("invalid base85 sequence %.5s", //$NON-NLS-1$
								encoded.substring(i - 5, i)));
			}
			acc += de;

			cnt = (len < 4) ? len : 4;
			len -= cnt;
			int word = (int) (acc & 0xFFFFFFFF);
			do
			{
				word = (word << 8) | (word >>> 24);
				result[j++] = (byte) (word & 0xFF);
			} while (--cnt > 0);
		}

		return result;
	}

	/**
	 * Encodes input bytes using base85 alphabet
	 *
	 * @param data
	 *            bytes to encode
	 * @return encoded bytes
	 */
	public static byte[] encodeBase85(byte[] data)
	{
		byte[] result = new byte[(data.length / 4 + (data.length % 4 > 0 ? 1 : 0)) * 5];

		int i = 0, j = 0;
		while (i < data.length)
		{
			long acc = 0;
			int cnt;
			for (cnt = 24; cnt >= 0; cnt -= 8)
			{
				long ch = data[i++] & 0xFF;
				acc |= ch << cnt;
				if (i == data.length)
				{
					break;
				}
			}

			for (cnt = 4; cnt >= 0; cnt--)
			{
				int val = (int) (acc % 85);
				acc /= 85;
				result[j + cnt] = ENCODE_TABLE[val];
			}
			j += 5;
		}
		return result;
	}
}
