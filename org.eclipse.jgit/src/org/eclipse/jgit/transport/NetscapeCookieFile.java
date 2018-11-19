/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de>
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
package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.Writer;
import java.net.HttpCookie;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * Supports reading from and writing to files in the <strong>Netscape Cookie
 * File Format</strong> being referenced via the git config <a href=
 * "https://git-scm.com/docs/git-config#git-config-httpcookieFile">http.cookieFile</a>.
 *
 * @see <a href="http://www.cookiecentral.com/faq/#3.5">Netscape Cookie File
 *      Format</a>
 * @see <a href=
 *      "https://unix.stackexchange.com/questions/36531/format-of-cookies-when-using-wget">Cookie
 *      format for wget</a>
 * @see <a href=
 *      "https://github.com/curl/curl/blob/07ebaf837843124ee670e5b8c218b80b92e06e47/lib/cookie.c#L745">libcurl
 *      Cookie file parsing</a>
 * @see <a href=
 *      "https://github.com/curl/curl/blob/07ebaf837843124ee670e5b8c218b80b92e06e47/lib/cookie.c#L1417">libcurl
 *      Cookie file writing</a>
 */
public class NetscapeCookieFile {

	private static final String HTTP_ONLY_PREAMBLE = "#HttpOnly_"; //$NON-NLS-1$
	private static final String COLUMN_SEPARATOR = "\t"; //$NON-NLS-1$

	private static final String LINE_SEPARATOR = "\n"; //$NON-NLS-1$

	/**
	 * Parses the given file and extract all cookie information from it.
	 *
	 * @param cookieFile
	 *            the file to parse
	 * @param creationDate
	 *            the date for the creation of the cookies (used to calculate
	 *            the maxAge based on the expiration date given within the file)
	 * @return the set of parsed cookies from the given file (even expired
	 *         ones). If there is more than one cookie with the same name in
	 *         this file the last one overwrites the first one!
	 * @throws IOException
	 *             if the given file could not be read for some reason
	 * @throws IllegalArgumentException
	 *             if the given file does not have a proper format.
	 */
	public static synchronized Set<HttpCookie> read(Path cookieFile,
			Date creationDate)
			throws IOException, IllegalArgumentException {
		Set<HttpCookie> cookies = new HashSet<>();
		for (String line : Files.readAllLines(cookieFile, StandardCharsets.US_ASCII)) {
			HttpCookie cookie = parseLine(line, creationDate);
			if (cookie != null) {
				cookies.add(cookie);
			}
		}
		return cookies;
	}

	private static HttpCookie parseLine(String line, Date creationDate) {
		// skip empty lines and comment lines
		if (line.isEmpty() || (line.startsWith("#") //$NON-NLS-1$
				&& !line.startsWith(HTTP_ONLY_PREAMBLE))) {
			return null;
		}
		String[] cookieLineParts = line.split(COLUMN_SEPARATOR, 7);
		if (cookieLineParts == null) {
			throw new IllegalArgumentException(
					"Did not find any tab in line " + line); //$NON-NLS-1$
		}
		if (cookieLineParts.length < 7) {
			throw new IllegalArgumentException(
					"Each line must consist of 7 tab separated entries, found only " //$NON-NLS-1$
							+ cookieLineParts.length);
		}
		String name = cookieLineParts[5];
		String value = cookieLineParts[6];
		HttpCookie cookie = new HttpCookie(name, value);

		String domain = cookieLineParts[0];
		if (domain.startsWith(HTTP_ONLY_PREAMBLE)) {
			cookie.setHttpOnly(true);
			domain = domain.substring(HTTP_ONLY_PREAMBLE.length());
		}
		cookie.setDomain(domain);
		// domain evaluation as boolean flag not considered (i.e. always assumed
		// to be true)
		cookie.setPath(cookieLineParts[2]);
		cookie.setSecure(Boolean.parseBoolean(cookieLineParts[3]));

		long expires = Long.parseLong(cookieLineParts[4]);
		if (creationDate == null) {
			creationDate = new Date();
		}
		long maxAge = (expires - creationDate.getTime()) / 1000;
		if (maxAge < 0) {
			maxAge = -1;
		}
		cookie.setMaxAge(maxAge);
		return cookie;
	}

	/**
	 * Writes the given cookies to the file in the Netscape Cookie File Format
	 * (also used by curl)
	 *
	 * @param path
	 *            the path where to write the file. Existing content in this
	 *            file will be overwritten and the file is created if it does
	 *            not exist yet.
	 * @param cookies
	 *            the cookies to write into the file
	 * @param url
	 *            the url for which to write the cookie (to derive the default
	 *            values for certain cookie attributes)
	 * @param creationDate
	 *            the date when the cookie has been created. Important for
	 *            calculation the cookie expiration time (calculated from
	 *            cookie's maxAge and this creation time).
	 * @throws IOException
	 */
	public static synchronized void write(Path path,
			Collection<HttpCookie> cookies, URL url,
			Date creationDate)
			throws IOException {
		try (Writer writer = Files.newBufferedWriter(path,
				StandardCharsets.US_ASCII)) {
			for (HttpCookie cookie : cookies) {
				writeCookie(writer, cookie, url, creationDate);
			}
		}
	}

	private static void writeCookie(Writer writer, HttpCookie cookie, URL url,
			Date creationDate)
			throws IOException {
		String domain = ""; //$NON-NLS-1$
		if (cookie.isHttpOnly()) {
			domain = HTTP_ONLY_PREAMBLE;
		}
		if (cookie.getDomain() != null) {
			domain += cookie.getDomain();
		} else {
			domain += url.getHost();
		}
		writer.write(domain);
		writer.write(COLUMN_SEPARATOR);
		writer.write("TRUE"); //$NON-NLS-1$
		writer.write(COLUMN_SEPARATOR);
		String path = cookie.getPath();
		if (path == null) {
			path = url.getPath();
		}
		writer.write(path);
		writer.write(COLUMN_SEPARATOR);
		writer.write(Boolean.toString(cookie.getSecure()).toUpperCase());
		writer.write(COLUMN_SEPARATOR);
		final String expirationDate;
		if (cookie.getMaxAge() == -1) {
			expirationDate = "0"; //$NON-NLS-1$
		} else {
			// whenCreated field is not accessible in HttpCookie
			if (creationDate == null) {
				creationDate = new Date();
			}
			expirationDate = String
					.valueOf(creationDate.getTime()
							+ (cookie.getMaxAge() * 1000));
		}
		writer.write(expirationDate);
		writer.write(COLUMN_SEPARATOR);
		writer.write(cookie.getName());
		writer.write(COLUMN_SEPARATOR);
		writer.write(cookie.getValue());
		writer.write(LINE_SEPARATOR);
	}
}
