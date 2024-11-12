/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.util.buffer.BufferUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.StringUtils;

/**
 * An implementation of OpenSSH binary format key revocation lists (KRLs).
 *
 * @see <a href=
 *      "https://github.com/openssh/openssh-portable/blob/master/PROTOCOL.krl">PROTOCOL.krl</a>
 */
class OpenSshBinaryKrl {

	/**
	 * The "magic" bytes at the start of an OpenSSH binary KRL.
	 */
	static final byte[] MAGIC = { 'S', 'S', 'H', 'K', 'R', 'L', '\n', 0 };

	private static final int FORMAT_VERSION = 1;

	private static final int SECTION_CERTIFICATES = 1;

	private static final int SECTION_KEY = 2;

	private static final int SECTION_SHA1 = 3;

	private static final int SECTION_SIGNATURE = 4; // Skipped

	private static final int SECTION_SHA256 = 5;

	private static final int SECTION_EXTENSION = 255; // Skipped

	// Certificates

	private static final int CERT_SERIAL_LIST = 0x20;

	private static final int CERT_SERIAL_RANGES = 0x21;

	private static final int CERT_SERIAL_BITS = 0x22;

	private static final int CERT_KEY_IDS = 0x23;

	private static final int CERT_EXTENSIONS = 0x39; // Skipped

	private final Map<Blob, CertificateRevocation> certificates = new HashMap<>();

	private static class CertificateRevocation {

		final SerialRangeSet ranges = new SerialRangeSet();

		final Set<String> keyIds = new HashSet<>();
	}

	// Plain keys

	/**
	 * A byte array that can be used as a key in a {@link Map} or {@link Set}.
	 * {@link #equals(Object)} and {@link #hashCode()} are based on the content.
	 *
	 * @param blob
	 *            the array to wrap
	 */
	@SuppressWarnings("ArrayRecordComponent")
	private static record Blob(byte[] blob) {

		@Override
		public final boolean equals(Object any) {
			if (this == any) {
				return true;
			}
			if (any == null || !(any instanceof Blob)) {
				return false;
			}
			Blob other = (Blob) any;
			return Arrays.equals(blob, other.blob);
		}

		@Override
		public final int hashCode() {
			return Arrays.hashCode(blob);
		}
	}

	private final Set<Blob> blobs = new HashSet<>();

	private final Set<Blob> sha1 = new HashSet<>();

	private final Set<Blob> sha256 = new HashSet<>();

	private OpenSshBinaryKrl() {
		// No public instantiation, use load(InputStream, boolean) instead.
	}

	/**
	 * Tells whether the given key has been revoked.
	 *
	 * @param key
	 *            {@link PublicKey} to check
	 * @return {@code true} if the key was revoked, {@code false} otherwise
	 */
	boolean isRevoked(PublicKey key) {
		if (key instanceof OpenSshCertificate certificate) {
			if (certificates.isEmpty()) {
				return false;
			}
			// These apply to all certificates
			if (isRevoked(certificate, certificates.get(null))) {
				return true;
			}
			if (isRevoked(certificate,
					certificates.get(blob(certificate.getCaPubKey())))) {
				return true;
			}
			// Keys themselves are checked in OpenSshKrl.
			return false;
		}
		if (!blobs.isEmpty() && blobs.contains(blob(key))) {
			return true;
		}
		if (!sha256.isEmpty() && sha256.contains(hash("SHA256", key))) { //$NON-NLS-1$
			return true;
		}
		if (!sha1.isEmpty() && sha1.contains(hash("SHA1", key))) { //$NON-NLS-1$
			return true;
		}
		return false;
	}

	private boolean isRevoked(OpenSshCertificate certificate,
			CertificateRevocation revocations) {
		if (revocations == null) {
			return false;
		}
		String id = certificate.getId();
		if (!StringUtils.isEmptyOrNull(id) && revocations.keyIds.contains(id)) {
			return true;
		}
		long serial = certificate.getSerial();
		if (serial != 0 && revocations.ranges.contains(serial)) {
			return true;
		}
		return false;
	}

	private Blob blob(PublicKey key) {
		ByteArrayBuffer buf = new ByteArrayBuffer();
		buf.putRawPublicKey(key);
		return new Blob(buf.getCompactData());
	}

	private Blob hash(String algorithm, PublicKey key) {
		ByteArrayBuffer buf = new ByteArrayBuffer();
		buf.putRawPublicKey(key);
		try {
			return new Blob(MessageDigest.getInstance(algorithm)
					.digest(buf.getCompactData()));
		} catch (NoSuchAlgorithmException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Loads a binary KRL from the given stream.
	 *
	 * @param in
	 *            {@link InputStream} to read from
	 * @param magicSkipped
	 *            whether the {@link #MAGIC} bytes at the beginning have already
	 *            been skipped
	 * @return a new {@link OpenSshBinaryKrl}.
	 * @throws IOException
	 *             if the stream cannot be read as an OpenSSH binary KRL
	 */
	@NonNull
	static OpenSshBinaryKrl load(InputStream in, boolean magicSkipped)
			throws IOException {
		if (!magicSkipped) {
			byte[] magic = new byte[MAGIC.length];
			IO.readFully(in, magic);
			if (!Arrays.equals(magic, MAGIC)) {
				throw new StreamCorruptedException(
						SshdText.get().signKrlInvalidMagic);
			}
		}
		skipHeader(in);
		return load(in);
	}

	private static long getUInt(InputStream in) throws IOException {
		byte[] buf = new byte[Integer.BYTES];
		IO.readFully(in, buf);
		return BufferUtils.getUInt(buf);
	}

	private static long getLong(InputStream in) throws IOException {
		byte[] buf = new byte[Long.BYTES];
		IO.readFully(in, buf);
		return BufferUtils.getLong(buf, 0, Long.BYTES);
	}

	private static void skipHeader(InputStream in) throws IOException {
		long version = getUInt(in);
		if (version != FORMAT_VERSION) {
			throw new StreamCorruptedException(
					MessageFormat.format(SshdText.get().signKrlInvalidVersion,
							Long.valueOf(version)));
		}
		// krl_version, generated_date, flags (none defined in version 1)
		in.skip(24);
		in.skip(getUInt(in)); // reserved
		in.skip(getUInt(in)); // comment
	}

	private static OpenSshBinaryKrl load(InputStream in) throws IOException {
		OpenSshBinaryKrl krl = new OpenSshBinaryKrl();
		for (;;) {
			int sectionType = in.read();
			if (sectionType < 0) {
				break; // EOF
			}
			switch (sectionType) {
			case SECTION_CERTIFICATES:
				readCertificates(krl.certificates, in, getUInt(in));
				break;
			case SECTION_KEY:
				readBlobs("explicit_keys", krl.blobs, in, getUInt(in), 0); //$NON-NLS-1$
				break;
			case SECTION_SHA1:
				readBlobs("fingerprint_sha1", krl.sha1, in, getUInt(in), 20); //$NON-NLS-1$
				break;
			case SECTION_SIGNATURE:
				// Unsupported as of OpenSSH 9.4. It even refuses to load such
				// KRLs. Just skip it.
				in.skip(getUInt(in));
				break;
			case SECTION_SHA256:
				readBlobs("fingerprint_sha256", krl.sha256, in, getUInt(in), //$NON-NLS-1$
						32);
				break;
			case SECTION_EXTENSION:
				// No extensions are defined for version 1 KRLs.
				in.skip(getUInt(in));
				break;
			default:
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlUnknownSection,
						Integer.valueOf(sectionType)));
			}
		}
		return krl;
	}

	private static void readBlobs(String sectionName, Set<Blob> blobs,
			InputStream in, long sectionLength, long expectedBlobLength)
			throws IOException {
		while (sectionLength >= Integer.BYTES) {
			// Read blobs.
			long blobLength = getUInt(in);
			sectionLength -= Integer.BYTES;
			if (blobLength > sectionLength) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlBlobLengthInvalid, sectionName,
						Long.valueOf(blobLength)));
			}
			if (expectedBlobLength != 0 && blobLength != expectedBlobLength) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlBlobLengthInvalidExpected,
						sectionName, Long.valueOf(blobLength),
						Long.valueOf(expectedBlobLength)));
			}
			byte[] blob = new byte[(int) blobLength];
			IO.readFully(in, blob);
			sectionLength -= blobLength;
			blobs.add(new Blob(blob));
		}
		if (sectionLength != 0) {
			throw new StreamCorruptedException(
					MessageFormat.format(SshdText.get().signKrlBlobLeftover,
							sectionName, Long.valueOf(sectionLength)));
		}
	}

	private static void readCertificates(Map<Blob, CertificateRevocation> certs,
			InputStream in, long sectionLength) throws IOException {
		long keyLength = getUInt(in);
		sectionLength -= Integer.BYTES;
		if (keyLength > sectionLength) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signKrlCaKeyLengthInvalid,
					Long.valueOf(keyLength)));
		}
		Blob key = null;
		if (keyLength > 0) {
			byte[] blob = new byte[(int) keyLength];
			IO.readFully(in, blob);
			key = new Blob(blob);
			sectionLength -= keyLength;
		}
		CertificateRevocation rev = certs.computeIfAbsent(key,
				k -> new CertificateRevocation());
		long reservedLength = getUInt(in);
		sectionLength -= Integer.BYTES;
		if (reservedLength > sectionLength) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signKrlCaKeyLengthInvalid,
					Long.valueOf(reservedLength)));
		}
		in.skip(reservedLength);
		sectionLength -= reservedLength;
		if (sectionLength == 0) {
			throw new StreamCorruptedException(
					SshdText.get().signKrlNoCertificateSubsection);
		}
		while (sectionLength > 0) {
			int subSection = in.read();
			if (subSection < 0) {
				throw new EOFException();
			}
			sectionLength--;
			if (sectionLength < Integer.BYTES) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlCertificateLeftover,
						Long.valueOf(sectionLength)));
			}
			long subLength = getUInt(in);
			sectionLength -= Integer.BYTES;
			if (subLength > sectionLength) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlCertificateSubsectionLength,
						Long.valueOf(subLength)));
			}
			if (subLength > 0) {
				switch (subSection) {
				case CERT_SERIAL_LIST:
					readSerials(rev.ranges, in, subLength, false);
					break;
				case CERT_SERIAL_RANGES:
					readSerials(rev.ranges, in, subLength, true);
					break;
				case CERT_SERIAL_BITS:
					readSerialBitSet(rev.ranges, in, subLength);
					break;
				case CERT_KEY_IDS:
					readIds(rev.keyIds, in, subLength);
					break;
				case CERT_EXTENSIONS:
					in.skip(subLength);
					break;
				default:
					throw new StreamCorruptedException(MessageFormat.format(
							SshdText.get().signKrlUnknownSubsection,
							Long.valueOf(subSection)));
				}
			}
			sectionLength -= subLength;
		}
	}

	private static void readSerials(SerialRangeSet set, InputStream in,
			long length, boolean ranges) throws IOException {
		while (length >= Long.BYTES) {
			long a = getLong(in);
			length -= Long.BYTES;
			if (a == 0) {
				throw new StreamCorruptedException(
						SshdText.get().signKrlSerialZero);
			}
			if (!ranges) {
				set.add(a);
				continue;
			}
			if (length < Long.BYTES) {
				throw new StreamCorruptedException(
						MessageFormat.format(SshdText.get().signKrlShortRange,
								Long.valueOf(length)));
			}
			long b = getLong(in);
			length -= Long.BYTES;
			if (Long.compareUnsigned(a, b) > 0) {
				throw new StreamCorruptedException(
						SshdText.get().signKrlEmptyRange);
			}
			set.add(a, b);
		}
		if (length != 0) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signKrlCertificateSubsectionLeftover,
					Long.valueOf(length)));
		}
	}

	private static void readSerialBitSet(SerialRangeSet set, InputStream in,
			long subLength) throws IOException {
		while (subLength > 0) {
			if (subLength < Long.BYTES) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlCertificateSubsectionLeftover,
						Long.valueOf(subLength)));
			}
			long base = getLong(in);
			subLength -= Long.BYTES;
			if (subLength < Integer.BYTES) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlCertificateSubsectionLeftover,
						Long.valueOf(subLength)));
			}
			long setLength = getUInt(in);
			subLength -= Integer.BYTES;
			if (setLength == 0 || setLength > subLength) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlInvalidBitSetLength,
						Long.valueOf(setLength)));
			}
			// Now process the bits. Note that the mpint is stored MSB first.
			//
			// We set individual serial numbers (one for each set bit) and let
			// the SerialRangeSet take care of coalescing for successive runs
			// of set bits.
			int n = (int) setLength;
			for (int i = n - 1; i >= 0; i--) {
				int b = in.read();
				if (b < 0) {
					throw new EOFException();
				} else if (b == 0) {
					// Stored as an mpint: may have leading zero bytes (actually
					// at most one; if the high bit of the first byte is set).
					continue;
				}
				for (int bit = 0,
						mask = 1; bit < Byte.SIZE; bit++, mask <<= 1) {
					if ((b & mask) != 0) {
						set.add(base + (i * Byte.SIZE) + bit);
					}
				}
			}
			subLength -= setLength;
		}
	}

	private static void readIds(Set<String> ids, InputStream in, long subLength)
			throws IOException {
		while (subLength >= Integer.BYTES) {
			long length = getUInt(in);
			subLength -= Integer.BYTES;
			if (length > subLength) {
				throw new StreamCorruptedException(MessageFormat.format(
						SshdText.get().signKrlInvalidKeyIdLength,
						Long.valueOf(length)));
			}
			byte[] bytes = new byte[(int) length];
			IO.readFully(in, bytes);
			ids.add(new String(bytes, StandardCharsets.UTF_8));
			subLength -= length;
		}
		if (subLength != 0) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signKrlCertificateSubsectionLeftover,
					Long.valueOf(subLength)));
		}
	}
}
