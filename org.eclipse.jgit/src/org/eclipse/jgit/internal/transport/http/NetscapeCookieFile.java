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
package org.eclipse.jgit.internal.transport.http;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpCookie;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps all cookies persisted in a <strong>Netscape Cookie File Format</strong>
 * being referenced via the git config <a href=
 * "https://git-scm.com/docs/git-config#git-config-httpcookieFile">http.cookieFile</a>.
 *
 * It will only load the cookies lazily, i.e. before calling
 * {@link #getCookies(boolean)} the file is not evaluated. This class also
 * allows persisting cookies in that file format.
 * <p>
 * In general this class is not thread-safe. So any consumer needs to take care
 * of synchronization!
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
 * @see NetscapeCookieFileCache
 */
public final class NetscapeCookieFile {

	private static final String HTTP_ONLY_PREAMBLE = "#HttpOnly_"; //$NON-NLS-1$

	private static final String COLUMN_SEPARATOR = "\t"; //$NON-NLS-1$

	private static final String LINE_SEPARATOR = "\n"; //$NON-NLS-1$

	private static final Integer LOCK_ACQUIRE_MAX_RETRY_COUNT_DEFAULT = Integer
			.valueOf(4);

	/**
	 * Maximum number of retries to acquire the lock for reading or writing to
	 * the underlying file.
	 */
	private static final int LOCK_ACQUIRE_MAX_RETRY_COUNT = Integer
			.getInteger("NetscapeCookieFile.LockAcquireMaxRetryCount", //$NON-NLS-1$
					LOCK_ACQUIRE_MAX_RETRY_COUNT_DEFAULT)
			.intValue();

	private static final Integer LOCK_ACQUIRE_RETRY_SLEEP_DEFAULT = Integer
			.valueOf(500);

	/**
	 * Sleep time in milliseconds between retries to acquire the lock for
	 * reading or writing to the underlying file.
	 */
	private static final int LOCK_ACQUIRE_RETRY_SLEEP = Integer
			.getInteger("NetscapeCookieFile.LockAcquireRetrySleep", //$NON-NLS-1$
					LOCK_ACQUIRE_RETRY_SLEEP_DEFAULT)
			.intValue();

	private static final int MAX_STALE_FILEHANDLE_RETRIES = 5;

	private final Path file;

	private FileSnapshot snapshot;

	final Date creationDate;

	private Set<HttpCookie> cookies = null;

	private static final Logger LOG = LoggerFactory
			.getLogger(NetscapeCookieFile.class);

	/**
	 * @param file
	 */
	public NetscapeCookieFile(Path file) {
		this.file = file;
		this.snapshot = FileSnapshot.save(file.toFile());
		creationDate = new Date();
	}

	/**
	 * @return the underlying cookie file
	 */
	public Path getFile() {
		return file;
	}

	/**
	 * @param refresh
	 *            if {@code true} updates the list from the underlying cookie
	 *            file if it has been touched since the last read otherwise
	 *            returns the current transient state.
	 * @return all cookies (may contain session cookies as well). This does not
	 *         return a copy of the list but rather the original one. Every
	 *         addition to the returned list can afterwards be persisted via
	 *         {@link #write(URL)}. Errors in the underlying file will not lead
	 *         to exceptions but rather to an empty set being returned and the
	 *         underlying error being logged.
	 */
	public Set<HttpCookie> getCookies(boolean refresh) {
		if (cookies == null
				|| (refresh && snapshot.isModified(file.toFile()))) {
			try {
				for (int retryCount = 0; retryCount < MAX_STALE_FILEHANDLE_RETRIES; retryCount++) {
					try {
						snapshot = FileSnapshot.save(file.toFile());
						Set<HttpCookie> newCookies = read(file, creationDate);
						if (cookies != null) {
							cookies = mergeCookies(newCookies, cookies);
						} else {
							cookies = newCookies;
						}
						return cookies;
					} catch (IOException e) {
						if (FileUtils.isStaleFileHandle(e)) {
							if (LOG.isDebugEnabled()) {
								LOG.debug(MessageFormat.format(
										JGitText.get().configHandleIsStale,
										Integer.valueOf(retryCount)), e);
							}
							continue;
						} else {
							throw e;
						}
					}
				}
			} catch (IOException | IllegalArgumentException e) {
				LOG.warn(
						MessageFormat.format(
								JGitText.get().couldNotReadCookieFile, file),
						e);
				if (cookies == null) {
					cookies = new LinkedHashSet<>();
				}
			}
		}
		return cookies;
	}

	/**
	 * Parses the given file and extracts all cookie information from it.
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
	static Set<HttpCookie> read(@NonNull Path cookieFile,
			@NonNull Date creationDate)
			throws IOException, IllegalArgumentException {
		Set<HttpCookie> cookies = new LinkedHashSet<>();
		for (String line : Files.readAllLines(cookieFile,
				StandardCharsets.US_ASCII)) {
			HttpCookie cookie = parseLine(line, creationDate);
			if (cookie != null) {
				cookies.add(cookie);
			}
		}
		return cookies;
	}

	private static HttpCookie parseLine(@NonNull String line,
			@NonNull Date creationDate) {
		if (line.isEmpty() || (line.startsWith("#") //$NON-NLS-1$
				&& !line.startsWith(HTTP_ONLY_PREAMBLE))) {
			return null;
		}
		String[] cookieLineParts = line.split(COLUMN_SEPARATOR, 7);
		if (cookieLineParts == null) {
			throw new IllegalArgumentException(MessageFormat
					.format(JGitText.get().couldNotFindTabInLine, line));
		}
		if (cookieLineParts.length < 7) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().couldNotFindSixTabsInLine,
					Integer.valueOf(cookieLineParts.length), line));
		}
		String name = cookieLineParts[5];
		String value = cookieLineParts[6];
		HttpCookie cookie = new HttpCookie(name, value);

		String domain = cookieLineParts[0];
		if (domain.startsWith(HTTP_ONLY_PREAMBLE)) {
			cookie.setHttpOnly(true);
			domain = domain.substring(HTTP_ONLY_PREAMBLE.length());
		}
		// strip off leading "."
		// (https://tools.ietf.org/html/rfc6265#section-5.2.3)
		if (domain.startsWith(".")) { //$NON-NLS-1$
			domain = domain.substring(1);
		}
		cookie.setDomain(domain);
		// domain evaluation as boolean flag not considered (i.e. always assumed
		// to be true)
		cookie.setPath(cookieLineParts[2]);
		cookie.setSecure(Boolean.parseBoolean(cookieLineParts[3]));

		long expires = Long.parseLong(cookieLineParts[4]);
		long maxAge = (expires - creationDate.getTime()) / 1000;
		if (maxAge <= 0) {
			return null; // skip expired cookies
		}
		cookie.setMaxAge(maxAge);
		return cookie;
	}

	/**
	 * Writes all the cookies being maintained in the set being returned by
	 * {@link #getCookies(boolean)} to the underlying file.
	 *
	 * Session-cookies will not be persisted.
	 *
	 * @param url
	 *            url for which to write the cookies (important to derive
	 *            default values for non-explicitly set attributes)
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws InterruptedException
	 */
	public void write(URL url)
			throws IllegalArgumentException, IOException, InterruptedException {
		LockFile lockFile = new LockFile(file.toFile());
		for (int retryCount = 0; retryCount < LOCK_ACQUIRE_MAX_RETRY_COUNT; retryCount++) {
			if (lockFile.lock()) {
				try {
					// only write if lock could get acquired
					if (snapshot.isModified(file.toFile())) {
						LOG.debug(
								"Reading the underlying cookie file '{}' as it has been modified since the last access", //$NON-NLS-1$
								file);
						// reread new changes if necessary
						Set<HttpCookie> cookiesFromFile = NetscapeCookieFile
								.read(file, creationDate);
						this.cookies = mergeCookies(cookiesFromFile, cookies);
					}
					try (OutputStream output = new BufferedOutputStream(
							lockFile.getOutputStream());
							Writer writer = new OutputStreamWriter(output,
									StandardCharsets.US_ASCII)) {
						write(writer, cookies, url, creationDate);
					}
					if (!lockFile.commit()) {
						throw new IOException(MessageFormat.format(
								JGitText.get().cannotCommitWriteTo, file));
					}
				} finally {
					lockFile.unlock();
				}
				return;
			}
			Thread.sleep(LOCK_ACQUIRE_RETRY_SLEEP);
		}
		throw new IOException(
				MessageFormat.format(JGitText.get().cannotLock, lockFile));
	}

	/**
	 * Writes the given cookies to the file in the Netscape Cookie File Format
	 * (also used by curl)
	 *
	 * @param writer
	 *            the writer to use to persist the cookies.
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
	static void write(@NonNull Writer writer,
			@NonNull Collection<HttpCookie> cookies, @NonNull URL url,
			@NonNull Date creationDate) throws IOException {
		for (HttpCookie cookie : cookies) {
			writeCookie(writer, cookie, url, creationDate);
		}
	}

	private static void writeCookie(@NonNull Writer writer,
			@NonNull HttpCookie cookie, @NonNull URL url,
			@NonNull Date creationDate) throws IOException {
		if (cookie.getMaxAge() <= 0) {
			return; // skip expired cookies
		}
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
		// whenCreated field is not accessible in HttpCookie
		expirationDate = String
				.valueOf(creationDate.getTime() + (cookie.getMaxAge() * 1000));
		writer.write(expirationDate);
		writer.write(COLUMN_SEPARATOR);
		writer.write(cookie.getName());
		writer.write(COLUMN_SEPARATOR);
		writer.write(cookie.getValue());
		writer.write(LINE_SEPARATOR);
	}

	/**
	 * Merge the given sets in the following way. All cookies from
	 * {@code cookies1} and {@code cookies2} are contained in the resulting set
	 * which have unique names. If there is a duplicate entry for one name only
	 * the entry from set {@code cookies1} ends up in the resulting set.
	 *
	 * @param cookies1
	 * @param cookies2
	 *
	 * @return the merged cookies
	 */
	static Set<HttpCookie> mergeCookies(Set<HttpCookie> cookies1,
			@Nullable Set<HttpCookie> cookies2) {
		Set<HttpCookie> mergedCookies = new LinkedHashSet<>(cookies1);
		if (cookies2 != null) {
			mergedCookies.addAll(cookies2);
		}
		return mergedCookies;
	}
}
