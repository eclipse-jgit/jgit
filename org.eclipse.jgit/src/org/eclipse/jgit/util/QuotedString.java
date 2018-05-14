/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.util;

import java.util.Arrays;

import org.eclipse.jgit.lib.Constants;

/**
 * Utility functions related to quoted string handling.
 */
public abstract class QuotedString {
	/** Quoting style that obeys the rules Git applies to file names */
	public static final GitPathStyle GIT_PATH = new GitPathStyle();

	/**
	 * Quoting style used by the Bourne shell.
	 * <p>
	 * Quotes are unconditionally inserted during {@link #quote(String)}. This
	 * protects shell meta-characters like <code>$</code> or <code>~</code> from
	 * being recognized as special.
	 */
	public static final BourneStyle BOURNE = new BourneStyle();

	/** Bourne style, but permits <code>~user</code> at the start of the string. */
	public static final BourneUserPathStyle BOURNE_USER_PATH = new BourneUserPathStyle();

	/**
	 * Quote an input string by the quoting rules.
	 * <p>
	 * If the input string does not require any quoting, the same String
	 * reference is returned to the caller.
	 * <p>
	 * Otherwise a quoted string is returned, including the opening and closing
	 * quotation marks at the start and end of the string. If the style does not
	 * permit raw Unicode characters then the string will first be encoded in
	 * UTF-8, with unprintable sequences possibly escaped by the rules.
	 *
	 * @param in
	 *            any non-null Unicode string.
	 * @return a quoted string. See above for details.
	 */
	public abstract String quote(String in);

	/**
	 * Clean a previously quoted input, decoding the result via UTF-8.
	 * <p>
	 * This method must match quote such that:
	 *
	 * <pre>
	 * a.equals(dequote(quote(a)));
	 * </pre>
	 *
	 * is true for any <code>a</code>.
	 *
	 * @param in
	 *            a Unicode string to remove quoting from.
	 * @return the cleaned string.
	 * @see #dequote(byte[], int, int)
	 */
	public String dequote(String in) {
		final byte[] b = Constants.encode(in);
		return dequote(b, 0, b.length);
	}

	/**
	 * Decode a previously quoted input, scanning a UTF-8 encoded buffer.
	 * <p>
	 * This method must match quote such that:
	 *
	 * <pre>
	 * a.equals(dequote(Constants.encode(quote(a))));
	 * </pre>
	 *
	 * is true for any <code>a</code>.
	 * <p>
	 * This method removes any opening/closing quotation marks added by
	 * {@link #quote(String)}.
	 *
	 * @param in
	 *            the input buffer to parse.
	 * @param offset
	 *            first position within <code>in</code> to scan.
	 * @param end
	 *            one position past in <code>in</code> to scan.
	 * @return the cleaned string.
	 */
	public abstract String dequote(byte[] in, int offset, int end);

	/**
	 * Quoting style used by the Bourne shell.
	 * <p>
	 * Quotes are unconditionally inserted during {@link #quote(String)}. This
	 * protects shell meta-characters like <code>$</code> or <code>~</code> from
	 * being recognized as special.
	 */
	public static class BourneStyle extends QuotedString {
		@Override
		public String quote(String in) {
			final StringBuilder r = new StringBuilder();
			r.append('\'');
			int start = 0, i = 0;
			for (; i < in.length(); i++) {
				switch (in.charAt(i)) {
				case '\'':
				case '!':
					r.append(in, start, i);
					r.append('\'');
					r.append('\\');
					r.append(in.charAt(i));
					r.append('\'');
					start = i + 1;
					break;
				}
			}
			r.append(in, start, i);
			r.append('\'');
			return r.toString();
		}

		@Override
		public String dequote(byte[] in, int ip, int ie) {
			boolean inquote = false;
			final byte[] r = new byte[ie - ip];
			int rPtr = 0;
			while (ip < ie) {
				final byte b = in[ip++];
				switch (b) {
				case '\'':
					inquote = !inquote;
					continue;
				case '\\':
					if (inquote || ip == ie)
						r[rPtr++] = b; // literal within a quote
					else
						r[rPtr++] = in[ip++];
					continue;
				default:
					r[rPtr++] = b;
					continue;
				}
			}
			return RawParseUtils.decode(Constants.CHARSET, r, 0, rPtr);
		}
	}

	/** Bourne style, but permits <code>~user</code> at the start of the string. */
	public static class BourneUserPathStyle extends BourneStyle {
		@Override
		public String quote(String in) {
			if (in.matches("^~[A-Za-z0-9_-]+$")) { //$NON-NLS-1$
				// If the string is just "~user" we can assume they
				// mean "~user/".
				//
				return in + "/"; //$NON-NLS-1$
			}

			if (in.matches("^~[A-Za-z0-9_-]*/.*$")) { //$NON-NLS-1$
				// If the string is of "~/path" or "~user/path"
				// we must not escape ~/ or ~user/ from the shell.
				//
				final int i = in.indexOf('/') + 1;
				if (i == in.length())
					return in;
				return in.substring(0, i) + super.quote(in.substring(i));
			}

			return super.quote(in);
		}
	}

	/** Quoting style that obeys the rules Git applies to file names */
	public static final class GitPathStyle extends QuotedString {
		private static final byte[] quote;
		static {
			quote = new byte[128];
			Arrays.fill(quote, (byte) -1);

			for (int i = '0'; i <= '9'; i++)
				quote[i] = 0;
			for (int i = 'a'; i <= 'z'; i++)
				quote[i] = 0;
			for (int i = 'A'; i <= 'Z'; i++)
				quote[i] = 0;
			quote[' '] = 0;
			quote['$'] = 0;
			quote['%'] = 0;
			quote['&'] = 0;
			quote['*'] = 0;
			quote['+'] = 0;
			quote[','] = 0;
			quote['-'] = 0;
			quote['.'] = 0;
			quote['/'] = 0;
			quote[':'] = 0;
			quote[';'] = 0;
			quote['='] = 0;
			quote['?'] = 0;
			quote['@'] = 0;
			quote['_'] = 0;
			quote['^'] = 0;
			quote['|'] = 0;
			quote['~'] = 0;

			quote['\u0007'] = 'a';
			quote['\b'] = 'b';
			quote['\f'] = 'f';
			quote['\n'] = 'n';
			quote['\r'] = 'r';
			quote['\t'] = 't';
			quote['\u000B'] = 'v';
			quote['\\'] = '\\';
			quote['"'] = '"';
		}

		@Override
		public String quote(String instr) {
			if (instr.length() == 0)
				return "\"\""; //$NON-NLS-1$
			boolean reuse = true;
			final byte[] in = Constants.encode(instr);
			final StringBuilder r = new StringBuilder(2 + in.length);
			r.append('"');
			for (int i = 0; i < in.length; i++) {
				final int c = in[i] & 0xff;
				if (c < quote.length) {
					final byte style = quote[c];
					if (style == 0) {
						r.append((char) c);
						continue;
					}
					if (style > 0) {
						reuse = false;
						r.append('\\');
						r.append((char) style);
						continue;
					}
				}

				reuse = false;
				r.append('\\');
				r.append((char) (((c >> 6) & 03) + '0'));
				r.append((char) (((c >> 3) & 07) + '0'));
				r.append((char) (((c >> 0) & 07) + '0'));
			}
			if (reuse)
				return instr;
			r.append('"');
			return r.toString();
		}

		@Override
		public String dequote(byte[] in, int inPtr, int inEnd) {
			if (2 <= inEnd - inPtr && in[inPtr] == '"' && in[inEnd - 1] == '"')
				return dq(in, inPtr + 1, inEnd - 1);
			return RawParseUtils.decode(Constants.CHARSET, in, inPtr, inEnd);
		}

		private static String dq(byte[] in, int inPtr, int inEnd) {
			final byte[] r = new byte[inEnd - inPtr];
			int rPtr = 0;
			while (inPtr < inEnd) {
				final byte b = in[inPtr++];
				if (b != '\\') {
					r[rPtr++] = b;
					continue;
				}

				if (inPtr == inEnd) {
					// Lone trailing backslash. Treat it as a literal.
					//
					r[rPtr++] = '\\';
					break;
				}

				switch (in[inPtr++]) {
				case 'a':
					r[rPtr++] = 0x07 /* \a = BEL */;
					continue;
				case 'b':
					r[rPtr++] = '\b';
					continue;
				case 'f':
					r[rPtr++] = '\f';
					continue;
				case 'n':
					r[rPtr++] = '\n';
					continue;
				case 'r':
					r[rPtr++] = '\r';
					continue;
				case 't':
					r[rPtr++] = '\t';
					continue;
				case 'v':
					r[rPtr++] = 0x0B/* \v = VT */;
					continue;

				case '\\':
				case '"':
					r[rPtr++] = in[inPtr - 1];
					continue;

				case '0':
				case '1':
				case '2':
				case '3': {
					int cp = in[inPtr - 1] - '0';
					for (int n = 1; n < 3 && inPtr < inEnd; n++) {
						final byte c = in[inPtr];
						if ('0' <= c && c <= '7') {
							cp <<= 3;
							cp |= c - '0';
							inPtr++;
						} else {
							break;
						}
					}
					r[rPtr++] = (byte) cp;
					continue;
				}

				default:
					// Any other code is taken literally.
					//
					r[rPtr++] = '\\';
					r[rPtr++] = in[inPtr - 1];
					continue;
				}
			}

			return RawParseUtils.decode(Constants.CHARSET, r, 0, rPtr);
		}

		private GitPathStyle() {
			// Singleton
		}
	}
}
