/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

/**
 * Java port of native git's {@code struct credential} together with its wire
 * codec {@code credential_read} / {@code credential_write} /
 * {@code credential_write_item} (git {@code credential.h:131-194},
 * {@code credential.c:314-442}).
 * <p>
 * Field names and their order follow native git's credential protocol
 * (credential.c). Only the fields for a single get/store/erase exchange are
 * handled; the multi-stage fields ({@code state[]}, {@code continue},
 * {@code multistage}) and {@code oauth_refresh_token} are not. Passwords and
 * tokens are kept in {@code char[]} and cleared by {@link #clear()}.
 * <p>
 * Bundle-internal, not public API.
 */
final class Credential {

	/**
	 * git limits helper protocol lines to 64k (git {@code git-credential.txt}).
	 */
	static final int MAX_LINE = 65535;

	/** Sentinel for "no expiry", mirroring git's {@code TIME_MAX}. */
	static final long NO_EXPIRY = Long.MAX_VALUE;

	String protocol;

	String host;

	String path;

	String username;

	char[] password;

	String authtype;

	char[] credential;

	/** git {@code password_expiry_utc}; {@link #NO_EXPIRY} if unset. */
	long passwordExpiryUtc = NO_EXPIRY;

	/** Whether we advertise {@code capability[]=authtype} to the helper. */
	boolean capaAuthtype;

	/** Zeroes and drops all secret material. */
	void clear() {
		if (password != null) {
			Arrays.fill(password, (char) 0);
			password = null;
		}
		if (credential != null) {
			Arrays.fill(credential, (char) 0);
			credential = null;
		}
		username = null;
		authtype = null;
		passwordExpiryUtc = NO_EXPIRY;
	}

	/**
	 * Serialize for a helper, porting {@code credential_write}
	 * (credential.c:409). {@code protocol} and {@code host} are required; other
	 * {@code null} fields are omitted; the record ends with a blank line.
	 *
	 * @param protectProtocol
	 *            reject carriage returns as well as newlines (git's
	 *            {@code credential.protectProtocol})
	 * @return the UTF-8 request bytes for the helper's stdin
	 */
	byte[] write(boolean protectProtocol) {
		WipingByteArrayOutputStream out = new WipingByteArrayOutputStream();
		try {
			if (capaAuthtype) {
				writeItem(out, "capability[]", "authtype", protectProtocol); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (authtype != null) {
				writeItem(out, "authtype", authtype, protectProtocol); //$NON-NLS-1$
			}
			if (credential != null) {
				writeItem(out, "credential", credential, protectProtocol); //$NON-NLS-1$
			}
			writeItem(out, "protocol", require(protocol, "protocol"), //$NON-NLS-1$ //$NON-NLS-2$
					protectProtocol);
			writeItem(out, "host", require(host, "host"), protectProtocol); //$NON-NLS-1$ //$NON-NLS-2$
			writeItem(out, "path", path, protectProtocol); //$NON-NLS-1$
			writeItem(out, "username", username, protectProtocol); //$NON-NLS-1$
			writeItem(out, "password", password, protectProtocol); //$NON-NLS-1$
			out.write('\n');
			return out.toByteArray();
		} finally {
			out.wipe();
		}
	}

	/**
	 * Parse a helper's response into this credential, porting
	 * {@code credential_read} (credential.c:314): read until a blank line or
	 * EOF, enforcing {@link #MAX_LINE}.
	 *
	 * @param in
	 *            the helper's stdout
	 * @throws IOException
	 *             on a malformed or over-long line
	 */
	void read(InputStream in) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int ch;
		while ((ch = in.read()) != -1) {
			if (ch == '\n') {
				if (line.size() == 0) {
					return; // blank line terminates
				}
				parseLine(line.toByteArray());
				line.reset();
			} else {
				if (line.size() >= MAX_LINE) {
					throw new IOException(
							"credential helper line exceeds 65535 bytes"); //$NON-NLS-1$
				}
				line.write(ch);
			}
		}
		if (line.size() > 0) {
			parseLine(line.toByteArray());
		}
	}

	private void parseLine(byte[] bytes) {
		int eq = -1;
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] == '=') {
				eq = i;
				break;
			}
		}
		if (eq < 0) {
			return; // git warns and ignores malformed lines
		}
		String key = new String(bytes, 0, eq, UTF_8);
		int vOff = eq + 1;
		int vLen = bytes.length - vOff;
		switch (key) {
		case "username": //$NON-NLS-1$
			username = str(bytes, vOff, vLen);
			break;
		case "password": //$NON-NLS-1$
			password = chars(bytes, vOff, vLen);
			break;
		case "credential": //$NON-NLS-1$
			credential = chars(bytes, vOff, vLen);
			break;
		case "authtype": //$NON-NLS-1$
			authtype = str(bytes, vOff, vLen);
			break;
		case "protocol": //$NON-NLS-1$
			protocol = str(bytes, vOff, vLen);
			break;
		case "host": //$NON-NLS-1$
			host = str(bytes, vOff, vLen);
			break;
		case "path": //$NON-NLS-1$
			path = str(bytes, vOff, vLen);
			break;
		case "password_expiry_utc": //$NON-NLS-1$
			try {
				long expiry = Long.parseLong(str(bytes, vOff, vLen));
				// git maps 0 (and unparseable) to "no expiry".
				passwordExpiryUtc = expiry == 0 ? NO_EXPIRY : expiry;
			} catch (NumberFormatException e) {
				// leave as NO_EXPIRY
			}
			break;
		case "capability[]": //$NON-NLS-1$
			if ("authtype".equals(str(bytes, vOff, vLen))) { //$NON-NLS-1$
				capaAuthtype = true;
			}
			break;
		default:
			// url, oauth_refresh_token, ephemeral, state[], continue, quit are
			// recognized by git but not acted on.
			break;
		}
	}

	private static void writeItem(ByteArrayOutputStream out, String key,
			String value, boolean protectProtocol) {
		if (value == null) {
			return;
		}
		checkNoControl(key, value.toCharArray(), protectProtocol);
		writeRaw(out, key);
		out.write('=');
		writeRaw(out, value);
		out.write('\n');
	}

	private static void writeItem(ByteArrayOutputStream out, String key,
			char[] value, boolean protectProtocol) {
		if (value == null) {
			return;
		}
		checkNoControl(key, value, protectProtocol);
		writeRaw(out, key);
		out.write('=');
		byte[] encoded = encode(value);
		try {
			out.write(encoded, 0, encoded.length);
		} finally {
			Arrays.fill(encoded, (byte) 0);
		}
		out.write('\n');
	}

	private static void checkNoControl(String key, char[] value,
			boolean protectProtocol) {
		for (char ch : value) {
			if (ch == '\n' || ch == '\0') {
				throw new IllegalArgumentException("credential value for " + key //$NON-NLS-1$
						+ " contains a control character"); //$NON-NLS-1$
			}
			if (protectProtocol && ch == '\r') {
				throw new IllegalArgumentException("credential value for " + key //$NON-NLS-1$
						+ " contains carriage return"); //$NON-NLS-1$
			}
		}
	}

	private static void writeRaw(ByteArrayOutputStream out, String s) {
		byte[] b = s.getBytes(UTF_8);
		out.write(b, 0, b.length);
	}

	private static String require(String value, String key) {
		if (value == null) {
			throw new IllegalArgumentException(
					"credential value for " + key + " is required"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value;
	}

	private static String str(byte[] bytes, int off, int len) {
		return new String(bytes, off, len, UTF_8);
	}

	/** {@link ByteArrayOutputStream} whose backing buffer can be zeroed. */
	private static final class WipingByteArrayOutputStream
			extends ByteArrayOutputStream {
		void wipe() {
			Arrays.fill(buf, (byte) 0);
		}
	}

	private static char[] chars(byte[] bytes, int off, int len) {
		CharBuffer cb = UTF_8.decode(ByteBuffer.wrap(bytes, off, len));
		char[] out = Arrays.copyOf(cb.array(), cb.limit());
		if (cb.array() != out) {
			Arrays.fill(cb.array(), (char) 0);
		}
		return out;
	}

	private static byte[] encode(char[] value) {
		ByteBuffer bb = UTF_8.encode(CharBuffer.wrap(value));
		byte[] out = Arrays.copyOf(bb.array(), bb.limit());
		if (bb.array() != out) {
			Arrays.fill(bb.array(), (byte) 0);
		}
		return out;
	}
}
