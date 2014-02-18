/*
 * Copyright (C) 2014, Alexey Kuznetsov <axet@me.com>
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NetRC file parser.
 *
 */
public class NetRC {
	static final Pattern NETRC = Pattern.compile("(\\S+)"); //$NON-NLS-1$

	/**
	 * .netrc file entry
	 */
	public static class NetRCEntry {
		/**
		 * login netrc entry
		 */
		public String login;

		/**
		 * password netrc entry
		 */
		public char[] password;

		/**
		 * 'default' netrc entry
		 */
		public Boolean def;

		/**
		 * machine netrc entry
		 */
		public String machine;

		/**
		 * account netrc entry
		 */
		public String account;

		/**
		 * macdef netrc entry
		 */
		public String macdef;

		/**
		 * Default constructor
		 */
		public NetRCEntry() {
		}

		boolean complete() {
			return login != null && password != null && machine != null;
		}
	}

	File netrc;

	long lastModified;

	Map<String, NetRCEntry> hosts = new HashMap<String, NetRCEntry>();

	enum State {
		COMMAND, MACHINE, LOGIN, PASSWORD, DEFAULT, ACCOUNT, MACDEF
	}

	/**
	 *
	 */
	public NetRC() {
		netrc = getDefaultFile();
		parse();
	}

	/**
	 * @param netrc
	 *            point to the .netrc file
	 */
	public NetRC(File netrc) {
		this.netrc = netrc;
		parse();
	}

	private static File getDefaultFile() {
		File netrc;

		File home = new File(System.getProperty("user.home")); //$NON-NLS-1$

		netrc = new File(home, ".netrc"); //$NON-NLS-1$
		if (netrc.exists())
			return netrc;

		netrc = new File(home, "_netrc"); //$NON-NLS-1$
		if (netrc.exists())
			return netrc;

		return null;
	}

	/**
	 * Request entry by host name
	 *
	 * @param host
	 * @return entry associated with host name or null
	 */
	public NetRCEntry entry(String host) {
		if (this.lastModified != this.netrc.lastModified())
			parse();
		return this.hosts.get(host);
	}

	/**
	 * @return all entries collected from .netrc file
	 */
	public Collection<NetRCEntry> entries() {
		return hosts.values();
	}

	TreeMap<String, State> STATE = new TreeMap<String, NetRC.State>() {
		private static final long serialVersionUID = -4285910831814853334L;
		{
			put("machine", State.MACHINE); //$NON-NLS-1$
			put("login", State.LOGIN); //$NON-NLS-1$
			put("password", State.PASSWORD); //$NON-NLS-1$
			put("default", State.DEFAULT); //$NON-NLS-1$
			put("account", State.ACCOUNT); //$NON-NLS-1$
			put("macdef", State.MACDEF); //$NON-NLS-1$
		}
	};

	void parse() {
		this.hosts.clear();
		this.lastModified = this.netrc.lastModified();

		BufferedReader r = null;
		try {
			r = new BufferedReader(new FileReader(netrc));
			String line = null;

			NetRCEntry entry = new NetRCEntry();

			State state = State.COMMAND;

			Matcher matcher = NETRC.matcher(""); //$NON-NLS-1$
			while ((line = r.readLine()) != null) {
				matcher.reset(line);
				while (matcher.find()) {
					String match = matcher.group();
					switch (state) {
					case COMMAND:
						String command = match.toLowerCase();
						if (command.startsWith("#")) { //$NON-NLS-1$
							// command starts with # means it is a comment until
							// end of line
							// ex: #machine a login b password d // ignore whole
							// line
							// ex # machine a login b password d // ignore whole
							// line
							// ex machine a login #b # password d // ignore
							// password, but take login as "#b"
							matcher.reset(""); //$NON-NLS-1$
							continue;
						}
						state = STATE.get(command);
						if (state == null)
							state = State.COMMAND;
						break;
					case ACCOUNT:
						if (entry.account != null && entry.complete()) {
							hosts.put(entry.machine, entry);
							entry = new NetRCEntry();
						}
						entry.account = match;
						state = State.COMMAND;
						break;
					case LOGIN:
						if (entry.login != null && entry.complete()) {
							hosts.put(entry.machine, entry);
							entry = new NetRCEntry();
						}
						entry.login = match;
						state = State.COMMAND;
						break;
					case PASSWORD:
						if (entry.password != null && entry.complete()) {
							hosts.put(entry.machine, entry);
							entry = new NetRCEntry();
						}
						entry.password = match.toCharArray();
						state = State.COMMAND;
						break;
					case DEFAULT:
						if (entry.def != null && entry.complete()) {
							hosts.put(entry.machine, entry);
							entry = new NetRCEntry();
						}
						entry.def = new Boolean(true);
						state = State.COMMAND;
						break;
					case MACDEF:
						if (entry.macdef != null && entry.complete()) {
							hosts.put(entry.machine, entry);
							entry = new NetRCEntry();
						}
						entry.macdef = match;
						state = State.COMMAND;
						break;
					case MACHINE:
						if (entry.machine != null && entry.complete()) {
							hosts.put(entry.machine, entry);
							entry = new NetRCEntry();
						}
						entry.machine = match;
						state = State.COMMAND;
						break;
					}
				}
			}

			if (entry.complete())
				hosts.put(entry.machine, entry);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				if (r != null)
					r.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}