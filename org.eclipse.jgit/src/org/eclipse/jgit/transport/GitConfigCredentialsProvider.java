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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.StringUtils;

/**
 * A {@link CredentialsProvider} that runs the git credential helpers configured
 * in a {@link Config} (git {@code credential_fill} +
 * {@code credential_apply_config}): it reads {@code credential.helper} and the
 * best-matching {@code [credential "<url>"]} subsection, honors empty-value
 * reset and {@code credential.useHttpPath}, tries the helpers in order
 * (stopping when a request is satisfied), and optionally falls back to a
 * supplied provider.
 * <p>
 * git accumulates helpers from every matching subsection in config-file order;
 * this resolves only the single best-matching subsection.
 * <p>
 * Operates over the supplied {@code Config}; the caller decides the layering
 * (typically {@code Repository.getConfig()}). URL matching reuses
 * {@link HttpConfig#findMatch(java.util.Set, URIish)}; the chain reuses
 * {@link ChainingCredentialsProvider}; each helper is an
 * {@link ExecCredentialsProvider}.
 * <p>
 * Resolves username/password and, when opted in, Bearer helpers, and runs their
 * {@code store}/{@code erase} actions. Multistage (NTLM/Negotiate) auth is not
 * handled.
 *
 * @since 7.9
 */
public class GitConfigCredentialsProvider extends CredentialsProvider {

	private static final String SECTION = "credential"; //$NON-NLS-1$

	private static final String HELPER = "helper"; //$NON-NLS-1$

	private static final String USE_HTTP_PATH = "useHttpPath"; //$NON-NLS-1$

	private final Config config;

	private final CredentialsProvider fallback;

	private boolean useBearer;

	/**
	 * Create a provider that runs only the configured helpers.
	 *
	 * @param config
	 *            the effective git config to read {@code credential.*} from
	 */
	public GitConfigCredentialsProvider(Config config) {
		this(config, null);
	}

	/**
	 * Create a provider that runs the configured helpers, then falls back to
	 * another provider for anything they do not supply.
	 *
	 * @param config
	 *            the effective git config to read {@code credential.*} from
	 * @param fallback
	 *            the provider to try after the helpers (e.g. an interactive
	 *            provider), or {@code null}
	 */
	public GitConfigCredentialsProvider(Config config,
			CredentialsProvider fallback) {
		this.config = config;
		this.fallback = fallback;
	}

	/**
	 * Off by default. When set, configured helpers may return a Bearer
	 * {@code authtype}/{@code credential} pair instead of a username/password.
	 *
	 * @param use
	 *            whether the configured helpers may answer with a Bearer token
	 * @return {@code this}
	 */
	public GitConfigCredentialsProvider setUseBearer(boolean use) {
		this.useBearer = use;
		return this;
	}

	@Override
	public boolean isInteractive() {
		return fallback != null && fallback.isInteractive();
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem item : items) {
			if (isHelperItem(item)) {
				continue;
			}
			if (fallback != null && fallback.supports(item)) {
				continue;
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		List<CredentialsProvider> chain = chain(uri);
		if (chain.isEmpty()) {
			return false;
		}
		return new ChainingCredentialsProvider(
				chain.toArray(new CredentialsProvider[0])).get(uri, items);
	}

	@Override
	public void reset(URIish uri) {
		for (CredentialsProvider p : chain(uri)) {
			p.reset(uri);
		}
	}

	@Override
	public void store(URIish uri, CredentialItem... items) {
		for (CredentialsProvider p : chain(uri)) {
			p.store(uri, items);
		}
	}

	@Override
	public void erase(URIish uri) {
		for (CredentialsProvider p : chain(uri)) {
			p.erase(uri);
		}
	}

	private List<CredentialsProvider> chain(URIish uri) {
		List<CredentialsProvider> chain = new ArrayList<>();
		boolean useHttpPath = resolveUseHttpPath(uri);
		for (String helper : resolveHelpers(uri)) {
			chain.add(new ExecCredentialsProvider(helper)
					.setUseHttpPath(useHttpPath).setUseBearer(useBearer));
		}
		if (fallback != null) {
			chain.add(fallback);
		}
		return chain;
	}

	private List<String> resolveHelpers(URIish uri) {
		List<String> helpers = new ArrayList<>();
		accumulate(helpers, config.getStringList(SECTION, null, HELPER));
		String sub = HttpConfig.findMatch(config.getSubsections(SECTION), uri);
		if (sub != null) {
			accumulate(helpers, config.getStringList(SECTION, sub, HELPER));
		}
		return helpers;
	}

	private static void accumulate(List<String> helpers, String[] values) {
		for (String value : values) {
			if (StringUtils.isEmptyOrNull(value)) {
				helpers.clear(); // an empty helper value resets the list (git)
			} else {
				helpers.add(value);
			}
		}
	}

	private boolean resolveUseHttpPath(URIish uri) {
		boolean base = config.getBoolean(SECTION, USE_HTTP_PATH, false);
		String sub = HttpConfig.findMatch(config.getSubsections(SECTION), uri);
		if (sub != null) {
			return config.getBoolean(SECTION, sub, USE_HTTP_PATH, base);
		}
		return base;
	}

	private boolean isHelperItem(CredentialItem item) {
		return item instanceof CredentialItem.Username
				|| item instanceof CredentialItem.Password
				|| item instanceof CredentialItem.InformationalMessage
				|| (useBearer && item instanceof CredentialItem.Bearer);
	}
}
