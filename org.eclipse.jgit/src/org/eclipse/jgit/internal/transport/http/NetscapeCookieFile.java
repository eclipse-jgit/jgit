/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.http;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.HttpCookie;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps all cookies persisted in a <strong>Netscape Cookie File Format</strong>
 * being referenced via the git config <a href=
 * "https://git-scm.com/docs/git-config#git-config-httpcookieFile">http.cookieFile</a>.
 * <p>
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

	/**
	 * Maximum number of retries to acquire the lock for writing to the
	 * underlying file.
	 */
	private static final int LOCK_ACQUIRE_MAX_RETRY_COUNT = 4;

	/**
	 * Sleep time in milliseconds between retries to acquire the lock for
	 * writing to the underlying file.
	 */
	private static final int LOCK_ACQUIRE_RETRY_SLEEP = 500;

	private final Path path;

	private FileSnapshot snapshot;

	private byte[] hash;

	final Date creationDate;

	private Set<HttpCookie> cookies = null;

	private static final Logger LOG = LoggerFactory
			.getLogger(NetscapeCookieFile.class);

	/**
	 * @param path
	 *            where to find the cookie file
	 */
	public NetscapeCookieFile(Path path) {
		this(path, new Date());
	}

	NetscapeCookieFile(Path path, Date creationDate) {
		this.path = path;
		this.snapshot = FileSnapshot.DIRTY;
		this.creationDate = creationDate;
	}

	/**
	 * Path to the underlying cookie file.
	 *
	 * @return the path
	 */
	public Path getPath() {
		return path;
	}

	/**
	 * Return all cookies from the underlying cookie file.
	 *
	 * @param refresh
	 *            if {@code true} updates the list from the underlying cookie
	 *            file if it has been modified since the last read otherwise
	 *            returns the current transient state. In case the cookie file
	 *            has never been read before will always read from the
	 *            underlying file disregarding the value of this parameter.
	 * @return all cookies (may contain session cookies as well). This does not
	 *         return a copy of the list but rather the original one. Every
	 *         addition to the returned list can afterwards be persisted via
	 *         {@link #write(URL)}. Errors in the underlying file will not lead
	 *         to exceptions but rather to an empty set being returned and the
	 *         underlying error being logged.
	 */
	public Set<HttpCookie> getCookies(boolean refresh) {
		if (cookies == null || refresh) {
			try {
				byte[] in = getFileContentIfModified();
				Set<HttpCookie> newCookies = parseCookieFile(in, creationDate);
				if (cookies != null) {
					cookies = mergeCookies(newCookies, cookies);
				} else {
					cookies = newCookies;
				}
				return cookies;
			} catch (IOException | IllegalArgumentException e) {
				LOG.warn(
						MessageFormat.format(
								JGitText.get().couldNotReadCookieFile, path),
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
	 * @param input
	 *            the file content to parse
	 * @param creationDate
	 *            the date for the creation of the cookies (used to calculate
	 *            the maxAge based on the expiration date given within the file)
	 * @return the set of parsed cookies from the given file (even expired
	 *         ones). If there is more than one cookie with the same name in
	 *         this file the last one overwrites the first one!
	 * @throws IOException
	 *             if the given file could not be read for some reason
	 * @throws IllegalArgumentException
	 *             if the given file does not have a proper format
	 */
	private static Set<HttpCookie> parseCookieFile(@NonNull byte[] input,
			@NonNull Date creationDate)
			throws IOException, IllegalArgumentException {

		String decoded = RawParseUtils.decode(StandardCharsets.US_ASCII, input);

		Set<HttpCookie> cookies = new LinkedHashSet<>();
		try (BufferedReader reader = new BufferedReader(
				new StringReader(decoded))) {
			String line;
			while ((line = reader.readLine()) != null) {
				HttpCookie cookie = parseLine(line, creationDate);
				if (cookie != null) {
					cookies.add(cookie);
				}
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
	 * Read the underying file and return its content but only in case it has
	 * been modified since the last access.
	 * <p>
	 * Internally calculates the hash and maintains {@link FileSnapshot}s to
	 * prevent issues described as <a href=
	 * "https://github.com/git/git/blob/master/Documentation/technical/racy-git.txt">"Racy
	 * Git problem"</a>. Inspired by {@link FileBasedConfig#load()}.
	 *
	 * @return the file contents in case the file has been modified since the
	 *         last access, otherwise {@code null}
	 * @throws IOException
	 *             if the file is not found or cannot be read
	 */
	private byte[] getFileContentIfModified() throws IOException {
		final int maxStaleRetries = 5;
		int retries = 0;
		File file = getPath().toFile();
		if (!file.exists()) {
			LOG.warn(MessageFormat.format(JGitText.get().missingCookieFile,
					file.getAbsolutePath()));
			return new byte[0];
		}
		while (true) {
			final FileSnapshot oldSnapshot = snapshot;
			final FileSnapshot newSnapshot = FileSnapshot.save(file);
			try {
				final byte[] in = IO.readFully(file);
				byte[] newHash = hash(in);
				if (Arrays.equals(hash, newHash)) {
					if (oldSnapshot.equals(newSnapshot)) {
						oldSnapshot.setClean(newSnapshot);
					} else {
						snapshot = newSnapshot;
					}
				} else {
					snapshot = newSnapshot;
					hash = newHash;
				}
				return in;
			} catch (FileNotFoundException e) {
				throw e;
			} catch (IOException e) {
				if (FileUtils.isStaleFileHandle(e)
						&& retries < maxStaleRetries) {
					if (LOG.isDebugEnabled()) {
						LOG.debug(MessageFormat.format(
								JGitText.get().configHandleIsStale,
								Integer.valueOf(retries)), e);
					}
					retries++;
					continue;
				}
				throw new IOException(MessageFormat
						.format(JGitText.get().cannotReadFile, getPath()), e);
			}
		}

	}

	private static byte[] hash(final byte[] in) {
		return Constants.newMessageDigest().digest(in);
	}

	/**
	 * Writes all the cookies being maintained in the set being returned by
	 * {@link #getCookies(boolean)} to the underlying file.
	 * <p>
	 * Session-cookies will not be persisted.
	 *
	 * @param url
	 *            url for which to write the cookies (important to derive
	 *            default values for non-explicitly set attributes)
	 * @throws IOException
	 *             if the underlying cookie file could not be read or written or
	 *             a problem with the lock file
	 * @throws InterruptedException
	 *             if the thread is interrupted while waiting for the lock
	 */
	public void write(URL url) throws IOException, InterruptedException {
		try {
			byte[] cookieFileContent = getFileContentIfModified();
			if (cookieFileContent != null) {
				LOG.debug("Reading the underlying cookie file '{}' " //$NON-NLS-1$
						+ "as it has been modified since " //$NON-NLS-1$
						+ "the last access", //$NON-NLS-1$
						path);
				// reread new changes if necessary
				Set<HttpCookie> cookiesFromFile = NetscapeCookieFile
						.parseCookieFile(cookieFileContent, creationDate);
				this.cookies = mergeCookies(cookiesFromFile, cookies);
			}
		} catch (FileNotFoundException e) {
			// ignore if file previously did not exist yet!
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (Writer writer = new OutputStreamWriter(output,
				StandardCharsets.US_ASCII)) {
			write(writer, cookies, url, creationDate);
		}
		LockFile lockFile = new LockFile(path.toFile());
		for (int retryCount = 0; retryCount < LOCK_ACQUIRE_MAX_RETRY_COUNT; retryCount++) {
			if (lockFile.lock()) {
				try {
					lockFile.setNeedSnapshot(true);
					lockFile.write(output.toByteArray());
					if (!lockFile.commit()) {
						throw new IOException(MessageFormat.format(
								JGitText.get().cannotCommitWriteTo, path));
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
	 * (also used by curl).
	 *
	 * @param writer
	 *            the writer to use to persist the cookies
	 * @param cookies
	 *            the cookies to write into the file
	 * @param url
	 *            the url for which to write the cookie (to derive the default
	 *            values for certain cookie attributes)
	 * @param creationDate
	 *            the date when the cookie has been created. Important for
	 *            calculation the cookie expiration time (calculated from
	 *            cookie's maxAge and this creation time)
	 * @throws IOException
	 *             if an I/O error occurs
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
	 *            first set of cookies
	 * @param cookies2
	 *            second set of cookies
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
