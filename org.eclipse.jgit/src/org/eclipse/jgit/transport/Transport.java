/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TransferConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.FS;

/**
 * Connects two Git repositories together and copies objects between them.
 * <p>
 * A transport can be used for either fetching (copying objects into the
 * caller's repository from the remote repository) or pushing (copying objects
 * into the remote repository from the caller's repository). Each transport
 * implementation is responsible for the details associated with establishing
 * the network connection(s) necessary for the copy, as well as actually
 * shuffling data back and forth.
 * <p>
 * Transport instances and the connections they create are not thread-safe.
 * Callers must ensure a transport is accessed by only one thread at a time.
 */
public abstract class Transport {
	/** Type of operation a Transport is being opened for. */
	public enum Operation {
		/** Transport is to fetch objects locally. */
		FETCH,
		/** Transport is to push objects remotely. */
		PUSH;
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 * <p>
	 * This method assumes {@link Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository - may be URI or remote
	 *            configuration name.
	 * @return the new transport instance. Never null. In case of multiple URIs
	 *         in remote configuration, only the first is chosen.
	 * @throws URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static Transport open(final Repository local, final String remote)
			throws NotSupportedException, URISyntaxException {
		return open(local, remote, Operation.FETCH);
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 *
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository - may be URI or remote
	 *            configuration name.
	 * @param op
	 *            planned use of the returned Transport; the URI may differ
	 *            based on the type of connection desired.
	 * @return the new transport instance. Never null. In case of multiple URIs
	 *         in remote configuration, only the first is chosen.
	 * @throws URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static Transport open(final Repository local, final String remote,
			final Operation op) throws NotSupportedException,
			URISyntaxException {
		final RemoteConfig cfg = new RemoteConfig(local.getConfig(), remote);
		if (doesNotExist(cfg))
			return open(local, new URIish(remote));
		return open(local, cfg, op);
	}

	/**
	 * Open new transport instances to connect two repositories.
	 * <p>
	 * This method assumes {@link Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository - may be URI or remote
	 *            configuration name.
	 * @return the list of new transport instances for every URI in remote
	 *         configuration.
	 * @throws URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static List<Transport> openAll(final Repository local,
			final String remote) throws NotSupportedException,
			URISyntaxException {
		return openAll(local, remote, Operation.FETCH);
	}

	/**
	 * Open new transport instances to connect two repositories.
	 *
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository - may be URI or remote
	 *            configuration name.
	 * @param op
	 *            planned use of the returned Transport; the URI may differ
	 *            based on the type of connection desired.
	 * @return the list of new transport instances for every URI in remote
	 *         configuration.
	 * @throws URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static List<Transport> openAll(final Repository local,
			final String remote, final Operation op)
			throws NotSupportedException, URISyntaxException {
		final RemoteConfig cfg = new RemoteConfig(local.getConfig(), remote);
		if (doesNotExist(cfg)) {
			final ArrayList<Transport> transports = new ArrayList<Transport>(1);
			transports.add(open(local, new URIish(remote)));
			return transports;
		}
		return openAll(local, cfg, op);
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 * <p>
	 * This method assumes {@link Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param cfg
	 *            configuration describing how to connect to the remote
	 *            repository.
	 * @return the new transport instance. Never null. In case of multiple URIs
	 *         in remote configuration, only the first is chosen.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws IllegalArgumentException
	 *             if provided remote configuration doesn't have any URI
	 *             associated.
	 */
	public static Transport open(final Repository local, final RemoteConfig cfg)
			throws NotSupportedException {
		return open(local, cfg, Operation.FETCH);
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 *
	 * @param local
	 *            existing local repository.
	 * @param cfg
	 *            configuration describing how to connect to the remote
	 *            repository.
	 * @param op
	 *            planned use of the returned Transport; the URI may differ
	 *            based on the type of connection desired.
	 * @return the new transport instance. Never null. In case of multiple URIs
	 *         in remote configuration, only the first is chosen.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws IllegalArgumentException
	 *             if provided remote configuration doesn't have any URI
	 *             associated.
	 */
	public static Transport open(final Repository local,
			final RemoteConfig cfg, final Operation op)
			throws NotSupportedException {
		final List<URIish> uris = getURIs(cfg, op);
		if (uris.isEmpty())
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().remoteConfigHasNoURIAssociated, cfg.getName()));
		final Transport tn = open(local, uris.get(0));
		tn.applyConfig(cfg);
		return tn;
	}

	/**
	 * Open new transport instances to connect two repositories.
	 * <p>
	 * This method assumes {@link Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param cfg
	 *            configuration describing how to connect to the remote
	 *            repository.
	 * @return the list of new transport instances for every URI in remote
	 *         configuration.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static List<Transport> openAll(final Repository local,
			final RemoteConfig cfg) throws NotSupportedException {
		return openAll(local, cfg, Operation.FETCH);
	}

	/**
	 * Open new transport instances to connect two repositories.
	 *
	 * @param local
	 *            existing local repository.
	 * @param cfg
	 *            configuration describing how to connect to the remote
	 *            repository.
	 * @param op
	 *            planned use of the returned Transport; the URI may differ
	 *            based on the type of connection desired.
	 * @return the list of new transport instances for every URI in remote
	 *         configuration.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static List<Transport> openAll(final Repository local,
			final RemoteConfig cfg, final Operation op)
			throws NotSupportedException {
		final List<URIish> uris = getURIs(cfg, op);
		final List<Transport> transports = new ArrayList<Transport>(uris.size());
		for (final URIish uri : uris) {
			final Transport tn = open(local, uri);
			tn.applyConfig(cfg);
			transports.add(tn);
		}
		return transports;
	}

	private static List<URIish> getURIs(final RemoteConfig cfg,
			final Operation op) {
		switch (op) {
		case FETCH:
			return cfg.getURIs();
		case PUSH: {
			List<URIish> uris = cfg.getPushURIs();
			if (uris.isEmpty())
				uris = cfg.getURIs();
			return uris;
		}
		default:
			throw new IllegalArgumentException(op.toString());
		}
	}

	private static boolean doesNotExist(final RemoteConfig cfg) {
		return cfg.getURIs().isEmpty() && cfg.getPushURIs().isEmpty();
	}

	/**
	 * Determines whether the transport can handle the given URIish.
	 *
	 * @param remote
	 *            location of the remote repository.
	 * @param fs
	 *            type of filesystem the local repository is stored on.
	 * @return true if the protocol is supported.
	 */
	public static boolean canHandleProtocol(final URIish remote, final FS fs) {
		if (TransportGitSsh.canHandle(remote))
			return true;

		else if (TransportHttp.canHandle(remote))
			return true;

		else if (TransportSftp.canHandle(remote))
			return true;

		else if (TransportGitAnon.canHandle(remote))
			return true;

		else if (TransportAmazonS3.canHandle(remote))
			return true;

		else if (TransportBundleFile.canHandle(remote, fs))
			return true;

		else if (TransportLocal.canHandle(remote, fs))
			return true;

		return false;
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 *
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository.
	 * @return the new transport instance. Never null.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static Transport open(final Repository local, final URIish remote)
			throws NotSupportedException {
		if (TransportGitSsh.canHandle(remote))
			return new TransportGitSsh(local, remote);

		else if (TransportHttp.canHandle(remote))
			return new TransportHttp(local, remote);

		else if (TransportSftp.canHandle(remote))
			return new TransportSftp(local, remote);

		else if (TransportGitAnon.canHandle(remote))
			return new TransportGitAnon(local, remote);

		else if (TransportAmazonS3.canHandle(remote))
			return new TransportAmazonS3(local, remote);

		else if (TransportBundleFile.canHandle(remote, local.getFS()))
			return new TransportBundleFile(local, remote);

		else if (TransportLocal.canHandle(remote, local.getFS()))
			return new TransportLocal(local, remote);

		throw new NotSupportedException(MessageFormat.format(JGitText.get().URINotSupported, remote));
	}

	/**
	 * Convert push remote refs update specification from {@link RefSpec} form
	 * to {@link RemoteRefUpdate}. Conversion expands wildcards by matching
	 * source part to local refs. expectedOldObjectId in RemoteRefUpdate is
	 * always set as null. Tracking branch is configured if RefSpec destination
	 * matches source of any fetch ref spec for this transport remote
	 * configuration.
	 *
	 * @param db
	 *            local database.
	 * @param specs
	 *            collection of RefSpec to convert.
	 * @param fetchSpecs
	 *            fetch specifications used for finding localtracking refs. May
	 *            be null or empty collection.
	 * @return collection of set up {@link RemoteRefUpdate}.
	 * @throws IOException
	 *             when problem occurred during conversion or specification set
	 *             up: most probably, missing objects or refs.
	 */
	public static Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
			final Repository db, final Collection<RefSpec> specs,
			Collection<RefSpec> fetchSpecs) throws IOException {
		if (fetchSpecs == null)
			fetchSpecs = Collections.emptyList();
		final List<RemoteRefUpdate> result = new LinkedList<RemoteRefUpdate>();
		final Collection<RefSpec> procRefs = expandPushWildcardsFor(db, specs);

		for (final RefSpec spec : procRefs) {
			String srcSpec = spec.getSource();
			final Ref srcRef = db.getRef(srcSpec);
			if (srcRef != null)
				srcSpec = srcRef.getName();

			String destSpec = spec.getDestination();
			if (destSpec == null) {
				// No destination (no-colon in ref-spec), DWIMery assumes src
				//
				destSpec = srcSpec;
			}

			if (srcRef != null && !destSpec.startsWith(Constants.R_REFS)) {
				// Assume the same kind of ref at the destination, e.g.
				// "refs/heads/foo:master", DWIMery assumes master is also
				// under "refs/heads/".
				//
				final String n = srcRef.getName();
				final int kindEnd = n.indexOf('/', Constants.R_REFS.length());
				destSpec = n.substring(0, kindEnd + 1) + destSpec;
			}

			final boolean forceUpdate = spec.isForceUpdate();
			final String localName = findTrackingRefName(destSpec, fetchSpecs);
			final RemoteRefUpdate rru = new RemoteRefUpdate(db, srcSpec,
					destSpec, forceUpdate, localName, null);
			result.add(rru);
		}
		return result;
	}

	private static Collection<RefSpec> expandPushWildcardsFor(
			final Repository db, final Collection<RefSpec> specs) {
		final Map<String, Ref> localRefs = db.getAllRefs();
		final Collection<RefSpec> procRefs = new HashSet<RefSpec>();

		for (final RefSpec spec : specs) {
			if (spec.isWildcard()) {
				for (final Ref localRef : localRefs.values()) {
					if (spec.matchSource(localRef))
						procRefs.add(spec.expandFromSource(localRef));
				}
			} else {
				procRefs.add(spec);
			}
		}
		return procRefs;
	}

	private static String findTrackingRefName(final String remoteName,
			final Collection<RefSpec> fetchSpecs) {
		// try to find matching tracking refs
		for (final RefSpec fetchSpec : fetchSpecs) {
			if (fetchSpec.matchSource(remoteName)) {
				if (fetchSpec.isWildcard())
					return fetchSpec.expandFromSource(remoteName)
							.getDestination();
				else
					return fetchSpec.getDestination();
			}
		}
		return null;
	}

	/**
	 * Default setting for {@link #fetchThin} option.
	 */
	public static final boolean DEFAULT_FETCH_THIN = true;

	/**
	 * Default setting for {@link #pushThin} option.
	 */
	public static final boolean DEFAULT_PUSH_THIN = false;

	/**
	 * Specification for fetch or push operations, to fetch or push all tags.
	 * Acts as --tags.
	 */
	public static final RefSpec REFSPEC_TAGS = new RefSpec(
			"refs/tags/*:refs/tags/*");

	/**
	 * Specification for push operation, to push all refs under refs/heads. Acts
	 * as --all.
	 */
	public static final RefSpec REFSPEC_PUSH_ALL = new RefSpec(
			"refs/heads/*:refs/heads/*");

	/** The repository this transport fetches into, or pushes out of. */
	protected final Repository local;

	/** The URI used to create this transport. */
	protected final URIish uri;

	/** Name of the upload pack program, if it must be executed. */
	private String optionUploadPack = RemoteConfig.DEFAULT_UPLOAD_PACK;

	/** Specifications to apply during fetch. */
	private List<RefSpec> fetch = Collections.emptyList();

	/**
	 * How {@link #fetch(ProgressMonitor, Collection)} should handle tags.
	 * <p>
	 * We default to {@link TagOpt#NO_TAGS} so as to avoid fetching annotated
	 * tags during one-shot fetches used for later merges. This prevents
	 * dragging down tags from repositories that we do not have established
	 * tracking branches for. If we do not track the source repository, we most
	 * likely do not care about any tags it publishes.
	 */
	private TagOpt tagopt = TagOpt.NO_TAGS;

	/** Should fetch request thin-pack if remote repository can produce it. */
	private boolean fetchThin = DEFAULT_FETCH_THIN;

	/** Name of the receive pack program, if it must be executed. */
	private String optionReceivePack = RemoteConfig.DEFAULT_RECEIVE_PACK;

	/** Specifications to apply during push. */
	private List<RefSpec> push = Collections.emptyList();

	/** Should push produce thin-pack when sending objects to remote repository. */
	private boolean pushThin = DEFAULT_PUSH_THIN;

	/** Should push just check for operation result, not really push. */
	private boolean dryRun;

	/** Should an incoming (fetch) transfer validate objects? */
	private boolean checkFetchedObjects;

	/** Should refs no longer on the source be pruned from the destination? */
	private boolean removeDeletedRefs;

	/** Timeout in seconds to wait before aborting an IO read or write. */
	private int timeout;

	/** Pack configuration used by this transport to make pack file. */
	private PackConfig packConfig;

	/**
	 * Create a new transport instance.
	 *
	 * @param local
	 *            the repository this instance will fetch into, or push out of.
	 *            This must be the repository passed to
	 *            {@link #open(Repository, URIish)}.
	 * @param uri
	 *            the URI used to access the remote repository. This must be the
	 *            URI passed to {@link #open(Repository, URIish)}.
	 */
	protected Transport(final Repository local, final URIish uri) {
		final TransferConfig tc = local.getConfig().get(TransferConfig.KEY);
		this.local = local;
		this.uri = uri;
		this.checkFetchedObjects = tc.isFsckObjects();
	}

	/**
	 * Get the URI this transport connects to.
	 * <p>
	 * Each transport instance connects to at most one URI at any point in time.
	 *
	 * @return the URI describing the location of the remote repository.
	 */
	public URIish getURI() {
		return uri;
	}

	/**
	 * Get the name of the remote executable providing upload-pack service.
	 *
	 * @return typically "git-upload-pack".
	 */
	public String getOptionUploadPack() {
		return optionUploadPack;
	}

	/**
	 * Set the name of the remote executable providing upload-pack services.
	 *
	 * @param where
	 *            name of the executable.
	 */
	public void setOptionUploadPack(final String where) {
		if (where != null && where.length() > 0)
			optionUploadPack = where;
		else
			optionUploadPack = RemoteConfig.DEFAULT_UPLOAD_PACK;
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
	public void setTagOpt(final TagOpt option) {
		tagopt = option != null ? option : TagOpt.AUTO_FOLLOW;
	}

	/**
	 * Default setting is: {@link #DEFAULT_FETCH_THIN}
	 *
	 * @return true if fetch should request thin-pack when possible; false
	 *         otherwise
	 * @see PackTransport
	 */
	public boolean isFetchThin() {
		return fetchThin;
	}

	/**
	 * Set the thin-pack preference for fetch operation. Default setting is:
	 * {@link #DEFAULT_FETCH_THIN}
	 *
	 * @param fetchThin
	 *            true when fetch should request thin-pack when possible; false
	 *            when it shouldn't
	 * @see PackTransport
	 */
	public void setFetchThin(final boolean fetchThin) {
		this.fetchThin = fetchThin;
	}

	/**
	 * @return true if fetch will verify received objects are formatted
	 *         correctly. Validating objects requires more CPU time on the
	 *         client side of the connection.
	 */
	public boolean isCheckFetchedObjects() {
		return checkFetchedObjects;
	}

	/**
	 * @param check
	 *            true to enable checking received objects; false to assume all
	 *            received objects are valid.
	 */
	public void setCheckFetchedObjects(final boolean check) {
		checkFetchedObjects = check;
	}

	/**
	 * Default setting is: {@link RemoteConfig#DEFAULT_RECEIVE_PACK}
	 *
	 * @return remote executable providing receive-pack service for pack
	 *         transports.
	 * @see PackTransport
	 */
	public String getOptionReceivePack() {
		return optionReceivePack;
	}

	/**
	 * Set remote executable providing receive-pack service for pack transports.
	 * Default setting is: {@link RemoteConfig#DEFAULT_RECEIVE_PACK}
	 *
	 * @param optionReceivePack
	 *            remote executable, if null or empty default one is set;
	 */
	public void setOptionReceivePack(String optionReceivePack) {
		if (optionReceivePack != null && optionReceivePack.length() > 0)
			this.optionReceivePack = optionReceivePack;
		else
			this.optionReceivePack = RemoteConfig.DEFAULT_RECEIVE_PACK;
	}

	/**
	 * Default setting is: {@value #DEFAULT_PUSH_THIN}
	 *
	 * @return true if push should produce thin-pack in pack transports
	 * @see PackTransport
	 */
	public boolean isPushThin() {
		return pushThin;
	}

	/**
	 * Set thin-pack preference for push operation. Default setting is:
	 * {@value #DEFAULT_PUSH_THIN}
	 *
	 * @param pushThin
	 *            true when push should produce thin-pack in pack transports;
	 *            false when it shouldn't
	 * @see PackTransport
	 */
	public void setPushThin(final boolean pushThin) {
		this.pushThin = pushThin;
	}

	/**
	 * @return true if destination refs should be removed if they no longer
	 *         exist at the source repository.
	 */
	public boolean isRemoveDeletedRefs() {
		return removeDeletedRefs;
	}

	/**
	 * Set whether or not to remove refs which no longer exist in the source.
	 * <p>
	 * If true, refs at the destination repository (local for fetch, remote for
	 * push) are deleted if they no longer exist on the source side (remote for
	 * fetch, local for push).
	 * <p>
	 * False by default, as this may cause data to become unreachable, and
	 * eventually be deleted on the next GC.
	 *
	 * @param remove true to remove refs that no longer exist.
	 */
	public void setRemoveDeletedRefs(final boolean remove) {
		removeDeletedRefs = remove;
	}

	/**
	 * Apply provided remote configuration on this transport.
	 *
	 * @param cfg
	 *            configuration to apply on this transport.
	 */
	public void applyConfig(final RemoteConfig cfg) {
		setOptionUploadPack(cfg.getUploadPack());
		setOptionReceivePack(cfg.getReceivePack());
		setTagOpt(cfg.getTagOpt());
		fetch = cfg.getFetchRefSpecs();
		push = cfg.getPushRefSpecs();
		timeout = cfg.getTimeout();
	}

	/**
	 * @return true if push operation should just check for possible result and
	 *         not really update remote refs, false otherwise - when push should
	 *         act normally.
	 */
	public boolean isDryRun() {
		return dryRun;
	}

	/**
	 * Set dry run option for push operation.
	 *
	 * @param dryRun
	 *            true if push operation should just check for possible result
	 *            and not really update remote refs, false otherwise - when push
	 *            should act normally.
	 */
	public void setDryRun(final boolean dryRun) {
		this.dryRun = dryRun;
	}

	/** @return timeout (in seconds) before aborting an IO operation. */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the timeout before willing to abort an IO call.
	 *
	 * @param seconds
	 *            number of seconds to wait (with no data transfer occurring)
	 *            before aborting an IO read or write operation with this
	 *            remote.
	 */
	public void setTimeout(final int seconds) {
		timeout = seconds;
	}

	/**
	 * Get the configuration used by the pack generator to make packs.
	 *
	 * If {@link #setPackConfig(PackConfig)} was previously given null a new
	 * PackConfig is created on demand by this method using the source
	 * repository's settings.
	 *
	 * @return the pack configuration. Never null.
	 */
	public PackConfig getPackConfig() {
		if (packConfig == null)
			packConfig = new PackConfig(local);
		return packConfig;
	}

	/**
	 * Set the configuration used by the pack generator.
	 *
	 * @param pc
	 *            configuration controlling packing parameters. If null the
	 *            source repository's settings will be used.
	 */
	public void setPackConfig(PackConfig pc) {
		packConfig = pc;
	}

	/**
	 * Fetch objects and refs from the remote repository to the local one.
	 * <p>
	 * This is a utility function providing standard fetch behavior. Local
	 * tracking refs associated with the remote repository are automatically
	 * updated if this transport was created from a {@link RemoteConfig} with
	 * fetch RefSpecs defined.
	 *
	 * @param monitor
	 *            progress monitor to inform the user about our processing
	 *            activity. Must not be null. Use {@link NullProgressMonitor} if
	 *            progress updates are not interesting or necessary.
	 * @param toFetch
	 *            specification of refs to fetch locally. May be null or the
	 *            empty collection to use the specifications from the
	 *            RemoteConfig. Source for each RefSpec can't be null.
	 * @return information describing the tracking refs updated.
	 * @throws NotSupportedException
	 *             this transport implementation does not support fetching
	 *             objects.
	 * @throws TransportException
	 *             the remote connection could not be established or object
	 *             copying (if necessary) failed or update specification was
	 *             incorrect.
	 */
	public FetchResult fetch(final ProgressMonitor monitor,
			Collection<RefSpec> toFetch) throws NotSupportedException,
			TransportException {
		if (toFetch == null || toFetch.isEmpty()) {
			// If the caller did not ask for anything use the defaults.
			//
			if (fetch.isEmpty())
				throw new TransportException(JGitText.get().nothingToFetch);
			toFetch = fetch;
		} else if (!fetch.isEmpty()) {
			// If the caller asked for something specific without giving
			// us the local tracking branch see if we can update any of
			// the local tracking branches without incurring additional
			// object transfer overheads.
			//
			final Collection<RefSpec> tmp = new ArrayList<RefSpec>(toFetch);
			for (final RefSpec requested : toFetch) {
				final String reqSrc = requested.getSource();
				for (final RefSpec configured : fetch) {
					final String cfgSrc = configured.getSource();
					final String cfgDst = configured.getDestination();
					if (cfgSrc.equals(reqSrc) && cfgDst != null) {
						tmp.add(configured);
						break;
					}
				}
			}
			toFetch = tmp;
		}

		final FetchResult result = new FetchResult();
		new FetchProcess(this, toFetch).execute(monitor, result);
		return result;
	}

	/**
	 * Push objects and refs from the local repository to the remote one.
	 * <p>
	 * This is a utility function providing standard push behavior. It updates
	 * remote refs and send there necessary objects according to remote ref
	 * update specification. After successful remote ref update, associated
	 * locally stored tracking branch is updated if set up accordingly. Detailed
	 * operation result is provided after execution.
	 * <p>
	 * For setting up remote ref update specification from ref spec, see helper
	 * method {@link #findRemoteRefUpdatesFor(Collection)}, predefined refspecs
	 * ({@link #REFSPEC_TAGS}, {@link #REFSPEC_PUSH_ALL}) or consider using
	 * directly {@link RemoteRefUpdate} for more possibilities.
	 * <p>
	 * When {@link #isDryRun()} is true, result of this operation is just
	 * estimation of real operation result, no real action is performed.
	 *
	 * @see RemoteRefUpdate
	 *
	 * @param monitor
	 *            progress monitor to inform the user about our processing
	 *            activity. Must not be null. Use {@link NullProgressMonitor} if
	 *            progress updates are not interesting or necessary.
	 * @param toPush
	 *            specification of refs to push. May be null or the empty
	 *            collection to use the specifications from the RemoteConfig
	 *            converted by {@link #findRemoteRefUpdatesFor(Collection)}. No
	 *            more than 1 RemoteRefUpdate with the same remoteName is
	 *            allowed. These objects are modified during this call.
	 * @return information about results of remote refs updates, tracking refs
	 *         updates and refs advertised by remote repository.
	 * @throws NotSupportedException
	 *             this transport implementation does not support pushing
	 *             objects.
	 * @throws TransportException
	 *             the remote connection could not be established or object
	 *             copying (if necessary) failed at I/O or protocol level or
	 *             update specification was incorrect.
	 */
	public PushResult push(final ProgressMonitor monitor,
			Collection<RemoteRefUpdate> toPush) throws NotSupportedException,
			TransportException {
		if (toPush == null || toPush.isEmpty()) {
			// If the caller did not ask for anything use the defaults.
			try {
				toPush = findRemoteRefUpdatesFor(push);
			} catch (final IOException e) {
				throw new TransportException(MessageFormat.format(
						JGitText.get().problemWithResolvingPushRefSpecsLocally, e.getMessage()), e);
			}
			if (toPush.isEmpty())
				throw new TransportException(JGitText.get().nothingToPush);
		}
		final PushProcess pushProcess = new PushProcess(this, toPush);
		return pushProcess.execute(monitor);
	}

	/**
	 * Convert push remote refs update specification from {@link RefSpec} form
	 * to {@link RemoteRefUpdate}. Conversion expands wildcards by matching
	 * source part to local refs. expectedOldObjectId in RemoteRefUpdate is
	 * always set as null. Tracking branch is configured if RefSpec destination
	 * matches source of any fetch ref spec for this transport remote
	 * configuration.
	 * <p>
	 * Conversion is performed for context of this transport (database, fetch
	 * specifications).
	 *
	 * @param specs
	 *            collection of RefSpec to convert.
	 * @return collection of set up {@link RemoteRefUpdate}.
	 * @throws IOException
	 *             when problem occurred during conversion or specification set
	 *             up: most probably, missing objects or refs.
	 */
	public Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
			final Collection<RefSpec> specs) throws IOException {
		return findRemoteRefUpdatesFor(local, specs, fetch);
	}

	/**
	 * Begins a new connection for fetching from the remote repository.
	 *
	 * @return a fresh connection to fetch from the remote repository.
	 * @throws NotSupportedException
	 *             the implementation does not support fetching.
	 * @throws TransportException
	 *             the remote connection could not be established.
	 */
	public abstract FetchConnection openFetch() throws NotSupportedException,
			TransportException;

	/**
	 * Begins a new connection for pushing into the remote repository.
	 *
	 * @return a fresh connection to push into the remote repository.
	 * @throws NotSupportedException
	 *             the implementation does not support pushing.
	 * @throws TransportException
	 *             the remote connection could not be established
	 */
	public abstract PushConnection openPush() throws NotSupportedException,
			TransportException;

	/**
	 * Close any resources used by this transport.
	 * <p>
	 * If the remote repository is contacted by a network socket this method
	 * must close that network socket, disconnecting the two peers. If the
	 * remote repository is actually local (same system) this method must close
	 * any open file handles used to read the "remote" repository.
	 */
	public abstract void close();
}
