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
 *    https://github.com/git/git/blob/8d530c4d64ffcc853889f7b385f554d53db375ed/base85.c - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.jgit.patch.binary;

import java.io.IOException;

/**
 * Base 85 Codec used in git binary patch
 */
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
