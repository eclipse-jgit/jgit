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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.Config;

/**
 * A {@link CredentialsProvider} backed by standard Git {@code credential.*}
 * configuration.
 * <p>
 * The provider resolves URL-specific credential subsections using the same
 * precedence rules as {@link HttpConfig}. It applies any configured
 * {@code credential.username} directly and delegates configured
 * {@code credential.helper} values to
 * {@link GitCredentialHelperCredentialsProvider}.
 *
 * @since 7.8
 */
public class ConfigAwareCredentialsProvider extends CredentialsProvider {

	private static final String CREDENTIAL = "credential"; //$NON-NLS-1$

	private static final String HELPER = "helper"; //$NON-NLS-1$

	private static final String PASSWORD_PROMPT = "Password: "; //$NON-NLS-1$

	private static final String USE_HTTP_PATH = "useHttpPath"; //$NON-NLS-1$

	private static final String USERNAME = "username"; //$NON-NLS-1$

	private final Config config;

	/**
	 * Creates a provider reading from the given config.
	 *
	 * @param config
	 *            config to resolve credential settings from.
	 */
	public ConfigAwareCredentialsProvider(Config config) {
		this.config = config;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.InformationalMessage) {
				continue;
			}
			if (item instanceof CredentialItem.Username) {
				continue;
			}
			if (item instanceof CredentialItem.Password) {
				continue;
			}
			if (item instanceof CredentialItem.StringType
					&& PASSWORD_PROMPT.equals(item.getPromptText())) {
				continue;
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		ResolvedCredentialConfig resolved = resolve(uri);
		applyConfiguredUsername(resolved.username, uri, items);

		CredentialItem[] helperItems = filterHelperItems(resolved.username,
				items);
		if (helperItems.length > 0) {
			CredentialsProvider helpers = createHelperChain(resolved);
			if (helpers != null) {
				helpers.get(helperUri(uri, resolved.username), helperItems);
			}
		}
		return areSatisfied(items);
	}

	@Override
	public void reset(URIish uri) {
		ResolvedCredentialConfig resolved = resolve(uri);
		URIish helperUri = helperUri(uri, resolved.username);
		for (GitCredentialHelperCredentialsProvider helper : createHelpers(
				resolved)) {
			helper.reset(helperUri);
		}
	}

	private void applyConfiguredUsername(String username, URIish uri,
			CredentialItem... items) throws UnsupportedCredentialItem {
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.InformationalMessage) {
				continue;
			}
			if (item instanceof CredentialItem.Username) {
				if (username != null) {
					((CredentialItem.Username) item).setValue(username);
				}
				continue;
			}
			if (item instanceof CredentialItem.Password) {
				continue;
			}
			if (item instanceof CredentialItem.StringType
					&& PASSWORD_PROMPT.equals(item.getPromptText())) {
				continue;
			}
			throw new UnsupportedCredentialItem(uri,
					item.getClass().getName() + ':' + item.getPromptText());
		}
	}

	private CredentialsProvider createHelperChain(
			ResolvedCredentialConfig resolved) {
		List<GitCredentialHelperCredentialsProvider> helpers = createHelpers(
				resolved);
		if (helpers.isEmpty()) {
			return null;
		}
		if (helpers.size() == 1) {
			return helpers.get(0);
		}
		return new ChainingCredentialsProvider(
				helpers.toArray(new CredentialsProvider[0]));
	}

	private List<GitCredentialHelperCredentialsProvider> createHelpers(
			ResolvedCredentialConfig resolved) {
		if (resolved.helpers.isEmpty()) {
			return Collections.emptyList();
		}
		List<GitCredentialHelperCredentialsProvider> helpers = new ArrayList<>(
				resolved.helpers.size());
		for (String helper : resolved.helpers) {
			helpers.add(new GitCredentialHelperCredentialsProvider(helper)
					.setUseHttpPath(resolved.useHttpPath));
		}
		return helpers;
	}

	private CredentialItem[] filterHelperItems(String username,
			CredentialItem... items) {
		List<CredentialItem> filtered = new ArrayList<>(items.length);
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.InformationalMessage) {
				continue;
			}
			if (username != null && item instanceof CredentialItem.Username) {
				continue;
			}
			filtered.add(item);
		}
		return filtered.toArray(new CredentialItem[0]);
	}

	private ResolvedCredentialConfig resolve(URIish uri) {
		String subsection = UrlConfigMatcher
				.findMatch(config.getSubsections(CREDENTIAL), uri);
		String username = config.getString(CREDENTIAL, null, USERNAME);
		boolean useHttpPath = config.getBoolean(CREDENTIAL, USE_HTTP_PATH,
				false);
		List<String> helpers = nonEmptyValues(
				config.getStringList(CREDENTIAL, null, HELPER));

		if (subsection != null) {
			String subsectionUser = config.getString(CREDENTIAL, subsection,
					USERNAME);
			if (subsectionUser != null) {
				username = subsectionUser;
			}
			Boolean subsectionUseHttpPath = config.getBoolean(CREDENTIAL,
					subsection, USE_HTTP_PATH);
			if (subsectionUseHttpPath != null) {
				useHttpPath = subsectionUseHttpPath.booleanValue();
			}
			String[] subsectionHelpers = config.getStringList(CREDENTIAL,
					subsection, HELPER);
			if (subsectionHelpers.length > 0) {
				helpers = nonEmptyValues(subsectionHelpers);
			}
		}
		return new ResolvedCredentialConfig(username, helpers, useHttpPath);
	}

	private URIish helperUri(URIish uri, String username) {
		if (username == null) {
			return uri;
		}
		return uri.setUser(username);
	}

	private static List<String> nonEmptyValues(String[] values) {
		int start = lastEmpty(values) + 1;
		if (start >= values.length) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>(values.length - start);
		for (int i = start; i < values.length; i++) {
			if (values[i] != null) {
				result.add(values[i]);
			}
		}
		return result;
	}

	private static int lastEmpty(String[] values) {
		for (int i = values.length - 1; i >= 0; i--) {
			if (values[i] == null) {
				return i;
			}
		}
		return -1;
	}

	private static boolean areSatisfied(CredentialItem... items) {
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.InformationalMessage) {
				continue;
			}
			if (item instanceof CredentialItem.CharArrayType) {
				if (((CredentialItem.CharArrayType) item).getValue() == null) {
					return false;
				}
				continue;
			}
			if (item instanceof CredentialItem.StringType) {
				if (((CredentialItem.StringType) item).getValue() == null) {
					return false;
				}
				continue;
			}
			return false;
		}
		return true;
	}

	private static final class ResolvedCredentialConfig {

		private final List<String> helpers;

		private final boolean useHttpPath;

		private final String username;

		private ResolvedCredentialConfig(String username, List<String> helpers,
				boolean useHttpPath) {
			this.username = username;
			this.helpers = helpers;
			this.useHttpPath = useHttpPath;
		}
	}
}