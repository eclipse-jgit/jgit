/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal.keys;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.operator.PBEProtectionRemoverFactory;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEProtectionRemoverFactory;
import org.bouncycastle.util.io.Streams;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.gpg.bc.internal.BCText;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Utilities for reading GPG secret keys from a gpg-agent key file.
 */
public final class SecretKeys {

	private SecretKeys() {
		// No instantiation.
	}

	/**
	 * Something that can supply a passphrase to decrypt an encrypted secret
	 * key.
	 */
	public interface PassphraseSupplier {

		/**
		 * Supplies a passphrase.
		 *
		 * @return the passphrase
		 * @throws PGPException
		 *             if no passphrase can be obtained
		 * @throws CanceledException
		 *             if the user canceled passphrase entry
		 * @throws UnsupportedCredentialItem
		 *             if an internal error occurred
		 * @throws URISyntaxException
		 *             if an internal error occurred
		 */
		char[] getPassphrase() throws PGPException, CanceledException,
				UnsupportedCredentialItem, URISyntaxException;
	}

	private static final byte[] PROTECTED_KEY = "protected-private-key" //$NON-NLS-1$
			.getBytes(StandardCharsets.US_ASCII);

	private static final byte[] OCB_PROTECTED = "openpgp-s2k3-ocb-aes" //$NON-NLS-1$
			.getBytes(StandardCharsets.US_ASCII);

	/**
	 * Reads a GPG secret key from the given stream.
	 *
	 * @param in
	 *            {@link InputStream} to read from, doesn't need to be buffered
	 * @param calculatorProvider
	 *            for checking digests
	 * @param passphraseSupplier
	 *            for decrypting encrypted keys
	 * @param publicKey
	 *            the secret key should be for
	 * @return the secret key
	 * @throws IOException
	 *             if the stream cannot be parsed
	 * @throws PGPException
	 *             if thrown by the underlying S-Expression parser, for instance
	 *             when the passphrase is wrong
	 * @throws CanceledException
	 *             if thrown by the {@code passphraseSupplier}
	 * @throws UnsupportedCredentialItem
	 *             if thrown by the {@code passphraseSupplier}
	 * @throws URISyntaxException
	 *             if thrown by the {@code passphraseSupplier}
	 */
	public static PGPSecretKey readSecretKey(InputStream in,
			PGPDigestCalculatorProvider calculatorProvider,
			PassphraseSupplier passphraseSupplier, PGPPublicKey publicKey)
			throws IOException, PGPException, CanceledException,
			UnsupportedCredentialItem, URISyntaxException {
		byte[] data = Streams.readAll(in);
		if (data.length == 0) {
			throw new EOFException();
		} else if (data.length < 4 + PROTECTED_KEY.length) {
			// +4 for "(21:" for a binary protected key
			throw new IOException(
					MessageFormat.format(BCText.get().secretKeyTooShort,
							Integer.toUnsignedString(data.length)));
		}
		SExprParser parser = new SExprParser(calculatorProvider);
		byte firstChar = data[0];
		try {
			if (firstChar == '(') {
				// Binary format.
				PBEProtectionRemoverFactory decryptor = null;
				if (matches(data, 4, PROTECTED_KEY)) {
					// AES/CBC encrypted.
					decryptor = new JcePBEProtectionRemoverFactory(
							passphraseSupplier.getPassphrase(),
							calculatorProvider);
				}
				try (InputStream sIn = new ByteArrayInputStream(data)) {
					return parser.parseSecretKey(sIn, decryptor, publicKey);
				}
			}
			// Assume it's the new key-value format.
			try (ByteArrayInputStream keyIn = new ByteArrayInputStream(data)) {
				byte[] rawData = keyFromNameValueFormat(keyIn);
				if (!matches(rawData, 1, PROTECTED_KEY)) {
					// Not encrypted human-readable format.
					try (InputStream sIn = new ByteArrayInputStream(
							convertSexpression(rawData))) {
						return parser.parseSecretKey(sIn, null, publicKey);
					}
				}
				// An encrypted key from a key-value file. Most likely AES/OCB
				// encrypted.
				boolean isOCB[] = { false };
				byte[] sExp = convertSexpression(rawData, isOCB);
				PBEProtectionRemoverFactory decryptor;
				if (isOCB[0]) {
					decryptor = new OCBPBEProtectionRemoverFactory(
							passphraseSupplier.getPassphrase(),
							calculatorProvider, getAad(sExp));
				} else {
					decryptor = new JcePBEProtectionRemoverFactory(
							passphraseSupplier.getPassphrase(),
							calculatorProvider);
				}
				try (InputStream sIn = new ByteArrayInputStream(sExp)) {
					return parser.parseSecretKey(sIn, decryptor, publicKey);
				}
			}
		} catch (IOException e) {
			throw new PGPException(e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Extract the AAD for the OCB decryption from an s-expression.
	 *
	 * @param sExp
	 *            buffer containing a valid binary s-expression
	 * @return the AAD
	 */
	private static byte[] getAad(byte[] sExp) {
		// Given a key
		// @formatter:off
		// (protected-private-key (rsa ... (protected openpgp-s2k3-ocb-aes ... )(protected-at ...)))
		//                        A        B                                    C                  D
		// The AAD is [A..B)[C..D). (From the binary serialized form.)
		// @formatter:on
		int i = 1; // Skip initial '('
		while (sExp[i] != '(') {
			i++;
		}
		int aadStart = i++;
		int aadEnd = skip(sExp, aadStart);
		byte[] protectedPrefix = "(9:protected" //$NON-NLS-1$
				.getBytes(StandardCharsets.US_ASCII);
		while (!matches(sExp, i, protectedPrefix)) {
			i++;
		}
		int protectedStart = i;
		int protectedEnd = skip(sExp, protectedStart);
		byte[] aadData = new byte[aadEnd - aadStart
				- (protectedEnd - protectedStart)];
		System.arraycopy(sExp, aadStart, aadData, 0, protectedStart - aadStart);
		System.arraycopy(sExp, protectedEnd, aadData, protectedStart - aadStart,
				aadEnd - protectedEnd);
		return aadData;
	}

	/**
	 * Skips a list including nested lists.
	 *
	 * @param sExp
	 *            buffer containing valid binary s-expression data
	 * @param start
	 *            index of the opening '(' of the list to skip
	 * @return the index after the closing ')' of the skipped list
	 */
	private static int skip(byte[] sExp, int start) {
		int i = start + 1;
		int depth = 1;
		while (depth > 0) {
			switch (sExp[i]) {
			case '(':
				depth++;
				break;
			case ')':
				depth--;
				break;
			default:
				// We must be on a length
				int j = i;
				while (sExp[j] >= '0' && sExp[j] <= '9') {
					j++;
				}
				// j is on the colon
				int length = Integer.parseInt(
						new String(sExp, i, j - i, StandardCharsets.US_ASCII));
				i = j + length;
			}
			i++;
		}
		return i;
	}

	/**
	 * Checks whether the {@code needle} matches {@code src} at offset
	 * {@code from}.
	 *
	 * @param src
	 *            to match against {@code needle}
	 * @param from
	 *            position in {@code src} to start matching
	 * @param needle
	 *            to match against
	 * @return {@code true} if {@code src} contains {@code needle} at position
	 *         {@code from}, {@code false} otherwise
	 */
	private static boolean matches(byte[] src, int from, byte[] needle) {
		if (from < 0 || from + needle.length > src.length) {
			return false;
		}
		return org.bouncycastle.util.Arrays.constantTimeAreEqual(needle.length,
				src, from, needle, 0);
	}

	/**
	 * Converts a human-readable serialized s-expression into a binary
	 * serialized s-expression.
	 *
	 * @param humanForm
	 *            to convert
	 * @return the converted s-expression
	 * @throws IOException
	 *             if the conversion fails
	 */
	private static byte[] convertSexpression(byte[] humanForm)
			throws IOException {
		boolean[] isOCB = { false };
		return convertSexpression(humanForm, isOCB);
	}

	/**
	 * Converts a human-readable serialized s-expression into a binary
	 * serialized s-expression.
	 *
	 * @param humanForm
	 *            to convert
	 * @param isOCB
	 *            returns whether the s-expression specified AES/OCB encryption
	 * @return the converted s-expression
	 * @throws IOException
	 *             if the conversion fails
	 */
	private static byte[] convertSexpression(byte[] humanForm, boolean[] isOCB)
			throws IOException {
		int pos = 0;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(
				humanForm.length)) {
			while (pos < humanForm.length) {
				byte b = humanForm[pos];
				if (b == '(' || b == ')') {
					out.write(b);
					pos++;
				} else if (isGpgSpace(b)) {
					pos++;
				} else if (b == '#') {
					// Hex value follows up to the next #
					int i = ++pos;
					while (i < humanForm.length && isHex(humanForm[i])) {
						i++;
					}
					if (i == pos || humanForm[i] != '#') {
						throw new StreamCorruptedException(
								BCText.get().sexprHexNotClosed);
					}
					if ((i - pos) % 2 != 0) {
						throw new StreamCorruptedException(
								BCText.get().sexprHexOdd);
					}
					int l = (i - pos) / 2;
					out.write(Integer.toString(l)
							.getBytes(StandardCharsets.US_ASCII));
					out.write(':');
					while (pos < i) {
						int x = (nibble(humanForm[pos]) << 4)
								| nibble(humanForm[pos + 1]);
						pos += 2;
						out.write(x);
					}
					pos = i + 1;
				} else if (isTokenChar(b)) {
					// Scan the token
					int start = pos++;
					while (pos < humanForm.length
							&& isTokenChar(humanForm[pos])) {
						pos++;
					}
					int l = pos - start;
					if (pos - start == OCB_PROTECTED.length
							&& matches(humanForm, start, OCB_PROTECTED)) {
						isOCB[0] = true;
					}
					out.write(Integer.toString(l)
							.getBytes(StandardCharsets.US_ASCII));
					out.write(':');
					out.write(humanForm, start, pos - start);
				} else if (b == '"') {
					// Potentially quoted string.
					int start = ++pos;
					boolean escaped = false;
					while (pos < humanForm.length
							&& (escaped || humanForm[pos] != '"')) {
						int ch = humanForm[pos++];
						escaped = !escaped && ch == '\\';
					}
					if (pos >= humanForm.length) {
						throw new StreamCorruptedException(
								BCText.get().sexprStringNotClosed);
					}
					// start is on the first character of the string, pos on the
					// closing quote.
					byte[] dq = dequote(humanForm, start, pos);
					out.write(Integer.toString(dq.length)
							.getBytes(StandardCharsets.US_ASCII));
					out.write(':');
					out.write(dq);
					pos++;
				} else {
					throw new StreamCorruptedException(
							MessageFormat.format(BCText.get().sexprUnhandled,
									Integer.toHexString(b & 0xFF)));
				}
			}
			return out.toByteArray();
		}
	}

	/**
	 * GPG-style string de-quoting, which is basically C-style, with some
	 * literal CR/LF escaping.
	 *
	 * @param in
	 *            buffer containing the quoted string
	 * @param from
	 *            index after the opening quote in {@code in}
	 * @param to
	 *            index of the closing quote in {@code in}
	 * @return the dequoted raw string value
	 * @throws StreamCorruptedException
	 *             if object stream is corrupt
	 */
	private static byte[] dequote(byte[] in, int from, int to)
			throws StreamCorruptedException {
		// Result must be shorter or have the same length
		byte[] out = new byte[to - from];
		int j = 0;
		int i = from;
		while (i < to) {
			byte b = in[i++];
			if (b != '\\') {
				out[j++] = b;
				continue;
			}
			if (i == to) {
				throw new StreamCorruptedException(
						BCText.get().sexprStringInvalidEscapeAtEnd);
			}
			b = in[i++];
			switch (b) {
			case 'b':
				out[j++] = '\b';
				break;
			case 'f':
				out[j++] = '\f';
				break;
			case 'n':
				out[j++] = '\n';
				break;
			case 'r':
				out[j++] = '\r';
				break;
			case 't':
				out[j++] = '\t';
				break;
			case 'v':
				out[j++] = 0x0B;
				break;
			case '"':
			case '\'':
			case '\\':
				out[j++] = b;
				break;
			case '\r':
				// Escaped literal line end. If an LF is following, skip that,
				// too.
				if (i < to && in[i] == '\n') {
					i++;
				}
				break;
			case '\n':
				// Same for LF possibly followed by CR.
				if (i < to && in[i] == '\r') {
					i++;
				}
				break;
			case 'x':
				if (i + 1 >= to || !isHex(in[i]) || !isHex(in[i + 1])) {
					throw new StreamCorruptedException(
							BCText.get().sexprStringInvalidHexEscape);
				}
				out[j++] = (byte) ((nibble(in[i]) << 4) | nibble(in[i + 1]));
				i += 2;
				break;
			case '0':
			case '1':
			case '2':
			case '3':
				if (i + 2 >= to || !isOctal(in[i]) || !isOctal(in[i + 1])
						|| !isOctal(in[i + 2])) {
					throw new StreamCorruptedException(
							BCText.get().sexprStringInvalidOctalEscape);
				}
				out[j++] = (byte) (((((in[i] - '0') << 3)
						| (in[i + 1] - '0')) << 3) | (in[i + 2] - '0'));
				i += 3;
				break;
			default:
				throw new StreamCorruptedException(MessageFormat.format(
						BCText.get().sexprStringInvalidEscape,
						Integer.toHexString(b & 0xFF)));
			}
		}
		return Arrays.copyOf(out, j);
	}

	/**
	 * Extracts the key from a GPG name-value-pair key file.
	 * <p>
	 * Package-visible for tests only.
	 * </p>
	 *
	 * @param in
	 *            {@link InputStream} to read from; should be buffered
	 * @return the raw key data as extracted from the file
	 * @throws IOException
	 *             if the {@code in} stream cannot be read or does not contain a
	 *             key
	 */
	static byte[] keyFromNameValueFormat(InputStream in) throws IOException {
		// It would be nice if we could use RawParseUtils here, but GPG compares
		// names case-insensitively. We're only interested in the "Key:"
		// name-value pair.
		int[] nameLow = { 'k', 'e', 'y', ':' };
		int[] nameCap = { 'K', 'E', 'Y', ':' };
		int nameIdx = 0;
		for (;;) {
			int next = in.read();
			if (next < 0) {
				throw new EOFException();
			}
			if (next == '\n') {
				nameIdx = 0;
			} else if (nameIdx >= 0) {
				if (nameLow[nameIdx] == next || nameCap[nameIdx] == next) {
					nameIdx++;
					if (nameIdx == nameLow.length) {
						break;
					}
				} else {
					nameIdx = -1;
				}
			}
		}
		// We're after "Key:". Read the value as continuation lines.
		int last = ':';
		byte[] rawData;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(8192)) {
			for (;;) {
				int next = in.read();
				if (next < 0) {
					break;
				}
				if (last == '\n') {
					if (next == ' ' || next == '\t') {
						// Continuation line; skip this whitespace
						last = next;
						continue;
					}
					break; // Not a continuation line
				}
				out.write(next);
				last = next;
			}
			rawData = out.toByteArray();
		}
		// GPG trims off trailing whitespace, and a line having only whitespace
		// is a single LF.
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(
				rawData.length)) {
			int lineStart = 0;
			boolean trimLeading = true;
			while (lineStart < rawData.length) {
				int nextLineStart = RawParseUtils.nextLF(rawData, lineStart);
				if (trimLeading) {
					while (lineStart < nextLineStart
							&& isGpgSpace(rawData[lineStart])) {
						lineStart++;
					}
				}
				// Trim trailing
				int i = nextLineStart - 1;
				while (lineStart < i && isGpgSpace(rawData[i])) {
					i--;
				}
				if (i <= lineStart) {
					// Empty line signifies LF
					out.write('\n');
					trimLeading = true;
				} else {
					out.write(rawData, lineStart, i - lineStart + 1);
					trimLeading = false;
				}
				lineStart = nextLineStart;
			}
			return out.toByteArray();
		}
	}

	private static boolean isGpgSpace(int ch) {
		return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
	}

	private static boolean isTokenChar(int ch) {
		switch (ch) {
		case '-':
		case '.':
		case '/':
		case '_':
		case ':':
		case '*':
		case '+':
		case '=':
			return true;
		default:
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
					|| (ch >= '0' && ch <= '9')) {
				return true;
			}
			return false;
		}
	}

	private static boolean isHex(int ch) {
		return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F')
				|| (ch >= 'a' && ch <= 'f');
	}

	private static boolean isOctal(int ch) {
		return (ch >= '0' && ch <= '7');
	}

	private static int nibble(int ch) {
		if (ch >= '0' && ch <= '9') {
			return ch - '0';
		} else if (ch >= 'A' && ch <= 'F') {
			return ch - 'A' + 10;
		} else if (ch >= 'a' && ch <= 'f') {
			return ch - 'a' + 10;
		}
		return -1;
	}
}
