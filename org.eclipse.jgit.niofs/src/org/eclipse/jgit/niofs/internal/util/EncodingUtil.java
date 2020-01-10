/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.util;

import java.io.UnsupportedEncodingException;
import java.util.BitSet;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.URLCodec;

/**
 * The URIUtil class that was available in commons-httpclient 3.x was retired
 * when httpclient moved to the 4.x branch.
 * <p>
 * See http://marc.info/?l=httpclient-users&m=125425095705062&w=2 for more
 * informatoin.
 */
public class EncodingUtil {

	/**
	 * Those characters that are allowed for the abs_path.
	 */
	public static final BitSet allowed_abs_path = new BitSet(256);

	// bitsets
	// --------------------------------------------------------------------------------------------------------------------
	/**
	 * The percent "%" character always has the reserved purpose of being the escape
	 * indicator, it must be escaped as "%25" in order to be used as data within a
	 * URI.
	 */
	protected static final BitSet percent = new BitSet(256);
	/**
	 * BitSet for digit.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * digit = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet digit = new BitSet(256);
	/**
	 * BitSet for alpha.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * alpha = lowalpha | upalpha
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet alpha = new BitSet(256);
	/**
	 * BitSet for alphanum (join of alpha &amp; digit).
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * alphanum = alpha | digit
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet alphanum = new BitSet(256);
	/**
	 * BitSet for hex.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * hex = digit | "A" | "B" | "C" | "D" | "E" | "F" | "a" | "b" | "c" | "d" | "e" | "f"
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet hex = new BitSet(256);
	/**
	 * BitSet for escaped.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * escaped       = "%" hex hex
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet escaped = new BitSet(256);
	/**
	 * BitSet for mark.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * mark = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet mark = new BitSet(256);
	/**
	 * Data characters that are allowed in a URI but do not have a reserved purpose
	 * are called unreserved.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * unreserved = alphanum | mark
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet unreserved = new BitSet(256);
	/**
	 * BitSet for pchar.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * pchar = unreserved | escaped | ":" | "@" | "&amp;" | "=" | "+" | "$" | ","
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet pchar = new BitSet(256);
	/**
	 * BitSet for param (alias for pchar).
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * param         = *pchar
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet param = pchar;
	/**
	 * BitSet for segment.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * segment       = *pchar *( ";" param )
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet segment = new BitSet(256);
	/**
	 * BitSet for path segments.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * path_segments = segment *( "/" segment )
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet path_segments = new BitSet(256);
	/**
	 * URI absolute path.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * abs_path      = "/"  path_segments
	 * </pre>
	 * 
	 * </blockquote>
	 * <p>
	 */
	protected static final BitSet abs_path = new BitSet(256);

	// Static initializer for percent
	static {
		percent.set('%');
	}

	// Static initializer for digit
	static {
		for (int i = '0'; i <= '9'; i++) {
			digit.set(i);
		}
	}

	// Static initializer for alpha
	static {
		for (int i = 'a'; i <= 'z'; i++) {
			alpha.set(i);
		}
		for (int i = 'A'; i <= 'Z'; i++) {
			alpha.set(i);
		}
	}

	// Static initializer for alphanum
	static {
		alphanum.or(alpha);
		alphanum.or(digit);
	}

	// Static initializer for hex
	static {
		hex.or(digit);
		for (int i = 'a'; i <= 'f'; i++) {
			hex.set(i);
		}
		for (int i = 'A'; i <= 'F'; i++) {
			hex.set(i);
		}
	}

	// Static initializer for escaped
	static {
		escaped.or(percent);
		escaped.or(hex);
	}

	// Static initializer for mark
	static {
		mark.set('-');
		mark.set('_');
		mark.set('.');
		mark.set('!');
		mark.set('~');
		mark.set('*');
		mark.set('\'');
		mark.set('(');
		mark.set(')');
	}

	// Static initializer for unreserved
	static {
		unreserved.or(alphanum);
		unreserved.or(mark);
	}

	// Static initializer for pchar
	static {
		pchar.or(unreserved);
		pchar.or(escaped);
		pchar.set(':');
		pchar.set('@');
		pchar.set('&');
		pchar.set('=');
		pchar.set('+');
		pchar.set('$');
		pchar.set(',');
	}

	// Static initializer for segment
	static {
		segment.or(pchar);
		segment.set(';');
		segment.or(param);
	}

	// Static initializer for path_segments
	static {
		path_segments.set('/');
		path_segments.or(segment);
	}

	// Static initializer for abs_path
	static {
		abs_path.set('/');
		abs_path.or(path_segments);
	}

	// Static initializer for allowed_abs_path
	static {
		allowed_abs_path.or(abs_path);
		// allowed_abs_path.set('/'); // aleady included
		allowed_abs_path.andNot(percent);
		allowed_abs_path.clear('+');
	}

	private EncodingUtil() {
		// utility class, does not need a constructor
	}

	// methods
	// --------------------------------------------------------------------------------------------------------------------

	/**
	 * Escape and encode a string regarded as the path component of an URI with the
	 * default protocol charset.
	 * 
	 * @param unescaped an unescaped string
	 * @return the escaped string
	 */
	public static String encodePath(String unescaped) {
		byte[] rawdata = URLCodec.encodeUrl(allowed_abs_path, getBytes(unescaped, "UTF-8"));
		return getAsciiString(rawdata);
	}

	/**
	 * Converts the specified string to a byte array. If the charset is not
	 * supported the default system charset is used.
	 * 
	 * @param data    the string to be encoded
	 * @param charset the desired character encoding
	 * @return The resulting byte array.
	 */
	public static byte[] getBytes(final String data, String charset) {
		if (data == null) {
			throw new IllegalArgumentException("data may not be null");
		}

		if (charset == null || charset.length() == 0) {
			throw new IllegalArgumentException("charset may not be null or empty");
		}

		try {
			return data.getBytes(charset);
		} catch (UnsupportedEncodingException e) {
			return data.getBytes();
		}
	}

	/**
	 * Converts the byte array of ASCII characters to a string. This method is to be
	 * used when decoding content of HTTP elements (such as response headers)
	 * 
	 * @param data the byte array to be encoded
	 * @return The string representation of the byte array
	 */
	public static String getAsciiString(final byte[] data) {
		if (data == null) {
			throw new IllegalArgumentException("Parameter may not be null");
		}

		try {
			return new String(data, 0, data.length, "US-ASCII");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(EncodingUtil.class.getSimpleName() + " requires ASCII support");
		}
	}

	/**
	 * Converts the specified string to byte array of ASCII characters.
	 * 
	 * @param data the string to be encoded
	 * @return The string as a byte array.
	 */
	public static byte[] getAsciiBytes(final String data) {
		if (data == null) {
			throw new IllegalArgumentException("Parameter may not be null");
		}

		try {
			return data.getBytes("US-ASCII");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(EncodingUtil.class.getSimpleName() + " requires ASCII support");
		}
	}

	/**
	 * Converts the byte array of HTTP content characters to a string. If the
	 * specified charset is not supported, default system encoding is used.
	 * 
	 * @param data    the byte array to be encoded
	 * @param charset the desired character encoding
	 * @return The result of the conversion.
	 */
	public static String getString(final byte[] data, String charset) {
		if (data == null) {
			throw new IllegalArgumentException("Parameter may not be null");
		}

		if (charset == null || charset.length() == 0) {
			throw new IllegalArgumentException("charset may not be null or empty");
		}

		try {
			return new String(data, 0, data.length, charset);
		} catch (UnsupportedEncodingException e) {
			return new String(data, 0, data.length);
		}
	}

	/**
	 * Unescape and decode a given string regarded as an escaped string with the
	 * UTF-8 protocol charset.
	 * 
	 * @param escaped a string
	 * @return the unescaped string
	 * @throws IllegalStateException if the escaped string is not a correct URL
	 */
	public static String decode(String escaped) {
		byte[] asciiData = getAsciiBytes(escaped);
		byte[] rawdata;
		try {
			rawdata = URLCodec.decodeUrl(asciiData);
		} catch (DecoderException e) {
			throw new IllegalStateException(e.getMessage());
		}
		return getString(rawdata, "UTF-8");
	}
}
