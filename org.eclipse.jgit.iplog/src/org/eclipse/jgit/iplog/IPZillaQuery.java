/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.iplog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.util.HttpSupport;

/** A crude interface to query IPzilla. */
class IPZillaQuery {
	private static final String RE_EPL = "^.*(Eclipse Public License|EPL).*$";

	private final URL base;

	private final String username;

	private final String password;

	private final ProxySelector proxySelector = ProxySelector.getDefault();

	IPZillaQuery(URL base, String username, String password) {
		this.base = base;
		this.username = username;
		this.password = password;
	}

	Set<CQ> getCQs(Collection<Project> projects) throws IOException {
		try {
			login();
			Set<CQ> cqs = new HashSet<CQ>();
			for (Project project : projects)
				cqs.addAll(queryOneProject(project));
			return cqs;
		} finally {
			// Kill the IPzilla session and log us out from there.
			logout();
		}
	}

	private Set<CQ> queryOneProject(Project project) throws IOException {
		Map<String, String> p = new LinkedHashMap<String, String>();
		p.put("bugidtype", "include");
		p.put("chfieldto", "Now");
		p.put("component", project.getID());
		p.put("field-1-0-0", "component");
		p.put("type-1-0-0", "anyexact");
		p.put("value-1-0-0", project.getID());
		p.put("ctype", "csv");

		StringBuilder req = new StringBuilder();
		for (Map.Entry<String, String> e : p.entrySet()) {
			if (req.length() > 0)
				req.append('&');
			req.append(URLEncoder.encode(e.getKey(), "UTF-8"));
			req.append('=');
			req.append(URLEncoder.encode(e.getValue(), "UTF-8"));
		}
		URL csv = new URL(new URL(base, "buglist.cgi").toString() + "?" + req);

		req = new StringBuilder();
		for (String name : new String[] { "bug_severity", "bug_status",
				"resolution", "short_desc", "cf_license", "keywords" }) {
			if (req.length() > 0)
				req.append("%20");
			req.append(name);
		}
		setCookie(csv, "COLUMNLIST", req.toString());

		HttpURLConnection conn = open(csv);
		if (HttpSupport.response(conn) != HttpURLConnection.HTTP_OK) {
			throw new IOException(MessageFormat.format(IpLogText.get().queryFailed
					, csv, conn.getResponseCode() + " " + conn.getResponseMessage()));
		}

		BufferedReader br = reader(conn);
		try {
			Set<CQ> cqs = new HashSet<CQ>();
			CSV in = new CSV(br);
			Map<String, String> row;
			while ((row = in.next()) != null) {
				CQ cq = parseOneCQ(row);
				if (cq != null)
					cqs.add(cq);
			}
			return cqs;
		} finally {
			br.close();
		}
	}

	private BufferedReader reader(HttpURLConnection conn)
			throws UnsupportedEncodingException, IOException {
		String encoding = conn.getContentEncoding();
		InputStream in = conn.getInputStream();
		if (encoding != null && !encoding.equals(""))
			return new BufferedReader(new InputStreamReader(in, encoding));
		return new BufferedReader(new InputStreamReader(in));
	}

	private void login() throws MalformedURLException,
			UnsupportedEncodingException, ConnectException, IOException {
		final URL login = new URL(base, "index.cgi");
		StringBuilder req = new StringBuilder();
		req.append("Bugzilla_login=");
		req.append(URLEncoder.encode(username, "UTF-8"));
		req.append('&');
		req.append("Bugzilla_password=");
		req.append(URLEncoder.encode(password, "UTF-8"));
		byte[] reqbin = req.toString().getBytes("UTF-8");

		HttpURLConnection c = open(login);
		c.setDoOutput(true);
		c.setFixedLengthStreamingMode(reqbin.length);
		c.setRequestProperty(HttpSupport.HDR_CONTENT_TYPE,
				"application/x-www-form-urlencoded");
		OutputStream out = c.getOutputStream();
		out.write(reqbin);
		out.close();

		if (HttpSupport.response(c) != HttpURLConnection.HTTP_OK) {
			throw new IOException(MessageFormat.format(IpLogText.get().loginFailed
					, username, login, c.getResponseCode() + " " + c.getResponseMessage()));
		}

		String content = readFully(c);
		Matcher matcher = Pattern.compile("<title>(.*)</title>",
				Pattern.CASE_INSENSITIVE).matcher(content);
		if (!matcher.find()) {
			throw new IOException(MessageFormat.format(IpLogText.get().loginFailed
					, username, login, IpLogText.get().responseNotHTMLAsExpected));
		}

		String title = matcher.group(1);
		if (!"IPZilla Main Page".equals(title)) {
			throw new IOException(MessageFormat.format(IpLogText.get().loginFailed
					, username, login
					, MessageFormat.format(IpLogText.get().pageTitleWas, title)));
		}
	}

	private String readFully(HttpURLConnection c) throws IOException {
		String enc = c.getContentEncoding();
		Reader reader;
		if (enc != null) {
			reader = new InputStreamReader(c.getInputStream(), enc);
		} else {
			reader = new InputStreamReader(c.getInputStream(), "ISO-8859-1");
		}
		try {
			StringBuilder b = new StringBuilder();
			BufferedReader r = new BufferedReader(reader);
			String line;
			while ((line = r.readLine()) != null) {
				b.append(line).append('\n');
			}
			return b.toString();
		} finally {
			reader.close();
		}
	}

	private void logout() throws MalformedURLException, ConnectException,
			IOException {
		HttpSupport.response(open(new URL(base, "relogin.cgi")));
	}

	private HttpURLConnection open(URL url) throws ConnectException,
			IOException {
		Proxy proxy = HttpSupport.proxyFor(proxySelector, url);
		HttpURLConnection c = (HttpURLConnection) url.openConnection(proxy);
		c.setUseCaches(false);
		return c;
	}

	private void setCookie(URL url, String name, String value)
			throws IOException {
		Map<String, List<String>> cols = new HashMap<String, List<String>>();
		cols.put("Set-Cookie", Collections.singletonList(name + "=" + value));
		try {
			CookieHandler.getDefault().put(url.toURI(), cols);
		} catch (URISyntaxException e) {
			IOException err = new IOException(MessageFormat.format(IpLogText.get().invalidURIFormat, url));
			err.initCause(e);
			throw err;
		}
	}

	private CQ parseOneCQ(Map<String, String> row) {
		long id = Long.parseLong(row.get("bug_id"));
		String state = row.get("bug_severity");
		String bug_status = row.get("bug_status");
		String resolution = row.get("resolution");
		String short_desc = row.get("short_desc");
		String license = row.get("cf_license");

		Set<String> keywords = new TreeSet<String>();
		for (String w : row.get("keywords").split(", *"))
			keywords.add(w);

		// Skip any CQs that were not accepted.
		//
		if ("closed".equalsIgnoreCase(state)
				|| "rejected".equalsIgnoreCase(state)
				|| "withdrawn".equalsIgnoreCase(state))
			return null;

		// Skip any CQs under the EPL without nonepl keyword
		// Skip any CQs with the EPL keyword
		//
		if (!keywords.contains("nonepl") && license.matches(RE_EPL))
			return null;
		if (keywords.contains("epl"))
			return null;

		// Work around CQs that were closed in the wrong state.
		//
		if ("new".equalsIgnoreCase(state)
				|| "under_review".equalsIgnoreCase(state)
				|| state.startsWith("awaiting_")) {
			if ("RESOLVED".equalsIgnoreCase(bug_status)
					|| "CLOSED".equalsIgnoreCase(bug_status)) {
				if ("FIXED".equalsIgnoreCase(resolution))
					state = "approved";
				else
					return null;
			}
		}

		StringBuilder use = new StringBuilder();
		for (String n : new String[] { "unmodified", "modified", "source",
				"binary" }) {
			if (keywords.contains(n)) {
				if (use.length() > 0)
					use.append(' ');
				use.append(n);
			}
		}
		if (keywords.contains("sourceandbinary")) {
			if (use.length() > 0)
				use.append(' ');
			use.append("source & binary");
		}

		CQ cq = new CQ(id);
		cq.setDescription(short_desc);
		cq.setLicense(license);
		cq.setState(state);
		if (use.length() > 0)
			cq.setUse(use.toString().trim());
		return cq;
	}
}
