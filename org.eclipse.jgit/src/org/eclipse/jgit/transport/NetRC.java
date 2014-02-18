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
 * @author Alexey Kuznetsov <axet@me.com>
 *
 */
public class NetRC {
	static final Pattern NETRC = Pattern.compile("(\\S+)"); //$NON-NLS-1$

	/**
	 * @author axet
	 *
	 */
	public static class NetRCEntry {
		/**
		 * login netrc entry
		 */
		public String login;

		/**
		 * password netrc entry
		 */
		public String password;

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

	File netrc = getDefaultFile();

	long lastModified;

	Map<String, NetRCEntry> hosts = new HashMap<String, NetRCEntry>();

	enum State {
		COMMAND, MACHINE, LOGIN, PASSWORD, DEFAULT, ACCOUNT, MACDEF
	}

	/**
	 * default constructor
	 *
	 */
	public NetRC() {
		parse();
	}

	/**
	 * @param netrc
	 *            point to the .netrc file
	 */
	public NetRC(File netrc) {
		this.netrc = netrc;
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
	 * request entry by host name
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

		try {
			BufferedReader r = new BufferedReader(new FileReader(netrc));
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
						entry.password = match;
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

			if (entry.complete()) {
				hosts.put(entry.machine, entry);
			}

			r.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}