/*
 * Copyright (C) 2026, JGit contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;

/**
 * A {@link CredentialsProvider} that delegates to an external credential helper
 * using the <a href="https://git-scm.com/docs/gitcredentials#_custom_helpers">
 * git credential helpers protocol</a>.
 * <p>
 * The helper is specified as a string following git's helper resolution rules:
 * <ol>
 * <li>If the helper string begins with {@code "!"}, it is treated as a shell
 * snippet and the rest of the string is executed via the shell.</li>
 * <li>If the helper string is an absolute path, it is used verbatim with the
 * operation appended as an argument.</li>
 * <li>Otherwise {@code "git-credential-"} is prepended and the result is
 * executed (via the shell for PATH resolution).</li>
 * </ol>
 * <p>
 * Example usage:
 *
 * <pre>
 * // Use the macOS Keychain helper
 * GitCredentialHelperProvider p =
 *         new GitCredentialHelperProvider("osxkeychain");
 * transport.setCredentialsProvider(p);
 *
 * // After a successful authentication, store the credentials
 * p.store(uri, username, password);
 *
 * // After a failed authentication, erase stale credentials
 * p.reset(uri);
 * </pre>
 *
 * @since 7.8
 */
public class GitCredentialHelperCredentialsProvider extends CredentialsProvider {

	private static final int PROCESS_TIMEOUT_SECONDS = 30;

	private final String helper;

	/**
	 * Whether to include the path component of an HTTP(S) URL in the
	 * credential context sent to the helper. Mirrors git's
	 * {@code credential.useHttpPath} option.
	 * <p>
	 * When {@code false} (the default, matching git's default), helpers
	 * receive only the protocol and host, so a single stored credential
	 * covers all repositories on that host. When {@code true} the full
	 * path is also sent, allowing per-repository credentials.
	 * <p>
	 * For non-HTTP(S) schemes the path is always included regardless of
	 * this setting.
	 */
	private boolean useHttpPath;

	/**
	 * Create a provider backed by the given credential helper.
	 *
	 * @param helper
	 *            the credential helper specifier following git's resolution
	 *            rules: a {@code "!"} prefix denotes a shell snippet, an
	 *            absolute path is used verbatim, otherwise
	 *            {@code "git-credential-"} is prepended.
	 */
	public GitCredentialHelperCredentialsProvider(String helper) {
		this.helper = helper;
	}

	/**
	 * Control whether the URL path is included in the credential context
	 * sent to the helper for HTTP(S) URLs.
	 * <p>
	 * When {@code false} (the default) the helper receives only the
	 * protocol and host, matching git's default behaviour where one
	 * credential covers all repositories on a host. When {@code true}
	 * the path is also sent, enabling per-repository credentials.
	 * <p>
	 * For non-HTTP(S) schemes (e.g. {@code ssh://}) the path is always
	 * included regardless of this setting.
	 *
	 * @param useHttpPath
	 *            {@code true} to include the path for HTTP(S) URLs.
	 * @return this provider, for fluent chaining.
	 */
	public GitCredentialHelperCredentialsProvider setUseHttpPath(boolean useHttpPath) {
		this.useHttpPath = useHttpPath;
		return this;
	}

	/**
	 * Return whether the path component is included for HTTP(S) URLs.
	 *
	 * @return {@code true} if the path is sent for HTTP(S) URLs.
	 */
	public boolean isUseHttpPath() {
		return useHttpPath;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username) {
				continue;
			} else if (i instanceof CredentialItem.Password) {
				continue;
			} else if (i instanceof CredentialItem.StringType) {
				if ("Password: ".equals(i.getPromptText())) { //$NON-NLS-1$
					continue;
				}
			}
			return false;
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Invokes the credential helper with the {@code get} action, writing the
	 * URI context to the helper's stdin. The helper's stdout is parsed and the
	 * returned {@code username} and {@code password} values are used to
	 * populate the requested credential items.
	 * <p>
	 * Returns {@code false} if the helper exits with a non-zero status, times
	 * out, or does not produce a username or password.
	 */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		try {
			Process process = startHelper("get"); //$NON-NLS-1$
			try {
				writeCredentialAttributes(process, uri, null, null);

				String username = null;
				String password = null;
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getInputStream(),
								UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null
							&& !line.isEmpty()) {
						int eq = line.indexOf('=');
						if (eq <= 0) {
							continue;
						}
						String key = line.substring(0, eq);
						String value = line.substring(eq + 1);
						switch (key) {
						case "username": //$NON-NLS-1$
							username = value;
							break;
						case "password": //$NON-NLS-1$
							password = value;
							break;
						default:
							// silently ignore unrecognised attributes
							break;
						}
					}
				}

				if (!process.waitFor(PROCESS_TIMEOUT_SECONDS,
						TimeUnit.SECONDS)) {
					process.destroyForcibly();
					return false;
				}
				if (process.exitValue() != 0) {
					return false;
				}
				if (username == null && password == null) {
					return false;
				}

				boolean anySet = false;
				for (CredentialItem item : items) {
					if (item instanceof CredentialItem.Username) {
						if (username != null) {
							((CredentialItem.Username) item).setValue(username);
							anySet = true;
						}
					} else if (item instanceof CredentialItem.Password) {
						if (password != null) {
							((CredentialItem.Password) item)
									.setValue(password.toCharArray());
							anySet = true;
						}
					} else if (item instanceof CredentialItem.StringType) {
						if ("Password: ".equals(item.getPromptText()) //$NON-NLS-1$
								&& password != null) {
							((CredentialItem.StringType) item).setValue(password);
							anySet = true;
						}
					} else {
						throw new UnsupportedCredentialItem(uri,
								item.getClass().getName()
										+ ":" + item.getPromptText()); //$NON-NLS-1$
					}
				}
				return anySet;
			} finally {
				process.destroy();
			}
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Invokes the credential helper with the {@code erase} action, notifying
	 * it that the credentials for the given URI were rejected. The helper may
	 * remove them from its backing store. Errors are silently ignored since
	 * this is a best-effort notification.
	 */
	@Override
	public void reset(URIish uri) {
		try {
			runWithoutOutput("erase", uri, null, null); //$NON-NLS-1$
		} catch (IOException | InterruptedException e) {
			// best-effort: ignore errors during erase
		}
	}

	/**
	 * Invokes the credential helper with the {@code store} action, asking it
	 * to persist the supplied credentials for the given URI. This should be
	 * called after a successful authentication. Errors are silently ignored
	 * since this is a best-effort notification.
	 * <p>
	 * Note: this method is not part of the
	 * {@link CredentialsProvider} interface. Callers must cast to this concrete
	 * type or use it directly to invoke store.
	 *
	 * @param uri
	 *            the URI for which credentials should be stored.
	 * @param username
	 *            the authenticated username, or {@code null} to omit.
	 * @param password
	 *            the authenticated password. The array is not modified or
	 *            cleared by this method; the caller remains responsible for
	 *            zeroing it after use.
	 */
	public void store(URIish uri, String username, char[] password) {
		try {
			runWithoutOutput("store", uri, username, password); //$NON-NLS-1$
		} catch (IOException | InterruptedException e) {
			// best-effort: ignore errors during store
		}
	}

	private void runWithoutOutput(String action, URIish uri, String username,
			char[] password) throws IOException, InterruptedException {
		Process process = startHelper(action);
		try {
			writeCredentialAttributes(process, uri, username, password);
			// drain stdout to prevent blocking on platforms with small buffers
			try (java.io.InputStream out = process.getInputStream()) {
				byte[] buf = new byte[512];
				while (out.read(buf) != -1) {
					// discard
				}
			}
			process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} finally {
			process.destroy();
		}
	}

	/**
	 * Writes credential attributes for the given URI (and optional explicit
	 * username/password) to the process's stdin, then closes stdin to signal
	 * end-of-input to the helper. The path attribute is only sent for HTTP(S)
	 * URIs when {@link #useHttpPath} is {@code true}; it is always sent for
	 * other schemes.
	 *
	 * @param process
	 *            the running helper process whose stdin will be written.
	 * @param uri
	 *            the URI providing protocol, host, path, and optional user.
	 * @param username
	 *            explicit username to send, or {@code null} to use the one
	 *            embedded in {@code uri}.
	 * @param password
	 *            explicit password to send, or {@code null} to omit.
	 * @throws IOException
	 *             if writing to the process's stdin fails.
	 */
	private void writeCredentialAttributes(Process process, URIish uri,
			String username, char[] password) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(process.getOutputStream(), UTF_8))) {
			String scheme = uri.getScheme();
			if (scheme != null && !scheme.isEmpty()) {
				writer.write("protocol="); //$NON-NLS-1$
				writer.write(scheme);
				writer.newLine();
			}
			String host = uri.getHost();
			if (host != null && !host.isEmpty()) {
				writer.write("host="); //$NON-NLS-1$
				writer.write(hostWithPort(uri));
				writer.newLine();
			}
			String path = uri.getPath();
			if (path != null && !path.isEmpty() && shouldSendPath(scheme)) {
				// strip leading slash to match git's convention
				String p = path.startsWith("/") ? path.substring(1) : path; //$NON-NLS-1$
				writer.write("path="); //$NON-NLS-1$
				writer.write(p);
				writer.newLine();
			}
			String user = (username != null) ? username : uri.getUser();
			if (user != null && !user.isEmpty()) {
				writer.write("username="); //$NON-NLS-1$
				writer.write(user);
				writer.newLine();
			}
			if (password != null && password.length > 0) {
				writer.write("password="); //$NON-NLS-1$
				writer.write(new String(password));
				writer.newLine();
			}
			writer.newLine(); // blank line terminates the attribute list
		}
	}

	/**
	 * Returns whether the path should be included for the given URI scheme.
	 * For HTTP(S) schemes the {@link #useHttpPath} flag controls this;
	 * for all other schemes the path is always sent.
	 *
	 * @param scheme
	 *            the URI scheme, or {@code null}.
	 * @return {@code true} if the path attribute should be written.
	 */
	private boolean shouldSendPath(String scheme) {
		if (scheme == null) {
			return true;
		}
		String s = scheme.toLowerCase(Locale.ROOT);
		if ("http".equals(s) || "https".equals(s)) { //$NON-NLS-1$ //$NON-NLS-2$
			return useHttpPath;
		}
		return true;
	}

	private static String hostWithPort(URIish uri) {
		int port = uri.getPort();
		if (port > 0) {
			return uri.getHost() + ":" + port; //$NON-NLS-1$
		}
		return uri.getHost();
	}

	private Process startHelper(String action) throws IOException {
		List<String> command = buildCommand(action);
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		return pb.start();
	}

	/**
	 * Builds the command list for the given credential operation, applying
	 * git's helper resolution rules.
	 * <p>
	 * Package-private for testing.
	 *
	 * @param action
	 *            one of {@code "get"}, {@code "store"}, or {@code "erase"}
	 * @return the command list suitable for {@link ProcessBuilder}
	 */
	List<String> buildCommand(String action) {
		if (helper.startsWith("!")) { //$NON-NLS-1$
			String snippet = helper.substring(1);
			if (isWindows()) {
				return Arrays.asList("cmd", "/c", //$NON-NLS-1$ //$NON-NLS-2$
						snippet + " " + action); //$NON-NLS-1$
			}
			return Arrays.asList("sh", "-c", //$NON-NLS-1$ //$NON-NLS-2$
					snippet + " " + action); //$NON-NLS-1$
		}
		if (Paths.get(helper).isAbsolute()) {
			return Arrays.asList(helper, action);
		}
		// Not an absolute path: prepend "git-credential-" and resolve via PATH
		String resolvedCmd = "git-credential-" + helper + " " + action; //$NON-NLS-1$ //$NON-NLS-2$
		if (isWindows()) {
			return Arrays.asList("cmd", "/c", resolvedCmd); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return Arrays.asList("sh", "-c", resolvedCmd); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "") //$NON-NLS-1$ //$NON-NLS-2$
				.toLowerCase(Locale.ROOT).contains("windows"); //$NON-NLS-1$
	}
}
