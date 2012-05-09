/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestOutputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * A simple HTTP REST client for the Amazon S3 service.
 * <p>
 * This client uses the REST API to communicate with the Amazon S3 servers and
 * read or write content through a bucket that the user has access to. It is a
 * very lightweight implementation of the S3 API and therefore does not have all
 * of the bells and whistles of popular client implementations.
 * <p>
 * Authentication is always performed using the user's AWSAccessKeyId and their
 * private AWSSecretAccessKey.
 * <p>
 * Optional client-side encryption may be enabled if requested. The format is
 * compatible with <a href="http://jets3t.s3.amazonaws.com/index.html">jets3t</a>,
 * a popular Java based Amazon S3 client library. Enabling encryption can hide
 * sensitive data from the operators of the S3 service.
 */
public class AmazonS3 {
	private static final Set<String> SIGNED_HEADERS;

	private static final String HMAC = "HmacSHA1";

	private static final String DOMAIN = "s3.amazonaws.com";

	private static final String X_AMZ_ACL = "x-amz-acl";

	private static final String X_AMZ_META = "x-amz-meta-";

	static {
		SIGNED_HEADERS = new HashSet<String>();
		SIGNED_HEADERS.add("content-type");
		SIGNED_HEADERS.add("content-md5");
		SIGNED_HEADERS.add("date");
	}

	private static boolean isSignedHeader(final String name) {
		final String nameLC = StringUtils.toLowerCase(name);
		return SIGNED_HEADERS.contains(nameLC) || nameLC.startsWith("x-amz-");
	}

	private static String toCleanString(final List<String> list) {
		final StringBuilder s = new StringBuilder();
		for (final String v : list) {
			if (s.length() > 0)
				s.append(',');
			s.append(v.replaceAll("\n", "").trim());
		}
		return s.toString();
	}

	private static String remove(final Map<String, String> m, final String k) {
		final String r = m.remove(k);
		return r != null ? r : "";
	}

	private static String httpNow() {
		final String tz = "GMT";
		final SimpleDateFormat fmt;
		fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
		fmt.setTimeZone(TimeZone.getTimeZone(tz));
		return fmt.format(new Date()) + " " + tz;
	}

	private static MessageDigest newMD5() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(JGitText.get().JRELacksMD5Implementation, e);
		}
	}

	/** AWSAccessKeyId, public string that identifies the user's account. */
	private final String publicKey;

	/** Decoded form of the private AWSSecretAccessKey, to sign requests. */
	private final SecretKeySpec privateKey;

	/** Our HTTP proxy support, in case we are behind a firewall. */
	private final ProxySelector proxySelector;

	/** ACL to apply to created objects. */
	private final String acl;

	/** Maximum number of times to try an operation. */
	private final int maxAttempts;

	/** Encryption algorithm, may be a null instance that provides pass-through. */
	private final WalkEncryption encryption;

	/**
	 * Create a new S3 client for the supplied user information.
	 * <p>
	 * The connection properties are a subset of those supported by the popular
	 * <a href="http://jets3t.s3.amazonaws.com/index.html">jets3t</a> library.
	 * For example:
	 *
	 * <pre>
	 * # AWS Access and Secret Keys (required)
	 * accesskey: &lt;YourAWSAccessKey&gt;
	 * secretkey: &lt;YourAWSSecretKey&gt;
	 *
	 * # Access Control List setting to apply to uploads, must be one of:
	 * # PRIVATE, PUBLIC_READ (defaults to PRIVATE).
	 * acl: PRIVATE
	 *
	 * # Number of times to retry after internal error from S3.
	 * httpclient.retry-max: 3
	 *
	 * # End-to-end encryption (hides content from S3 owners)
	 * password: &lt;encryption pass-phrase&gt;
	 * crypto.algorithm: PBEWithMD5AndDES
	 * </pre>
	 *
	 * @param props
	 *            connection properties.
	 *
	 */
	public AmazonS3(final Properties props) {
		publicKey = props.getProperty("accesskey");
		if (publicKey == null)
			throw new IllegalArgumentException(JGitText.get().missingAccesskey);

		final String secret = props.getProperty("secretkey");
		if (secret == null)
			throw new IllegalArgumentException(JGitText.get().missingSecretkey);
		privateKey = new SecretKeySpec(Constants.encodeASCII(secret), HMAC);

		final String pacl = props.getProperty("acl", "PRIVATE");
		if (StringUtils.equalsIgnoreCase("PRIVATE", pacl))
			acl = "private";
		else if (StringUtils.equalsIgnoreCase("PUBLIC", pacl))
			acl = "public-read";
		else if (StringUtils.equalsIgnoreCase("PUBLIC-READ", pacl))
			acl = "public-read";
		else if (StringUtils.equalsIgnoreCase("PUBLIC_READ", pacl))
			acl = "public-read";
		else
			throw new IllegalArgumentException("Invalid acl: " + pacl);

		try {
			final String cPas = props.getProperty("password");
			if (cPas != null) {
				String cAlg = props.getProperty("crypto.algorithm");
				if (cAlg == null)
					cAlg = "PBEWithMD5AndDES";
				encryption = new WalkEncryption.ObjectEncryptionV2(cAlg, cPas);
			} else {
				encryption = WalkEncryption.NONE;
			}
		} catch (InvalidKeySpecException e) {
			throw new IllegalArgumentException(JGitText.get().invalidEncryption, e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(JGitText.get().invalidEncryption, e);
		}

		maxAttempts = Integer.parseInt(props.getProperty(
				"httpclient.retry-max", "3"));
		proxySelector = ProxySelector.getDefault();
	}

	/**
	 * Get the content of a bucket object.
	 *
	 * @param bucket
	 *            name of the bucket storing the object.
	 * @param key
	 *            key of the object within its bucket.
	 * @return connection to stream the content of the object. The request
	 *         properties of the connection may not be modified by the caller as
	 *         the request parameters have already been signed.
	 * @throws IOException
	 *             sending the request was not possible.
	 */
	public URLConnection get(final String bucket, final String key)
			throws IOException {
		for (int curAttempt = 0; curAttempt < maxAttempts; curAttempt++) {
			final HttpURLConnection c = open("GET", bucket, key);
			authorize(c);
			switch (HttpSupport.response(c)) {
			case HttpURLConnection.HTTP_OK:
				encryption.validate(c, X_AMZ_META);
				return c;
			case HttpURLConnection.HTTP_NOT_FOUND:
				throw new FileNotFoundException(key);
			case HttpURLConnection.HTTP_INTERNAL_ERROR:
				continue;
			default:
				throw error("Reading", key, c);
			}
		}
		throw maxAttempts("Reading", key);
	}

	/**
	 * Decrypt an input stream from {@link #get(String, String)}.
	 *
	 * @param u
	 *            connection previously created by {@link #get(String, String)}}.
	 * @return stream to read plain text from.
	 * @throws IOException
	 *             decryption could not be configured.
	 */
	public InputStream decrypt(final URLConnection u) throws IOException {
		return encryption.decrypt(u.getInputStream());
	}

	/**
	 * List the names of keys available within a bucket.
	 * <p>
	 * This method is primarily meant for obtaining a "recursive directory
	 * listing" rooted under the specified bucket and prefix location.
	 *
	 * @param bucket
	 *            name of the bucket whose objects should be listed.
	 * @param prefix
	 *            common prefix to filter the results by. Must not be null.
	 *            Supplying the empty string will list all keys in the bucket.
	 *            Supplying a non-empty string will act as though a trailing '/'
	 *            appears in prefix, even if it does not.
	 * @return list of keys starting with <code>prefix</code>, after removing
	 *         <code>prefix</code> (or <code>prefix + "/"</code>)from all
	 *         of them.
	 * @throws IOException
	 *             sending the request was not possible, or the response XML
	 *             document could not be parsed properly.
	 */
	public List<String> list(final String bucket, String prefix)
			throws IOException {
		if (prefix.length() > 0 && !prefix.endsWith("/"))
			prefix += "/";
		final ListParser lp = new ListParser(bucket, prefix);
		do {
			lp.list();
		} while (lp.truncated);
		return lp.entries;
	}

	/**
	 * Delete a single object.
	 * <p>
	 * Deletion always succeeds, even if the object does not exist.
	 *
	 * @param bucket
	 *            name of the bucket storing the object.
	 * @param key
	 *            key of the object within its bucket.
	 * @throws IOException
	 *             deletion failed due to communications error.
	 */
	public void delete(final String bucket, final String key)
			throws IOException {
		for (int curAttempt = 0; curAttempt < maxAttempts; curAttempt++) {
			final HttpURLConnection c = open("DELETE", bucket, key);
			authorize(c);
			switch (HttpSupport.response(c)) {
			case HttpURLConnection.HTTP_NO_CONTENT:
				return;
			case HttpURLConnection.HTTP_INTERNAL_ERROR:
				continue;
			default:
				throw error("Deletion", key, c);
			}
		}
		throw maxAttempts("Deletion", key);
	}

	/**
	 * Atomically create or replace a single small object.
	 * <p>
	 * This form is only suitable for smaller contents, where the caller can
	 * reasonable fit the entire thing into memory.
	 * <p>
	 * End-to-end data integrity is assured by internally computing the MD5
	 * checksum of the supplied data and transmitting the checksum along with
	 * the data itself.
	 *
	 * @param bucket
	 *            name of the bucket storing the object.
	 * @param key
	 *            key of the object within its bucket.
	 * @param data
	 *            new data content for the object. Must not be null. Zero length
	 *            array will create a zero length object.
	 * @throws IOException
	 *             creation/updating failed due to communications error.
	 */
	public void put(final String bucket, final String key, final byte[] data)
			throws IOException {
		if (encryption != WalkEncryption.NONE) {
			// We have to copy to produce the cipher text anyway so use
			// the large object code path as it supports that behavior.
			//
			final OutputStream os = beginPut(bucket, key, null, null);
			os.write(data);
			os.close();
			return;
		}

		final String md5str = Base64.encodeBytes(newMD5().digest(data));
		final String lenstr = String.valueOf(data.length);
		for (int curAttempt = 0; curAttempt < maxAttempts; curAttempt++) {
			final HttpURLConnection c = open("PUT", bucket, key);
			c.setRequestProperty("Content-Length", lenstr);
			c.setRequestProperty("Content-MD5", md5str);
			c.setRequestProperty(X_AMZ_ACL, acl);
			authorize(c);
			c.setDoOutput(true);
			c.setFixedLengthStreamingMode(data.length);
			final OutputStream os = c.getOutputStream();
			try {
				os.write(data);
			} finally {
				os.close();
			}

			switch (HttpSupport.response(c)) {
			case HttpURLConnection.HTTP_OK:
				return;
			case HttpURLConnection.HTTP_INTERNAL_ERROR:
				continue;
			default:
				throw error("Writing", key, c);
			}
		}
		throw maxAttempts("Writing", key);
	}

	/**
	 * Atomically create or replace a single large object.
	 * <p>
	 * Initially the returned output stream buffers data into memory, but if the
	 * total number of written bytes starts to exceed an internal limit the data
	 * is spooled to a temporary file on the local drive.
	 * <p>
	 * Network transmission is attempted only when <code>close()</code> gets
	 * called at the end of output. Closing the returned stream can therefore
	 * take significant time, especially if the written content is very large.
	 * <p>
	 * End-to-end data integrity is assured by internally computing the MD5
	 * checksum of the supplied data and transmitting the checksum along with
	 * the data itself.
	 *
	 * @param bucket
	 *            name of the bucket storing the object.
	 * @param key
	 *            key of the object within its bucket.
	 * @param monitor
	 *            (optional) progress monitor to post upload completion to
	 *            during the stream's close method.
	 * @param monitorTask
	 *            (optional) task name to display during the close method.
	 * @return a stream which accepts the new data, and transmits once closed.
	 * @throws IOException
	 *             if encryption was enabled it could not be configured.
	 */
	public OutputStream beginPut(final String bucket, final String key,
			final ProgressMonitor monitor, final String monitorTask)
			throws IOException {
		final MessageDigest md5 = newMD5();
		final TemporaryBuffer buffer = new TemporaryBuffer.LocalFile() {
			@Override
			public void close() throws IOException {
				super.close();
				try {
					putImpl(bucket, key, md5.digest(), this, monitor,
							monitorTask);
				} finally {
					destroy();
				}
			}
		};
		return encryption.encrypt(new DigestOutputStream(buffer, md5));
	}

	private void putImpl(final String bucket, final String key,
			final byte[] csum, final TemporaryBuffer buf,
			ProgressMonitor monitor, String monitorTask) throws IOException {
		if (monitor == null)
			monitor = NullProgressMonitor.INSTANCE;
		if (monitorTask == null)
			monitorTask = MessageFormat.format(JGitText.get().progressMonUploading, key);

		final String md5str = Base64.encodeBytes(csum);
		final long len = buf.length();
		final String lenstr = String.valueOf(len);
		for (int curAttempt = 0; curAttempt < maxAttempts; curAttempt++) {
			final HttpURLConnection c = open("PUT", bucket, key);
			c.setRequestProperty("Content-Length", lenstr);
			c.setRequestProperty("Content-MD5", md5str);
			c.setRequestProperty(X_AMZ_ACL, acl);
			encryption.request(c, X_AMZ_META);
			authorize(c);
			c.setDoOutput(true);
			c.setFixedLengthStreamingMode((int) len);
			monitor.beginTask(monitorTask, (int) (len / 1024));
			final OutputStream os = c.getOutputStream();
			try {
				buf.writeTo(os, monitor);
			} finally {
				monitor.endTask();
				os.close();
			}

			switch (HttpSupport.response(c)) {
			case HttpURLConnection.HTTP_OK:
				return;
			case HttpURLConnection.HTTP_INTERNAL_ERROR:
				continue;
			default:
				throw error("Writing", key, c);
			}
		}
		throw maxAttempts("Writing", key);
	}

	private IOException error(final String action, final String key,
			final HttpURLConnection c) throws IOException {
		final IOException err = new IOException(MessageFormat.format(
				JGitText.get().amazonS3ActionFailed, action, key,
				Integer.valueOf(HttpSupport.response(c)),
				c.getResponseMessage()));
		final InputStream errorStream = c.getErrorStream();
		if (errorStream == null)
			return err;

		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		byte[] buf = new byte[2048];
		for (;;) {
			final int n = errorStream.read(buf);
			if (n < 0)
				break;
			if (n > 0)
				b.write(buf, 0, n);
		}
		buf = b.toByteArray();
		if (buf.length > 0)
			err.initCause(new IOException("\n" + new String(buf)));
		return err;
	}

	private IOException maxAttempts(final String action, final String key) {
		return new IOException(MessageFormat.format(
				JGitText.get().amazonS3ActionFailedGivingUp, action, key,
				Integer.valueOf(maxAttempts)));
	}

	private HttpURLConnection open(final String method, final String bucket,
			final String key) throws IOException {
		final Map<String, String> noArgs = Collections.emptyMap();
		return open(method, bucket, key, noArgs);
	}

	private HttpURLConnection open(final String method, final String bucket,
			final String key, final Map<String, String> args)
			throws IOException {
		final StringBuilder urlstr = new StringBuilder();
		urlstr.append("http://");
		urlstr.append(bucket);
		urlstr.append('.');
		urlstr.append(DOMAIN);
		urlstr.append('/');
		if (key.length() > 0)
			HttpSupport.encode(urlstr, key);
		if (!args.isEmpty()) {
			final Iterator<Map.Entry<String, String>> i;

			urlstr.append('?');
			i = args.entrySet().iterator();
			while (i.hasNext()) {
				final Map.Entry<String, String> e = i.next();
				urlstr.append(e.getKey());
				urlstr.append('=');
				HttpSupport.encode(urlstr, e.getValue());
				if (i.hasNext())
					urlstr.append('&');
			}
		}

		final URL url = new URL(urlstr.toString());
		final Proxy proxy = HttpSupport.proxyFor(proxySelector, url);
		final HttpURLConnection c;

		c = (HttpURLConnection) url.openConnection(proxy);
		c.setRequestMethod(method);
		c.setRequestProperty("User-Agent", "jgit/1.0");
		c.setRequestProperty("Date", httpNow());
		return c;
	}

	private void authorize(final HttpURLConnection c) throws IOException {
		final Map<String, List<String>> reqHdr = c.getRequestProperties();
		final SortedMap<String, String> sigHdr = new TreeMap<String, String>();
		for (final Map.Entry<String, List<String>> entry : reqHdr.entrySet()) {
			final String hdr = entry.getKey();
			if (isSignedHeader(hdr))
				sigHdr.put(StringUtils.toLowerCase(hdr), toCleanString(entry.getValue()));
		}

		final StringBuilder s = new StringBuilder();
		s.append(c.getRequestMethod());
		s.append('\n');

		s.append(remove(sigHdr, "content-md5"));
		s.append('\n');

		s.append(remove(sigHdr, "content-type"));
		s.append('\n');

		s.append(remove(sigHdr, "date"));
		s.append('\n');

		for (final Map.Entry<String, String> e : sigHdr.entrySet()) {
			s.append(e.getKey());
			s.append(':');
			s.append(e.getValue());
			s.append('\n');
		}

		final String host = c.getURL().getHost();
		s.append('/');
		s.append(host.substring(0, host.length() - DOMAIN.length() - 1));
		s.append(c.getURL().getPath());

		final String sec;
		try {
			final Mac m = Mac.getInstance(HMAC);
			m.init(privateKey);
			sec = Base64.encodeBytes(m.doFinal(s.toString().getBytes("UTF-8")));
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(MessageFormat.format(JGitText.get().noHMACsupport, HMAC, e.getMessage()));
		} catch (InvalidKeyException e) {
			throw new IOException(MessageFormat.format(JGitText.get().invalidKey, e.getMessage()));
		}
		c.setRequestProperty("Authorization", "AWS " + publicKey + ":" + sec);
	}

	static Properties properties(final File authFile)
			throws FileNotFoundException, IOException {
		final Properties p = new Properties();
		final FileInputStream in = new FileInputStream(authFile);
		try {
			p.load(in);
		} finally {
			in.close();
		}
		return p;
	}

	private final class ListParser extends DefaultHandler {
		final List<String> entries = new ArrayList<String>();

		private final String bucket;

		private final String prefix;

		boolean truncated;

		private StringBuilder data;

		ListParser(final String bn, final String p) {
			bucket = bn;
			prefix = p;
		}

		void list() throws IOException {
			final Map<String, String> args = new TreeMap<String, String>();
			if (prefix.length() > 0)
				args.put("prefix", prefix);
			if (!entries.isEmpty())
				args.put("marker", prefix + entries.get(entries.size() - 1));

			for (int curAttempt = 0; curAttempt < maxAttempts; curAttempt++) {
				final HttpURLConnection c = open("GET", bucket, "", args);
				authorize(c);
				switch (HttpSupport.response(c)) {
				case HttpURLConnection.HTTP_OK:
					truncated = false;
					data = null;

					final XMLReader xr;
					try {
						xr = XMLReaderFactory.createXMLReader();
					} catch (SAXException e) {
						throw new IOException(JGitText.get().noXMLParserAvailable);
					}
					xr.setContentHandler(this);
					final InputStream in = c.getInputStream();
					try {
						xr.parse(new InputSource(in));
					} catch (SAXException parsingError) {
						final IOException p;
						p = new IOException(MessageFormat.format(JGitText.get().errorListing, prefix));
						p.initCause(parsingError);
						throw p;
					} finally {
						in.close();
					}
					return;

				case HttpURLConnection.HTTP_INTERNAL_ERROR:
					continue;

				default:
					throw AmazonS3.this.error("Listing", prefix, c);
				}
			}
			throw maxAttempts("Listing", prefix);
		}

		@Override
		public void startElement(final String uri, final String name,
				final String qName, final Attributes attributes)
				throws SAXException {
			if ("Key".equals(name) || "IsTruncated".equals(name))
				data = new StringBuilder();
		}

		@Override
		public void ignorableWhitespace(final char[] ch, final int s,
				final int n) throws SAXException {
			if (data != null)
				data.append(ch, s, n);
		}

		@Override
		public void characters(final char[] ch, final int s, final int n)
				throws SAXException {
			if (data != null)
				data.append(ch, s, n);
		}

		@Override
		public void endElement(final String uri, final String name,
				final String qName) throws SAXException {
			if ("Key".equals(name))
				entries.add(data.toString().substring(prefix.length()));
			else if ("IsTruncated".equals(name))
				truncated = StringUtils.equalsIgnoreCase("true", data.toString());
			data = null;
		}
	}
}
