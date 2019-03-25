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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.hooks.Hooks;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.pack.PackConfig;

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
public abstract class Transport implements AutoCloseable {
	/** Type of operation a Transport is being opened for. */
	public enum Operation {
		/** Transport is to fetch objects locally. */
		FETCH,
		/** Transport is to push objects remotely. */
		PUSH;
	}

	private static final List<WeakReference<TransportProtocol>> protocols =
		new CopyOnWriteArrayList<>();

	static {
		// Registration goes backwards in order of priority.
		register(TransportLocal.PROTO_LOCAL);
		register(TransportBundleFile.PROTO_BUNDLE);
		register(TransportAmazonS3.PROTO_S3);
		register(TransportGitAnon.PROTO_GIT);
		register(TransportSftp.PROTO_SFTP);
		register(TransportHttp.PROTO_FTP);
		register(TransportHttp.PROTO_HTTP);
		register(TransportGitSsh.PROTO_SSH);

		registerByService();
	}

	private static void registerByService() {
		ClassLoader ldr = Thread.currentThread().getContextClassLoader();
		if (ldr == null)
			ldr = Transport.class.getClassLoader();
		Enumeration<URL> catalogs = catalogs(ldr);
		while (catalogs.hasMoreElements())
			scan(ldr, catalogs.nextElement());
	}

	private static Enumeration<URL> catalogs(ClassLoader ldr) {
		try {
			String prefix = "META-INF/services/"; //$NON-NLS-1$
			String name = prefix + Transport.class.getName();
			return ldr.getResources(name);
		} catch (IOException err) {
			return new Vector<URL>().elements();
		}
	}

	private static void scan(ClassLoader ldr, URL url) {
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(url.openStream(), UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0)
					continue;
				int comment = line.indexOf('#');
				if (comment == 0)
					continue;
				if (comment != -1)
					line = line.substring(0, comment).trim();
				load(ldr, line);
			}
		} catch (IOException e) {
			// Ignore errors
		}
	}

	private static void load(ClassLoader ldr, String cn) {
		Class<?> clazz;
		try {
			clazz = Class.forName(cn, false, ldr);
		} catch (ClassNotFoundException notBuiltin) {
			// Doesn't exist, even though the service entry is present.
			//
			return;
		}

		for (Field f : clazz.getDeclaredFields()) {
			if ((f.getModifiers() & Modifier.STATIC) == Modifier.STATIC
					&& TransportProtocol.class.isAssignableFrom(f.getType())) {
				TransportProtocol proto;
				try {
					proto = (TransportProtocol) f.get(null);
				} catch (IllegalArgumentException e) {
					// If we cannot access the field, don't.
					continue;
				} catch (IllegalAccessException e) {
					// If we cannot access the field, don't.
					continue;
				}
				if (proto != null)
					register(proto);
			}
		}
	}

	/**
	 * Register a TransportProtocol instance for use during open.
	 * <p>
	 * Protocol definitions are held by WeakReference, allowing them to be
	 * garbage collected when the calling application drops all strongly held
	 * references to the TransportProtocol. Therefore applications should use a
	 * singleton pattern as described in
	 * {@link org.eclipse.jgit.transport.TransportProtocol}'s class
	 * documentation to ensure their protocol does not get disabled by garbage
	 * collection earlier than expected.
	 * <p>
	 * The new protocol is registered in front of all earlier protocols, giving
	 * it higher priority than the built-in protocol definitions.
	 *
	 * @param proto
	 *            the protocol definition. Must not be null.
	 */
	public static void register(TransportProtocol proto) {
		protocols.add(0, new WeakReference<>(proto));
	}

	/**
	 * Unregister a TransportProtocol instance.
	 * <p>
	 * Unregistering a protocol usually isn't necessary, as protocols are held
	 * by weak references and will automatically clear when they are garbage
	 * collected by the JVM. Matching is handled by reference equality, so the
	 * exact reference given to {@link #register(TransportProtocol)} must be
	 * used.
	 *
	 * @param proto
	 *            the exact object previously given to register.
	 */
	public static void unregister(TransportProtocol proto) {
		for (WeakReference<TransportProtocol> ref : protocols) {
			TransportProtocol refProto = ref.get();
			if (refProto == null || refProto == proto)
				protocols.remove(ref);
		}
	}

	/**
	 * Obtain a copy of the registered protocols.
	 *
	 * @return an immutable copy of the currently registered protocols.
	 */
	public static List<TransportProtocol> getTransportProtocols() {
		int cnt = protocols.size();
		List<TransportProtocol> res = new ArrayList<>(cnt);
		for (WeakReference<TransportProtocol> ref : protocols) {
			TransportProtocol proto = ref.get();
			if (proto != null)
				res.add(proto);
			else
				protocols.remove(ref);
		}
		return Collections.unmodifiableList(res);
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 * <p>
	 * This method assumes
	 * {@link org.eclipse.jgit.transport.Transport.Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository - may be URI or remote
	 *            configuration name.
	 * @return the new transport instance. Never null. In case of multiple URIs
	 *         in remote configuration, only the first is chosen.
	 * @throws java.net.URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static Transport open(Repository local, String remote)
			throws NotSupportedException, URISyntaxException,
			TransportException {
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
	 * @throws java.net.URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static Transport open(final Repository local, final String remote,
			final Operation op) throws NotSupportedException,
			URISyntaxException, TransportException {
		if (local != null) {
			final RemoteConfig cfg = new RemoteConfig(local.getConfig(), remote);
			if (doesNotExist(cfg))
				return open(local, new URIish(remote), null);
			return open(local, cfg, op);
		} else
			return open(new URIish(remote));

	}

	/**
	 * Open new transport instances to connect two repositories.
	 * <p>
	 * This method assumes
	 * {@link org.eclipse.jgit.transport.Transport.Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository - may be URI or remote
	 *            configuration name.
	 * @return the list of new transport instances for every URI in remote
	 *         configuration.
	 * @throws java.net.URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static List<Transport> openAll(final Repository local,
			final String remote) throws NotSupportedException,
			URISyntaxException, TransportException {
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
	 * @throws java.net.URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static List<Transport> openAll(final Repository local,
			final String remote, final Operation op)
			throws NotSupportedException, URISyntaxException,
			TransportException {
		final RemoteConfig cfg = new RemoteConfig(local.getConfig(), remote);
		if (doesNotExist(cfg)) {
			final ArrayList<Transport> transports = new ArrayList<>(1);
			transports.add(open(local, new URIish(remote), null));
			return transports;
		}
		return openAll(local, cfg, op);
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 * <p>
	 * This method assumes
	 * {@link org.eclipse.jgit.transport.Transport.Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param cfg
	 *            configuration describing how to connect to the remote
	 *            repository.
	 * @return the new transport instance. Never null. In case of multiple URIs
	 *         in remote configuration, only the first is chosen.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 * @throws java.lang.IllegalArgumentException
	 *             if provided remote configuration doesn't have any URI
	 *             associated.
	 */
	public static Transport open(Repository local, RemoteConfig cfg)
			throws NotSupportedException, TransportException {
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
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 * @throws java.lang.IllegalArgumentException
	 *             if provided remote configuration doesn't have any URI
	 *             associated.
	 */
	public static Transport open(final Repository local,
			final RemoteConfig cfg, final Operation op)
			throws NotSupportedException, TransportException {
		final List<URIish> uris = getURIs(cfg, op);
		if (uris.isEmpty())
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().remoteConfigHasNoURIAssociated, cfg.getName()));
		final Transport tn = open(local, uris.get(0), cfg.getName());
		tn.applyConfig(cfg);
		return tn;
	}

	/**
	 * Open new transport instances to connect two repositories.
	 * <p>
	 * This method assumes
	 * {@link org.eclipse.jgit.transport.Transport.Operation#FETCH}.
	 *
	 * @param local
	 *            existing local repository.
	 * @param cfg
	 *            configuration describing how to connect to the remote
	 *            repository.
	 * @return the list of new transport instances for every URI in remote
	 *         configuration.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static List<Transport> openAll(final Repository local,
			final RemoteConfig cfg) throws NotSupportedException,
			TransportException {
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
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static List<Transport> openAll(final Repository local,
			final RemoteConfig cfg, final Operation op)
			throws NotSupportedException, TransportException {
		final List<URIish> uris = getURIs(cfg, op);
		final List<Transport> transports = new ArrayList<>(uris.size());
		for (URIish uri : uris) {
			final Transport tn = open(local, uri, cfg.getName());
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

	private static boolean doesNotExist(RemoteConfig cfg) {
		return cfg.getURIs().isEmpty() && cfg.getPushURIs().isEmpty();
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 *
	 * @param local
	 *            existing local repository.
	 * @param uri
	 *            location of the remote repository.
	 * @return the new transport instance. Never null.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static Transport open(Repository local, URIish uri)
			throws NotSupportedException, TransportException {
		return open(local, uri, null);
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 *
	 * @param local
	 *            existing local repository.
	 * @param uri
	 *            location of the remote repository.
	 * @param remoteName
	 *            name of the remote, if the remote as configured in
	 *            {@code local}; otherwise null.
	 * @return the new transport instance. Never null.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the transport cannot open this URI.
	 */
	public static Transport open(Repository local, URIish uri, String remoteName)
			throws NotSupportedException, TransportException {
		for (WeakReference<TransportProtocol> ref : protocols) {
			TransportProtocol proto = ref.get();
			if (proto == null) {
				protocols.remove(ref);
				continue;
			}

			if (proto.canHandle(uri, local, remoteName)) {
				Transport tn = proto.open(uri, local, remoteName);
				tn.prePush = Hooks.prePush(local, tn.hookOutRedirect);
				tn.prePush.setRemoteLocation(uri.toString());
				tn.prePush.setRemoteName(remoteName);
				return tn;
			}
		}

		throw new NotSupportedException(MessageFormat.format(JGitText.get().URINotSupported, uri));
	}

	/**
	 * Open a new transport with no local repository.
	 * <p>
	 * Note that the resulting transport instance can not be used for fetching
	 * or pushing, but only for reading remote refs.
	 *
	 * @param uri a {@link org.eclipse.jgit.transport.URIish} object.
	 * @return new Transport instance
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 * @throws org.eclipse.jgit.errors.TransportException
	 */
	public static Transport open(URIish uri) throws NotSupportedException, TransportException {
		for (WeakReference<TransportProtocol> ref : protocols) {
			TransportProtocol proto = ref.get();
			if (proto == null) {
				protocols.remove(ref);
				continue;
			}

			if (proto.canHandle(uri, null, null))
				return proto.open(uri);
		}

		throw new NotSupportedException(MessageFormat.format(JGitText.get().URINotSupported, uri));
	}

	/**
	 * Convert push remote refs update specification from
	 * {@link org.eclipse.jgit.transport.RefSpec} form to
	 * {@link org.eclipse.jgit.transport.RemoteRefUpdate}. Conversion expands
	 * wildcards by matching source part to local refs. expectedOldObjectId in
	 * RemoteRefUpdate is set when specified in leases. Tracking branch is
	 * configured if RefSpec destination matches source of any fetch ref spec
	 * for this transport remote configuration.
	 *
	 * @param db
	 *            local database.
	 * @param specs
	 *            collection of RefSpec to convert.
	 * @param leases
	 *            map from ref to lease (containing expected old object id)
	 * @param fetchSpecs
	 *            fetch specifications used for finding localtracking refs. May
	 *            be null or empty collection.
	 * @return collection of set up
	 *         {@link org.eclipse.jgit.transport.RemoteRefUpdate}.
	 * @throws java.io.IOException
	 *             when problem occurred during conversion or specification set
	 *             up: most probably, missing objects or refs.
	 * @since 4.7
	 */
	public static Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
			final Repository db, final Collection<RefSpec> specs,
			final Map<String, RefLeaseSpec> leases,
			Collection<RefSpec> fetchSpecs) throws IOException {
		if (fetchSpecs == null)
			fetchSpecs = Collections.emptyList();
		final List<RemoteRefUpdate> result = new LinkedList<>();
		final Collection<RefSpec> procRefs = expandPushWildcardsFor(db, specs);

		for (RefSpec spec : procRefs) {
			String srcSpec = spec.getSource();
			final Ref srcRef = db.findRef(srcSpec);
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
			final RefLeaseSpec leaseSpec = leases.get(destSpec);
			final ObjectId expected = leaseSpec == null ? null :
				db.resolve(leaseSpec.getExpected());
			final RemoteRefUpdate rru = new RemoteRefUpdate(db, srcSpec,
					destSpec, forceUpdate, localName, expected);
			result.add(rru);
		}
		return result;
	}

	/**
	 * Convert push remote refs update specification from
	 * {@link org.eclipse.jgit.transport.RefSpec} form to
	 * {@link org.eclipse.jgit.transport.RemoteRefUpdate}. Conversion expands
	 * wildcards by matching source part to local refs. expectedOldObjectId in
	 * RemoteRefUpdate is always set as null. Tracking branch is configured if
	 * RefSpec destination matches source of any fetch ref spec for this
	 * transport remote configuration.
	 *
	 * @param db
	 *            local database.
	 * @param specs
	 *            collection of RefSpec to convert.
	 * @param fetchSpecs
	 *            fetch specifications used for finding localtracking refs. May
	 *            be null or empty collection.
	 * @return collection of set up
	 *         {@link org.eclipse.jgit.transport.RemoteRefUpdate}.
	 * @throws java.io.IOException
	 *             when problem occurred during conversion or specification set
	 *             up: most probably, missing objects or refs.
	 */
	public static Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
			final Repository db, final Collection<RefSpec> specs,
			Collection<RefSpec> fetchSpecs) throws IOException {
		return findRemoteRefUpdatesFor(db, specs, Collections.emptyMap(),
					       fetchSpecs);
	}

	private static Collection<RefSpec> expandPushWildcardsFor(
			final Repository db, final Collection<RefSpec> specs)
			throws IOException {
		final List<Ref> localRefs = db.getRefDatabase().getRefs();
		final Collection<RefSpec> procRefs = new LinkedHashSet<>();

		for (RefSpec spec : specs) {
			if (spec.isWildcard()) {
				for (Ref localRef : localRefs) {
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
		for (RefSpec fetchSpec : fetchSpecs) {
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
			"refs/tags/*:refs/tags/*"); //$NON-NLS-1$

	/**
	 * Specification for push operation, to push all refs under refs/heads. Acts
	 * as --all.
	 */
	public static final RefSpec REFSPEC_PUSH_ALL = new RefSpec(
			"refs/heads/*:refs/heads/*"); //$NON-NLS-1$

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

	/** Should push be all-or-nothing atomic behavior? */
	private boolean pushAtomic;

	/** Should push just check for operation result, not really push. */
	private boolean dryRun;

	/** Should an incoming (fetch) transfer validate objects? */
	private ObjectChecker objectChecker;

	/** Should refs no longer on the source be pruned from the destination? */
	private boolean removeDeletedRefs;

	private FilterSpec filterSpec = FilterSpec.NO_FILTER;

	/** Timeout in seconds to wait before aborting an IO read or write. */
	private int timeout;

	/** Pack configuration used by this transport to make pack file. */
	private PackConfig packConfig;

	/** Assists with authentication the connection. */
	private CredentialsProvider credentialsProvider;

	/** The option strings associated with the push operation. */
	private List<String> pushOptions;

	private PrintStream hookOutRedirect;

	private PrePushHook prePush;
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
	protected Transport(Repository local, URIish uri) {
		final TransferConfig tc = local.getConfig().get(TransferConfig.KEY);
		this.local = local;
		this.uri = uri;
		this.objectChecker = tc.newObjectChecker();
		this.credentialsProvider = CredentialsProvider.getDefault();
		prePush = Hooks.prePush(local, hookOutRedirect);
	}

	/**
	 * Create a minimal transport instance not tied to a single repository.
	 *
	 * @param uri
	 *            a {@link org.eclipse.jgit.transport.URIish} object.
	 */
	protected Transport(URIish uri) {
		this.uri = uri;
		this.local = null;
		this.objectChecker = new ObjectChecker();
		this.credentialsProvider = CredentialsProvider.getDefault();
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
	public void setOptionUploadPack(String where) {
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
	public void setTagOpt(TagOpt option) {
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
	public void setFetchThin(boolean fetchThin) {
		this.fetchThin = fetchThin;
	}

	/**
	 * Whether fetch will verify if received objects are formatted correctly.
	 *
	 * @return true if fetch will verify received objects are formatted
	 *         correctly. Validating objects requires more CPU time on the
	 *         client side of the connection.
	 */
	public boolean isCheckFetchedObjects() {
		return getObjectChecker() != null;
	}

	/**
	 * Configure if checking received objects is enabled
	 *
	 * @param check
	 *            true to enable checking received objects; false to assume all
	 *            received objects are valid.
	 * @see #setObjectChecker(ObjectChecker)
	 */
	public void setCheckFetchedObjects(boolean check) {
		if (check && objectChecker == null)
			setObjectChecker(new ObjectChecker());
		else if (!check && objectChecker != null)
			setObjectChecker(null);
	}

	/**
	 * Get configured object checker for received objects
	 *
	 * @return configured object checker for received objects, or null.
	 * @since 3.6
	 */
	public ObjectChecker getObjectChecker() {
		return objectChecker;
	}

	/**
	 * Set the object checker to verify each received object with
	 *
	 * @param impl
	 *            if non-null the object checking instance to verify each
	 *            received object with; null to disable object checking.
	 * @since 3.6
	 */
	public void setObjectChecker(ObjectChecker impl) {
		objectChecker = impl;
	}

	/**
	 * Default setting is:
	 * {@link org.eclipse.jgit.transport.RemoteConfig#DEFAULT_RECEIVE_PACK}
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
	 * Default setting is:
	 * {@link org.eclipse.jgit.transport.RemoteConfig#DEFAULT_RECEIVE_PACK}
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
	public void setPushThin(boolean pushThin) {
		this.pushThin = pushThin;
	}

	/**
	 * Default setting is false.
	 *
	 * @return true if push requires all-or-nothing atomic behavior.
	 * @since 4.2
	 */
	public boolean isPushAtomic() {
		return pushAtomic;
	}

	/**
	 * Request atomic push (all references succeed, or none do).
	 * <p>
	 * Server must also support atomic push. If the server does not support the
	 * feature the push will abort without making changes.
	 *
	 * @param atomic
	 *            true when push should be an all-or-nothing operation.
	 * @see PackTransport
	 * @since 4.2
	 */
	public void setPushAtomic(boolean atomic) {
		this.pushAtomic = atomic;
	}

	/**
	 * Whether destination refs should be removed if they no longer exist at the
	 * source repository.
	 *
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
	public void setRemoveDeletedRefs(boolean remove) {
		removeDeletedRefs = remove;
	}

	/**
	 * @return the blob limit value set with {@link #setFilterBlobLimit} or
	 *         {@link #setFilterSpec(FilterSpec)}, or -1 if no blob limit value
	 *         was set
	 * @since 5.0
	 * @deprecated Use {@link #getFilterSpec()} instead
	 */
	@Deprecated
	public long getFilterBlobLimit() {
		return filterSpec.getBlobLimit();
	}

	/**
	 * @param bytes exclude blobs of size greater than this
	 * @since 5.0
	 * @deprecated Use {@link #setFilterSpec(FilterSpec)} instead
	 */
	@Deprecated
	public void setFilterBlobLimit(long bytes) {
		setFilterSpec(FilterSpec.withBlobLimit(bytes));
	}

	/**
	 * @return the last filter spec set with {@link #setFilterSpec(FilterSpec)},
	 *         or {@link FilterSpec#NO_FILTER} if it was never invoked.
	 * @since 5.4
	 */
	public final FilterSpec getFilterSpec() {
		return filterSpec;
	}

	/**
	 * @param filter a new filter to use for this transport
	 * @since 5.4
	 */
	public final void setFilterSpec(@NonNull FilterSpec filter) {
		filterSpec = requireNonNull(filter);
	}

	/**
	 * Apply provided remote configuration on this transport.
	 *
	 * @param cfg
	 *            configuration to apply on this transport.
	 */
	public void applyConfig(RemoteConfig cfg) {
		setOptionUploadPack(cfg.getUploadPack());
		setOptionReceivePack(cfg.getReceivePack());
		setTagOpt(cfg.getTagOpt());
		fetch = cfg.getFetchRefSpecs();
		push = cfg.getPushRefSpecs();
		timeout = cfg.getTimeout();
	}

	/**
	 * Whether push operation should just check for possible result and not
	 * really update remote refs
	 *
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
	public void setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
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
	 *            remote.
	 */
	public void setTimeout(int seconds) {
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
	 * A credentials provider to assist with authentication connections..
	 *
	 * @param credentialsProvider
	 *            the credentials provider, or null if there is none
	 */
	public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
	}

	/**
	 * The configured credentials provider.
	 *
	 * @return the credentials provider, or null if no credentials provider is
	 *         associated with this transport.
	 */
	public CredentialsProvider getCredentialsProvider() {
		return credentialsProvider;
	}

	/**
	 * Get the option strings associated with the push operation
	 *
	 * @return the option strings associated with the push operation
	 * @since 4.5
	 */
	public List<String> getPushOptions() {
		return pushOptions;
	}

	/**
	 * Sets the option strings associated with the push operation.
	 *
	 * @param pushOptions
	 *            null if push options are unsupported
	 * @since 4.5
	 */
	public void setPushOptions(List<String> pushOptions) {
		this.pushOptions = pushOptions;
	}

	/**
	 * Fetch objects and refs from the remote repository to the local one.
	 * <p>
	 * This is a utility function providing standard fetch behavior. Local
	 * tracking refs associated with the remote repository are automatically
	 * updated if this transport was created from a
	 * {@link org.eclipse.jgit.transport.RemoteConfig} with fetch RefSpecs
	 * defined.
	 *
	 * @param monitor
	 *            progress monitor to inform the user about our processing
	 *            activity. Must not be null. Use
	 *            {@link org.eclipse.jgit.lib.NullProgressMonitor} if progress
	 *            updates are not interesting or necessary.
	 * @param toFetch
	 *            specification of refs to fetch locally. May be null or the
	 *            empty collection to use the specifications from the
	 *            RemoteConfig. Source for each RefSpec can't be null.
	 * @return information describing the tracking refs updated.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             this transport implementation does not support fetching
	 *             objects.
	 * @throws org.eclipse.jgit.errors.TransportException
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
			final Collection<RefSpec> tmp = new ArrayList<>(toFetch);
			for (RefSpec requested : toFetch) {
				final String reqSrc = requested.getSource();
				for (RefSpec configured : fetch) {
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

		local.autoGC(monitor);

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
	 * directly {@link org.eclipse.jgit.transport.RemoteRefUpdate} for more
	 * possibilities.
	 * <p>
	 * When {@link #isDryRun()} is true, result of this operation is just
	 * estimation of real operation result, no real action is performed.
	 *
	 * @see RemoteRefUpdate
	 * @param monitor
	 *            progress monitor to inform the user about our processing
	 *            activity. Must not be null. Use
	 *            {@link org.eclipse.jgit.lib.NullProgressMonitor} if progress
	 *            updates are not interesting or necessary.
	 * @param toPush
	 *            specification of refs to push. May be null or the empty
	 *            collection to use the specifications from the RemoteConfig
	 *            converted by {@link #findRemoteRefUpdatesFor(Collection)}. No
	 *            more than 1 RemoteRefUpdate with the same remoteName is
	 *            allowed. These objects are modified during this call.
	 * @param out
	 *            output stream to write messages to
	 * @return information about results of remote refs updates, tracking refs
	 *         updates and refs advertised by remote repository.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             this transport implementation does not support pushing
	 *             objects.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the remote connection could not be established or object
	 *             copying (if necessary) failed at I/O or protocol level or
	 *             update specification was incorrect.
	 * @since 3.0
	 */
	public PushResult push(final ProgressMonitor monitor,
			Collection<RemoteRefUpdate> toPush, OutputStream out)
			throws NotSupportedException,
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
		if (prePush != null) {
			try {
				prePush.setRefs(toPush);
				prePush.call();
			} catch (AbortedByHookException | IOException e) {
				throw new TransportException(e.getMessage(), e);
			}
		}

		final PushProcess pushProcess = new PushProcess(this, toPush, out);
		return pushProcess.execute(monitor);
	}

	/**
	 * Push objects and refs from the local repository to the remote one.
	 * <p>
	 * This is a utility function providing standard push behavior. It updates
	 * remote refs and sends necessary objects according to remote ref update
	 * specification. After successful remote ref update, associated locally
	 * stored tracking branch is updated if set up accordingly. Detailed
	 * operation result is provided after execution.
	 * <p>
	 * For setting up remote ref update specification from ref spec, see helper
	 * method {@link #findRemoteRefUpdatesFor(Collection)}, predefined refspecs
	 * ({@link #REFSPEC_TAGS}, {@link #REFSPEC_PUSH_ALL}) or consider using
	 * directly {@link org.eclipse.jgit.transport.RemoteRefUpdate} for more
	 * possibilities.
	 * <p>
	 * When {@link #isDryRun()} is true, result of this operation is just
	 * estimation of real operation result, no real action is performed.
	 *
	 * @see RemoteRefUpdate
	 * @param monitor
	 *            progress monitor to inform the user about our processing
	 *            activity. Must not be null. Use
	 *            {@link org.eclipse.jgit.lib.NullProgressMonitor} if progress
	 *            updates are not interesting or necessary.
	 * @param toPush
	 *            specification of refs to push. May be null or the empty
	 *            collection to use the specifications from the RemoteConfig
	 *            converted by {@link #findRemoteRefUpdatesFor(Collection)}. No
	 *            more than 1 RemoteRefUpdate with the same remoteName is
	 *            allowed. These objects are modified during this call.
	 * @return information about results of remote refs updates, tracking refs
	 *         updates and refs advertised by remote repository.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             this transport implementation does not support pushing
	 *             objects.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the remote connection could not be established or object
	 *             copying (if necessary) failed at I/O or protocol level or
	 *             update specification was incorrect.
	 */
	public PushResult push(final ProgressMonitor monitor,
			Collection<RemoteRefUpdate> toPush) throws NotSupportedException,
			TransportException {
		return push(monitor, toPush, null);
	}

	/**
	 * Convert push remote refs update specification from
	 * {@link org.eclipse.jgit.transport.RefSpec} form to
	 * {@link org.eclipse.jgit.transport.RemoteRefUpdate}. Conversion expands
	 * wildcards by matching source part to local refs. expectedOldObjectId in
	 * RemoteRefUpdate is always set as null. Tracking branch is configured if
	 * RefSpec destination matches source of any fetch ref spec for this
	 * transport remote configuration.
	 * <p>
	 * Conversion is performed for context of this transport (database, fetch
	 * specifications).
	 *
	 * @param specs
	 *            collection of RefSpec to convert.
	 * @return collection of set up
	 *         {@link org.eclipse.jgit.transport.RemoteRefUpdate}.
	 * @throws java.io.IOException
	 *             when problem occurred during conversion or specification set
	 *             up: most probably, missing objects or refs.
	 */
	public Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
			final Collection<RefSpec> specs) throws IOException {
		return findRemoteRefUpdatesFor(local, specs, Collections.emptyMap(),
					       fetch);
	}

	/**
	 * Convert push remote refs update specification from
	 * {@link org.eclipse.jgit.transport.RefSpec} form to
	 * {@link org.eclipse.jgit.transport.RemoteRefUpdate}. Conversion expands
	 * wildcards by matching source part to local refs. expectedOldObjectId in
	 * RemoteRefUpdate is set according to leases. Tracking branch is configured
	 * if RefSpec destination matches source of any fetch ref spec for this
	 * transport remote configuration.
	 * <p>
	 * Conversion is performed for context of this transport (database, fetch
	 * specifications).
	 *
	 * @param specs
	 *            collection of RefSpec to convert.
	 * @param leases
	 *            map from ref to lease (containing expected old object id)
	 * @return collection of set up
	 *         {@link org.eclipse.jgit.transport.RemoteRefUpdate}.
	 * @throws java.io.IOException
	 *             when problem occurred during conversion or specification set
	 *             up: most probably, missing objects or refs.
	 * @since 4.7
	 */
	public Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
			final Collection<RefSpec> specs,
			final Map<String, RefLeaseSpec> leases) throws IOException {
		return findRemoteRefUpdatesFor(local, specs, leases,
					       fetch);
	}

	/**
	 * Begins a new connection for fetching from the remote repository.
	 * <p>
	 * If the transport has no local repository, the fetch connection can only
	 * be used for reading remote refs.
	 *
	 * @return a fresh connection to fetch from the remote repository.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the implementation does not support fetching.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the remote connection could not be established.
	 */
	public abstract FetchConnection openFetch() throws NotSupportedException,
			TransportException;

	/**
	 * Begins a new connection for pushing into the remote repository.
	 *
	 * @return a fresh connection to push into the remote repository.
	 * @throws org.eclipse.jgit.errors.NotSupportedException
	 *             the implementation does not support pushing.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the remote connection could not be established
	 */
	public abstract PushConnection openPush() throws NotSupportedException,
			TransportException;

	/**
	 * {@inheritDoc}
	 * <p>
	 * Close any resources used by this transport.
	 * <p>
	 * If the remote repository is contacted by a network socket this method
	 * must close that network socket, disconnecting the two peers. If the
	 * remote repository is actually local (same system) this method must close
	 * any open file handles used to read the "remote" repository.
	 * <p>
	 * {@code AutoClosable.close()} declares that it throws {@link Exception}.
	 * Implementers shouldn't throw checked exceptions. This override narrows
	 * the signature to prevent them from doing so.
	 */
	@Override
	public abstract void close();
}
