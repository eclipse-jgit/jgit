/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Config;

/**
 * A remembered remote repository, including URLs and RefSpecs.
 * <p>
 * A remote configuration remembers one or more URLs for a frequently accessed
 * remote repository as well as zero or more fetch and push specifications
 * describing how refs should be transferred between this repository and the
 * remote repository.
 */
public class RemoteConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final String SECTION = "remote"; //$NON-NLS-1$

	private static final String KEY_URL = "url"; //$NON-NLS-1$

	private static final String KEY_PUSHURL = "pushurl"; //$NON-NLS-1$

	private static final String KEY_FETCH = "fetch"; //$NON-NLS-1$

	private static final String KEY_PUSH = "push"; //$NON-NLS-1$

	private static final String KEY_UPLOADPACK = "uploadpack"; //$NON-NLS-1$

	private static final String KEY_RECEIVEPACK = "receivepack"; //$NON-NLS-1$

	private static final String KEY_TAGOPT = "tagopt"; //$NON-NLS-1$

	private static final String KEY_MIRROR = "mirror"; //$NON-NLS-1$

	private static final String KEY_TIMEOUT = "timeout"; //$NON-NLS-1$

	private static final boolean DEFAULT_MIRROR = false;

	/** Default value for {@link #getUploadPack()} if not specified. */
	public static final String DEFAULT_UPLOAD_PACK = "git-upload-pack"; //$NON-NLS-1$

	/** Default value for {@link #getReceivePack()} if not specified. */
	public static final String DEFAULT_RECEIVE_PACK = "git-receive-pack"; //$NON-NLS-1$

	/**
	 * Parse all remote blocks in an existing configuration file, looking for
	 * remotes configuration.
	 *
	 * @param rc
	 *            the existing configuration to get the remote settings from.
	 *            The configuration must already be loaded into memory.
	 * @return all remotes configurations existing in provided repository
	 *         configuration. Returned configurations are ordered
	 *         lexicographically by names.
	 * @throws java.net.URISyntaxException
	 *             one of the URIs within the remote's configuration is invalid.
	 */
	public static List<RemoteConfig> getAllRemoteConfigs(Config rc)
			throws URISyntaxException {
		final List<String> names = new ArrayList<>(rc
				.getSubsections(SECTION));
		Collections.sort(names);

		final List<RemoteConfig> result = new ArrayList<>(names
				.size());
		for (String name : names)
			result.add(new RemoteConfig(rc, name));
		return result;
	}

	private String name;

	private List<URIish> uris;

	private List<URIish> pushURIs;

	private List<RefSpec> fetch;

	private List<RefSpec> push;

	private String uploadpack;

	private String receivepack;

	private TagOpt tagopt;

	private boolean mirror;

	private int timeout;

	/**
	 * Parse a remote block from an existing configuration file.
	 * <p>
	 * This constructor succeeds even if the requested remote is not defined
	 * within the supplied configuration file. If that occurs then there will be
	 * no URIs and no ref specifications known to the new instance.
	 *
	 * @param rc
	 *            the existing configuration to get the remote settings from.
	 *            The configuration must already be loaded into memory.
	 * @param remoteName
	 *            subsection key indicating the name of this remote.
	 * @throws java.net.URISyntaxException
	 *             one of the URIs within the remote's configuration is invalid.
	 */
	public RemoteConfig(Config rc, String remoteName)
			throws URISyntaxException {
		name = remoteName;

		String[] vlst;
		String val;

		vlst = rc.getStringList(SECTION, name, KEY_URL);
		UrlConfig urls = new UrlConfig(rc);
		uris = new ArrayList<>(vlst.length);
		for (String s : vlst) {
			uris.add(new URIish(urls.replace(s)));
		}
		String[] plst = rc.getStringList(SECTION, name, KEY_PUSHURL);
		pushURIs = new ArrayList<>(plst.length);
		for (String s : plst) {
			pushURIs.add(new URIish(s));
		}
		if (pushURIs.isEmpty()) {
			// Would default to the uris. If we have pushinsteadof, we must
			// supply rewritten push uris.
			if (urls.hasPushReplacements()) {
				for (String s : vlst) {
					String replaced = urls.replacePush(s);
					if (!s.equals(replaced)) {
						pushURIs.add(new URIish(replaced));
					}
				}
			}
		}
		fetch = rc.getRefSpecs(SECTION, name, KEY_FETCH);
		push = rc.getRefSpecs(SECTION, name, KEY_PUSH);
		val = rc.getString(SECTION, name, KEY_UPLOADPACK);
		if (val == null) {
			val = DEFAULT_UPLOAD_PACK;
		}
		uploadpack = val;

		val = rc.getString(SECTION, name, KEY_RECEIVEPACK);
		if (val == null) {
			val = DEFAULT_RECEIVE_PACK;
		}
		receivepack = val;

		try {
			val = rc.getString(SECTION, name, KEY_TAGOPT);
			tagopt = TagOpt.fromOption(val);
		} catch (IllegalArgumentException e) {
			// C git silently ignores invalid tagopt values.
			tagopt = TagOpt.AUTO_FOLLOW;
		}
		mirror = rc.getBoolean(SECTION, name, KEY_MIRROR, DEFAULT_MIRROR);
		timeout = rc.getInt(SECTION, name, KEY_TIMEOUT, 0);
	}

	/**
	 * Update this remote's definition within the configuration.
	 *
	 * @param rc
	 *            the configuration file to store ourselves into.
	 */
	public void update(Config rc) {
		final List<String> vlst = new ArrayList<>();

		vlst.clear();
		for (URIish u : getURIs())
			vlst.add(u.toPrivateString());
		rc.setStringList(SECTION, getName(), KEY_URL, vlst);

		vlst.clear();
		for (URIish u : getPushURIs())
			vlst.add(u.toPrivateString());
		rc.setStringList(SECTION, getName(), KEY_PUSHURL, vlst);

		vlst.clear();
		for (RefSpec u : getFetchRefSpecs())
			vlst.add(u.toString());
		rc.setStringList(SECTION, getName(), KEY_FETCH, vlst);

		vlst.clear();
		for (RefSpec u : getPushRefSpecs())
			vlst.add(u.toString());
		rc.setStringList(SECTION, getName(), KEY_PUSH, vlst);

		set(rc, KEY_UPLOADPACK, getUploadPack(), DEFAULT_UPLOAD_PACK);
		set(rc, KEY_RECEIVEPACK, getReceivePack(), DEFAULT_RECEIVE_PACK);
		set(rc, KEY_TAGOPT, getTagOpt().option(), TagOpt.AUTO_FOLLOW.option());
		set(rc, KEY_MIRROR, mirror, DEFAULT_MIRROR);
		set(rc, KEY_TIMEOUT, timeout, 0);
	}

	private void set(final Config rc, final String key,
			final String currentValue, final String defaultValue) {
		if (defaultValue.equals(currentValue))
			unset(rc, key);
		else
			rc.setString(SECTION, getName(), key, currentValue);
	}

	private void set(final Config rc, final String key,
			final boolean currentValue, final boolean defaultValue) {
		if (defaultValue == currentValue)
			unset(rc, key);
		else
			rc.setBoolean(SECTION, getName(), key, currentValue);
	}

	private void set(final Config rc, final String key, final int currentValue,
			final int defaultValue) {
		if (defaultValue == currentValue)
			unset(rc, key);
		else
			rc.setInt(SECTION, getName(), key, currentValue);
	}

	private void unset(Config rc, String key) {
		rc.unset(SECTION, getName(), key);
	}

	/**
	 * Get the local name this remote configuration is recognized as.
	 *
	 * @return name assigned by the user to this configuration block.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get all configured URIs under this remote.
	 *
	 * @return the set of URIs known to this remote.
	 */
	public List<URIish> getURIs() {
		return Collections.unmodifiableList(uris);
	}

	/**
	 * Add a new URI to the end of the list of URIs.
	 *
	 * @param toAdd
	 *            the new URI to add to this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean addURI(URIish toAdd) {
		if (uris.contains(toAdd))
			return false;
		return uris.add(toAdd);
	}

	/**
	 * Remove a URI from the list of URIs.
	 *
	 * @param toRemove
	 *            the URI to remove from this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean removeURI(URIish toRemove) {
		return uris.remove(toRemove);
	}

	/**
	 * Get all configured push-only URIs under this remote.
	 *
	 * @return the set of URIs known to this remote.
	 */
	public List<URIish> getPushURIs() {
		return Collections.unmodifiableList(pushURIs);
	}

	/**
	 * Add a new push-only URI to the end of the list of URIs.
	 *
	 * @param toAdd
	 *            the new URI to add to this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean addPushURI(URIish toAdd) {
		if (pushURIs.contains(toAdd))
			return false;
		return pushURIs.add(toAdd);
	}

	/**
	 * Remove a push-only URI from the list of URIs.
	 *
	 * @param toRemove
	 *            the URI to remove from this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean removePushURI(URIish toRemove) {
		return pushURIs.remove(toRemove);
	}

	/**
	 * Remembered specifications for fetching from a repository.
	 *
	 * @return set of specs used by default when fetching.
	 */
	public List<RefSpec> getFetchRefSpecs() {
		return Collections.unmodifiableList(fetch);
	}

	/**
	 * Add a new fetch RefSpec to this remote.
	 *
	 * @param s
	 *            the new specification to add.
	 * @return true if the specification was added; false if it already exists.
	 */
	public boolean addFetchRefSpec(RefSpec s) {
		if (fetch.contains(s))
			return false;
		return fetch.add(s);
	}

	/**
	 * Override existing fetch specifications with new ones.
	 *
	 * @param specs
	 *            list of fetch specifications to set. List is copied, it can be
	 *            modified after this call.
	 */
	public void setFetchRefSpecs(List<RefSpec> specs) {
		fetch.clear();
		fetch.addAll(specs);
	}

	/**
	 * Override existing push specifications with new ones.
	 *
	 * @param specs
	 *            list of push specifications to set. List is copied, it can be
	 *            modified after this call.
	 */
	public void setPushRefSpecs(List<RefSpec> specs) {
		push.clear();
		push.addAll(specs);
	}

	/**
	 * Remove a fetch RefSpec from this remote.
	 *
	 * @param s
	 *            the specification to remove.
	 * @return true if the specification existed and was removed.
	 */
	public boolean removeFetchRefSpec(RefSpec s) {
		return fetch.remove(s);
	}

	/**
	 * Remembered specifications for pushing to a repository.
	 *
	 * @return set of specs used by default when pushing.
	 */
	public List<RefSpec> getPushRefSpecs() {
		return Collections.unmodifiableList(push);
	}

	/**
	 * Add a new push RefSpec to this remote.
	 *
	 * @param s
	 *            the new specification to add.
	 * @return true if the specification was added; false if it already exists.
	 */
	public boolean addPushRefSpec(RefSpec s) {
		if (push.contains(s))
			return false;
		return push.add(s);
	}

	/**
	 * Remove a push RefSpec from this remote.
	 *
	 * @param s
	 *            the specification to remove.
	 * @return true if the specification existed and was removed.
	 */
	public boolean removePushRefSpec(RefSpec s) {
		return push.remove(s);
	}

	/**
	 * Override for the location of 'git-upload-pack' on the remote system.
	 * <p>
	 * This value is only useful for an SSH style connection, where Git is
	 * asking the remote system to execute a program that provides the necessary
	 * network protocol.
	 *
	 * @return location of 'git-upload-pack' on the remote system. If no
	 *         location has been configured the default of 'git-upload-pack' is
	 *         returned instead.
	 */
	public String getUploadPack() {
		return uploadpack;
	}

	/**
	 * Override for the location of 'git-receive-pack' on the remote system.
	 * <p>
	 * This value is only useful for an SSH style connection, where Git is
	 * asking the remote system to execute a program that provides the necessary
	 * network protocol.
	 *
	 * @return location of 'git-receive-pack' on the remote system. If no
	 *         location has been configured the default of 'git-receive-pack' is
	 *         returned instead.
	 */
	public String getReceivePack() {
		return receivepack;
	}

	/**
	 * Get the description of how annotated tags should be treated during fetch.
	 *
	 * @return option indicating the behavior of annotated tags in fetch.
	 */
	public TagOpt getTagOpt() {
		return tagopt;
	}

	/**
	 * Set the description of how annotated tags should be treated on fetch.
	 *
	 * @param option
	 *            method to use when handling annotated tags.
	 */
	public void setTagOpt(TagOpt option) {
		tagopt = option != null ? option : TagOpt.AUTO_FOLLOW;
	}

	/**
	 * Whether pushing to the remote automatically deletes remote refs which
	 * don't exist on the source side.
	 *
	 * @return true if pushing to the remote automatically deletes remote refs
	 *         which don't exist on the source side.
	 */
	public boolean isMirror() {
		return mirror;
	}

	/**
	 * Set the mirror flag to automatically delete remote refs.
	 *
	 * @param m
	 *            true to automatically delete remote refs during push.
	 */
	public void setMirror(boolean m) {
		mirror = m;
	}

	/**
	 * Get timeout (in seconds) before aborting an IO operation.
	 *
	 * @return timeout (in seconds) before aborting an IO operation.
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the timeout before willing to abort an IO call.
	 *
	 * @param seconds
	 *            number of seconds to wait (with no data transfer occurring)
	 *            before aborting an IO read or write operation with this
	 *            remote.  A timeout of 0 will block indefinitely.
	 */
	public void setTimeout(int seconds) {
		timeout = seconds;
	}
}
