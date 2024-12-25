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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.io.ModifiableFileWatcher;
import org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.signing.ssh.VerificationException;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * Encapsulates the allowed signers handling.
 */
final class AllowedSigners extends ModifiableFileWatcher {

	private static final String CERT_AUTHORITY = "cert-authority"; //$NON-NLS-1$

	private static final String NAMESPACES = "namespaces="; //$NON-NLS-1$

	private static final String VALID_AFTER = "valid-after="; //$NON-NLS-1$

	private static final String VALID_BEFORE = "valid-before="; //$NON-NLS-1$

	private static final DateTimeFormatter SSH_DATE_FORMAT = new DateTimeFormatterBuilder()
			.appendValue(ChronoField.YEAR, 4)
			.appendValue(ChronoField.MONTH_OF_YEAR, 2)
			.appendValue(ChronoField.DAY_OF_MONTH, 2)
			.optionalStart()
			.appendValue(ChronoField.HOUR_OF_DAY, 2)
			.appendValue(ChronoField.MINUTE_OF_HOUR, 2)
			.optionalStart()
			.appendValue(ChronoField.SECOND_OF_MINUTE, 2)
			.toFormatter(Locale.ROOT);

	private static final Predicate<AllowedEntry> CERTIFICATES = AllowedEntry::isCA;

	private static final Predicate<AllowedEntry> PLAIN_KEYS = Predicate
			.not(CERTIFICATES);

	@SuppressWarnings("ArrayRecordComponent")
	static record AllowedEntry(String[] identities, boolean isCA,
			String[] namespaces, Instant validAfter, Instant validBefore,
			String key) {
		// Empty

		@Override
		public final boolean equals(Object any) {
			if (this == any) {
				return true;
			}
			if (any == null || !(any instanceof AllowedEntry)) {
				return false;
			}
			AllowedEntry other = (AllowedEntry) any;
			return isCA == other.isCA
					&& Arrays.equals(identities, other.identities)
					&& Arrays.equals(namespaces, other.namespaces)
					&& Objects.equals(validAfter, other.validAfter)
					&& Objects.equals(validBefore, other.validBefore)
					&& Objects.equals(key, other.key);
		}

		@Override
		public final int hashCode() {
			int hash = Boolean.hashCode(isCA);
			hash = hash * 31 + Arrays.hashCode(identities);
			hash = hash * 31 + Arrays.hashCode(namespaces);
			return hash * 31 + Objects.hash(validAfter, validBefore, key);
		}
	}

	private static record State(Map<String, List<AllowedEntry>> entries) {
		// Empty
	}

	private State state;

	public AllowedSigners(Path path) {
		super(path);
		state = new State(new HashMap<>());
	}

	public String isAllowed(PublicKey key, String namespace, String name,
			Instant time) throws IOException, VerificationException {
		State currentState = refresh();
		PublicKey keyToCheck = key;
		if (key instanceof OpenSshCertificate certificate) {
			AllowedEntry entry = find(currentState, certificate.getCaPubKey(),
					namespace, name, time, CERTIFICATES);
			if (entry != null) {
				Collection<String> principals = certificate.getPrincipals();
				if (principals.isEmpty()) {
					// According to the OpenSSH documentation, a certificate
					// without principals is valid for anyone.
					//
					// See https://man.openbsd.org/ssh-keygen.1#CERTIFICATES .
					//
					// However, the same documentation also says that a name
					// must match both the entry's patterns and be listed in the
					// certificate's principals.
					//
					// See https://man.openbsd.org/ssh-keygen.1#ALLOWED_SIGNERS
					//
					// git/OpenSSH considers signatures made by such
					// certificates untrustworthy.
					String identities;
					if (!StringUtils.isEmptyOrNull(name)) {
						// The name must have matched entry.identities.
						identities = name;
					} else {
						identities = Arrays.stream(entry.identities())
								.collect(Collectors.joining(",")); //$NON-NLS-1$
					}
					throw new VerificationException(false, MessageFormat.format(
							SshdText.get().signCertificateWithoutPrincipals,
							KeyUtils.getFingerPrint(certificate.getCaPubKey()),
							identities));
				}
				if (!StringUtils.isEmptyOrNull(name)) {
					if (!principals.contains(name)) {
						throw new VerificationException(false,
								MessageFormat.format(SshdText
										.get().signCertificateNotForName,
										KeyUtils.getFingerPrint(
												certificate.getCaPubKey()),
										name));
					}
					return name;
				}
				// Filter the principals listed in the certificate by
				// the patterns defined in the file.
				Set<String> filtered = new LinkedHashSet<>();
				List<String> patterns = Arrays.asList(entry.identities());
				for (String principal : principals) {
					if (OpenSshConfigFile.patternMatch(patterns, principal)) {
						filtered.add(principal);
					}
				}
				return filtered.stream().collect(Collectors.joining(",")); //$NON-NLS-1$
			}
			// Certificate not found. git/OpenSSH considers this untrustworthy,
			// even if the certified key itself might be listed.
			return null;
			// Alternative: go check for the certified key itself:
			// keyToCheck = certificate.getCertPubKey();
		}
		AllowedEntry entry = find(currentState, keyToCheck, namespace, name,
				time, PLAIN_KEYS);
		if (entry != null) {
			if (!StringUtils.isEmptyOrNull(name)) {
				// The name must have matched entry.identities.
				return name;
			}
			// No name given, but we consider the key valid: report the
			// identities.
			return Arrays.stream(entry.identities())
					.collect(Collectors.joining(",")); //$NON-NLS-1$
		}
		return null;
	}

	private AllowedEntry find(State current, PublicKey key,
			String namespace, String name, Instant time,
			Predicate<AllowedEntry> filter)
			throws VerificationException {
		String k = PublicKeyEntry.toString(key);
		VerificationException v = null;
		List<AllowedEntry> candidates = current.entries().get(k);
		if (candidates == null) {
			return null;
		}
		for (AllowedEntry entry : candidates) {
			if (!filter.test(entry)) {
				continue;
			}
			if (name != null && !OpenSshConfigFile
					.patternMatch(Arrays.asList(entry.identities()), name)) {
				continue;
			}
			if (entry.namespaces() != null) {
				if (!OpenSshConfigFile.patternMatch(
						Arrays.asList(entry.namespaces()),
						namespace)) {
					if (v == null) {
						v = new VerificationException(false,
								MessageFormat.format(
										SshdText.get().signWrongNamespace,
										KeyUtils.getFingerPrint(key),
										namespace));
					}
					continue;
				}
			}
			if (time != null) {
				if (entry.validAfter() != null
						&& time.isBefore(entry.validAfter())) {
					if (v == null) {
						v = new VerificationException(true,
								MessageFormat.format(
										SshdText.get().signKeyTooEarly,
										KeyUtils.getFingerPrint(key)));
					}
					continue;
				} else if (entry.validBefore() != null
						&& time.isAfter(entry.validBefore())) {
					if (v == null) {
						v = new VerificationException(true,
								MessageFormat.format(
										SshdText.get().signKeyTooEarly,
										KeyUtils.getFingerPrint(key)));
					}
					continue;
				}
			}
			return entry;
		}
		if (v != null) {
			throw v;
		}
		return null;
	}

	private synchronized State refresh() throws IOException {
		if (checkReloadRequired()) {
			updateReloadAttributes();
			try {
				state = reload(getPath());
			} catch (NoSuchFileException e) {
				// File disappeared
				resetReloadAttributes();
				state = new State(new HashMap<>());
			}
		}
		return state;
	}

	private static State reload(Path path) throws IOException {
		Map<String, List<AllowedEntry>> entries = new HashMap<>();
		try (BufferedReader r = Files.newBufferedReader(path,
				StandardCharsets.UTF_8)) {
			String line;
			for (int lineNumber = 1;; lineNumber++) {
				line = r.readLine();
				if (line == null) {
					break;
				}
				line = line.strip();
				try {
					AllowedEntry entry = parseLine(line);
					if (entry != null) {
						entries.computeIfAbsent(entry.key(),
								k -> new ArrayList<>()).add(entry);
					}
				} catch (IOException | RuntimeException e) {
					throw new IOException(MessageFormat.format(
							SshdText.get().signAllowedSignersFormatError, path,
							Integer.toString(lineNumber), line), e);
				}
			}
		}
		return new State(entries);
	}

	private static boolean matches(String src, String other, int offset) {
		return src.regionMatches(true, offset, other, 0, other.length());
	}

	// Things below have package visibility for testing.

	static AllowedEntry parseLine(String line)
			throws IOException {
		if (StringUtils.isEmptyOrNull(line) || line.charAt(0) == '#') {
			return null;
		}
		int length = line.length();
		if ((matches(line, CERT_AUTHORITY, 0)
				&& CERT_AUTHORITY.length() < length
				&& Character.isWhitespace(line.charAt(CERT_AUTHORITY.length())))
				|| matches(line, NAMESPACES, 0)
				|| matches(line, VALID_AFTER, 0)
				|| matches(line, VALID_BEFORE, 0)) {
			throw new StreamCorruptedException(
					SshdText.get().signAllowedSignersNoIdentities);
		}
		int i = 0;
		while (i < length && !Character.isWhitespace(line.charAt(i))) {
			i++;
		}
		if (i >= length) {
			throw new StreamCorruptedException(SshdText.get().signAllowedSignersLineFormat);
		}
		String[] identities = line.substring(0, i).split(","); //$NON-NLS-1$
		if (Arrays.stream(identities).anyMatch(String::isEmpty)) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signAllowedSignersEmptyIdentity,
					line.substring(0, i)));
		}
		// Parse the options
		i++;
		boolean isCA = false;
		List<String> namespaces = null;
		Instant validAfter = null;
		Instant validBefore = null;
		while (i < length) {
			// Skip whitespace
			if (Character.isSpaceChar(line.charAt(i))) {
				i++;
				continue;
			}
			if (matches(line, CERT_AUTHORITY, i)) {
				i += CERT_AUTHORITY.length();
				isCA = true;
				if (!Character.isWhitespace(line.charAt(i))) {
					throw new StreamCorruptedException(SshdText.get().signAllowedSignersCertAuthorityError);
				}
				i++;
			} else if (matches(line, NAMESPACES, i)) {
				if (namespaces != null) {
					throw new StreamCorruptedException(MessageFormat.format(
							SshdText.get().signAllowedSignersMultiple,
							NAMESPACES));
				}
				i += NAMESPACES.length();
				Dequoted parsed = dequote(line, i);
				i = parsed.after();
				String ns = parsed.value();
				String[] items = ns.split(","); //$NON-NLS-1$
				namespaces = new ArrayList<>(items.length);
				for (int j = 0; j < items.length; j++) {
					String n = items[j].strip();
					if (!n.isEmpty()) {
						namespaces.add(n);
					}
				}
				if (namespaces.isEmpty()) {
					throw new StreamCorruptedException(
							SshdText.get().signAllowedSignersEmptyNamespaces);
				}
			} else if (matches(line, VALID_AFTER, i)) {
				if (validAfter != null) {
					throw new StreamCorruptedException(MessageFormat.format(
							SshdText.get().signAllowedSignersMultiple,
							VALID_AFTER));
				}
				i += VALID_AFTER.length();
				Dequoted parsed = dequote(line, i);
				i = parsed.after();
				validAfter = parseDate(parsed.value());
			} else if (matches(line, VALID_BEFORE, i)) {
				if (validBefore != null) {
					throw new StreamCorruptedException(MessageFormat.format(
							SshdText.get().signAllowedSignersMultiple,
							VALID_BEFORE));
				}
				i += VALID_BEFORE.length();
				Dequoted parsed = dequote(line, i);
				i = parsed.after();
				validBefore = parseDate(parsed.value());
			} else {
				break;
			}
		}
		// Now we should be at the key
		String key = parsePublicKey(line, i);
		return new AllowedEntry(identities, isCA,
				namespaces == null ? null : namespaces.toArray(new String[0]),
				validAfter, validBefore, key);
	}

	static String parsePublicKey(String s, int from)
			throws StreamCorruptedException {
		int i = from;
		int length = s.length();
		while (i < length && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		if (i >= length) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signAllowedSignersPublicKeyParsing,
					s.substring(from)));
		}
		int start = i;
		while (i < length && !Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		if (i >= length) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signAllowedSignersPublicKeyParsing,
					s.substring(start)));
		}
		int endOfKeyType = i;
		i = endOfKeyType + 1;
		while (i < length && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		int startOfKey = i;
		while (i < length && !Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		if (i == startOfKey) {
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signAllowedSignersPublicKeyParsing,
					s.substring(start)));
		}
		String keyType = s.substring(start, endOfKeyType);
		String key = s.substring(startOfKey, i);
		if (!key.startsWith("AAAA")) { //$NON-NLS-1$
			// base64 encoded SSH keys always start with four 'A's.
			throw new StreamCorruptedException(MessageFormat.format(
					SshdText.get().signAllowedSignersPublicKeyParsing,
					s.substring(start)));
		}
		return keyType + ' ' + s.substring(startOfKey, i);
	}

	static Instant parseDate(String input) {
		// Allowed formats are YYYYMMDD[Z] or YYYYMMDDHHMM[SS][Z]. If 'Z', it's
		// UTC, otherwise local time.
		String timeSpec = input;
		int length = input.length();
		if (length < 8) {
			throw new IllegalArgumentException(MessageFormat.format(
					SshdText.get().signAllowedSignersInvalidDate, input));
		}
		boolean isUTC = false;
		if (timeSpec.charAt(length - 1) == 'Z') {
			isUTC = true;
			timeSpec = timeSpec.substring(0, length - 1);
		}
		LocalDateTime time;
		TemporalAccessor temporalAccessor = SSH_DATE_FORMAT.parseBest(timeSpec,
				LocalDateTime::from, LocalDate::from);
		if (temporalAccessor instanceof LocalDateTime) {
			time = (LocalDateTime) temporalAccessor;
		} else {
			time = ((LocalDate) temporalAccessor).atStartOfDay();
		}
		if (isUTC) {
			return time.atOffset(ZoneOffset.UTC).toInstant();
		}
		ZoneId tz = SystemReader.getInstance().getTimeZoneId();
		return time.atZone(tz).toInstant();
	}

	// OpenSSH uses the backslash *only* to quote the double-quote.
	static Dequoted dequote(String line, int from) {
		int length = line.length();
		int i = from;
		if (line.charAt(i) == '"') {
			boolean quoted = false;
			i++;
			StringBuilder b = new StringBuilder();
			while (i < length) {
				char ch = line.charAt(i);
				if (ch == '"') {
					if (quoted) {
						b.append(ch);
						quoted = false;
					} else {
						break;
					}
				} else if (ch == '\\') {
					quoted = true;
				} else {
					if (quoted) {
						b.append('\\');
					}
					b.append(ch);
					quoted = false;
				}
				i++;
			}
			if (i >= length) {
				throw new IllegalArgumentException(
						SshdText.get().signAllowedSignersUnterminatedQuote);
			}
			return new Dequoted(b.toString(), i + 1);
		}
		while (i < length && !Character.isWhitespace(line.charAt(i))) {
			i++;
		}
		return new Dequoted(line.substring(from, i), i);
	}

	static record Dequoted(String value, int after) {
		// Empty
	}
}
