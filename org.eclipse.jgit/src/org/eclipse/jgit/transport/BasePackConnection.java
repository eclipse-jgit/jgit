/*
 * Copyright (C) 2008, 2010 Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, 2020 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.COMMAND_LS_REFS;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;
import static org.eclipse.jgit.transport.GitProtocolConstants.REF_ATTR_PEELED;
import static org.eclipse.jgit.transport.GitProtocolConstants.REF_ATTR_SYMREF_TARGET;
import static org.eclipse.jgit.transport.GitProtocolConstants.VERSION_1;
import static org.eclipse.jgit.transport.GitProtocolConstants.VERSION_2;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.RemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.io.InterruptTimer;
import org.eclipse.jgit.util.io.TimeoutInputStream;
import org.eclipse.jgit.util.io.TimeoutOutputStream;

/**
 * Base helper class for pack-based operations implementations. Provides partial
 * implementation of pack-protocol - refs advertising and capabilities support,
 * and some other helper methods.
 *
 * @see BasePackFetchConnection
 * @see BasePackPushConnection
 */
abstract class BasePackConnection extends BaseConnection {

	protected static final String CAPABILITY_SYMREF_PREFIX = "symref="; //$NON-NLS-1$

	/** The repository this transport fetches into, or pushes out of. */
	protected final Repository local;

	/** Remote repository location. */
	protected final URIish uri;

	/** A transport connected to {@link #uri}. */
	protected final Transport transport;

	/** Low-level input stream, if a timeout was configured. */
	protected TimeoutInputStream timeoutIn;

	/** Low-level output stream, if a timeout was configured. */
	protected TimeoutOutputStream timeoutOut;

	/** Timer to manage {@link #timeoutIn} and {@link #timeoutOut}. */
	private InterruptTimer myTimer;

	/** Input stream reading from the remote. */
	protected InputStream in;

	/** Output stream sending to the remote. */
	protected OutputStream out;

	/** Packet line decoder around {@link #in}. */
	protected PacketLineIn pckIn;

	/** Packet line encoder around {@link #out}. */
	protected PacketLineOut pckOut;

	/** Send {@link PacketLineOut#end()} before closing {@link #out}? */
	protected boolean outNeedsEnd;

	/** True if this is a stateless RPC connection. */
	protected boolean statelessRPC;

	/** Capability tokens advertised by the remote side. */
	private final Map<String, String> remoteCapabilities = new HashMap<>();

	/** Extra objects the remote has, but which aren't offered as refs. */
	protected final Set<ObjectId> additionalHaves = new HashSet<>();

	private TransferConfig.ProtocolVersion protocol = TransferConfig.ProtocolVersion.V0;

	BasePackConnection(PackTransport packTransport) {
		transport = (Transport) packTransport;
		local = transport.local;
		uri = transport.uri;
	}

	TransferConfig.ProtocolVersion getProtocolVersion() {
		return protocol;
	}

	void setProtocolVersion(@NonNull TransferConfig.ProtocolVersion protocol) {
		this.protocol = protocol;
	}

	/**
	 * Configure this connection with the directional pipes.
	 *
	 * @param myIn
	 *            input stream to receive data from the peer. Caller must ensure
	 *            the input is buffered, otherwise read performance may suffer.
	 * @param myOut
	 *            output stream to transmit data to the peer. Caller must ensure
	 *            the output is buffered, otherwise write performance may
	 *            suffer.
	 */
	protected final void init(InputStream myIn, OutputStream myOut) {
		final int timeout = transport.getTimeout();
		if (timeout > 0) {
			final Thread caller = Thread.currentThread();
			if (myTimer == null) {
				myTimer = new InterruptTimer(caller.getName() + "-Timer"); //$NON-NLS-1$
			}
			timeoutIn = new TimeoutInputStream(myIn, myTimer);
			timeoutOut = new TimeoutOutputStream(myOut, myTimer);
			timeoutIn.setTimeout(timeout * 1000);
			timeoutOut.setTimeout(timeout * 1000);
			myIn = timeoutIn;
			myOut = timeoutOut;
		}

		in = myIn;
		out = myOut;

		pckIn = new PacketLineIn(in);
		pckOut = new PacketLineOut(out);
		outNeedsEnd = true;
	}

	/**
	 * Reads the advertised references through the initialized stream.
	 * <p>
	 * Subclass implementations may call this method only after setting up the
	 * input and output streams with {@link #init(InputStream, OutputStream)}.
	 * <p>
	 * If any errors occur, this connection is automatically closed by invoking
	 * {@link #close()} and the exception is wrapped (if necessary) and thrown
	 * as a {@link org.eclipse.jgit.errors.TransportException}.
	 *
	 * @return {@code true} if the refs were read; {@code false} otherwise
	 *         indicating that {@link #lsRefs} must be called
	 *
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             the reference list could not be scanned.
	 */
	protected boolean readAdvertisedRefs() throws TransportException {
		try {
			return readAdvertisedRefsImpl();
		} catch (TransportException err) {
			close();
			throw err;
		} catch (IOException | RuntimeException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		}
	}

	private String readLine() throws IOException {
		String line = pckIn.readString();
		if (PacketLineIn.isEnd(line)) {
			return null;
		}
		if (line.startsWith("ERR ")) { //$NON-NLS-1$
			// This is a customized remote service error.
			// Users should be informed about it.
			throw new RemoteRepositoryException(uri, line.substring(4));
		}
		return line;
	}

	private boolean readAdvertisedRefsImpl() throws IOException {
		final Map<String, Ref> avail = new LinkedHashMap<>();
		final Map<String, String> symRefs = new LinkedHashMap<>();
		for (boolean first = true;; first = false) {
			String line;

			if (first) {
				boolean isV1 = false;
				try {
					line = readLine();
				} catch (EOFException e) {
					TransportException noRepo = noRepository();
					if (noRepo.getCause() == null) {
						noRepo.initCause(e);
					}
					else {
						noRepo.addSuppressed(e);
					}
					throw noRepo;
				}
				if (line != null && VERSION_1.equals(line)) {
					// Same as V0, except for this extra line. We shouldn't get
					// it since we never request V1.
					setProtocolVersion(TransferConfig.ProtocolVersion.V0);
					isV1 = true;
					line = readLine();
				}
				if (line == null) {
					break;
				}
				final int nul = line.indexOf('\0');
				if (nul >= 0) {
					// Protocol V0: The first line (if any) may contain
					// "hidden" capability values after a NUL byte.
					for (String capability : line.substring(nul + 1)
							.split(" ")) { //$NON-NLS-1$
						if (capability.startsWith(CAPABILITY_SYMREF_PREFIX)) {
							String[] parts = capability
									.substring(
											CAPABILITY_SYMREF_PREFIX.length())
									.split(":", 2); //$NON-NLS-1$
							if (parts.length == 2) {
								symRefs.put(parts[0], parts[1]);
							}
						} else {
							addCapability(capability);
						}
					}
					line = line.substring(0, nul);
					setProtocolVersion(TransferConfig.ProtocolVersion.V0);
				} else if (!isV1 && VERSION_2.equals(line)) {
					// Protocol V2: remaining lines are capabilities as
					// key=value pairs
					setProtocolVersion(TransferConfig.ProtocolVersion.V2);
					readCapabilitiesV2();
					// Break out here so that stateless RPC transports get a
					// chance to set up the output stream.
					return false;
				} else {
					setProtocolVersion(TransferConfig.ProtocolVersion.V0);
				}
			} else {
				line = readLine();
				if (line == null) {
					break;
				}
			}

			// Expecting to get a line in the form "sha1 refname"
			if (line.length() < 41 || line.charAt(40) != ' ') {
				throw invalidRefAdvertisementLine(line);
			}
			String name = line.substring(41, line.length());
			if (first && name.equals("capabilities^{}")) { //$NON-NLS-1$
				// special line from git-receive-pack (protocol V0) to show
				// capabilities when there are no refs to advertise
				continue;
			}

			final ObjectId id = toId(line, line.substring(0, 40));
			if (name.equals(".have")) { //$NON-NLS-1$
				additionalHaves.add(id);
			} else {
				processLineV1(name, id, avail);
			}
		}
		updateWithSymRefs(avail, symRefs);
		available(avail);
		return true;
	}

	/**
	 * Issue a protocol V2 ls-refs command and read its response.
	 *
	 * @param refSpecs
	 *            to produce ref prefixes from if the server supports git
	 *            protocol V2
	 * @param additionalPatterns
	 *            to use for ref prefixes if the server supports git protocol V2
	 * @throws TransportException
	 *             if the command could not be run or its output not be read
	 */
	protected void lsRefs(Collection<RefSpec> refSpecs,
			String... additionalPatterns) throws TransportException {
		try {
			lsRefsImpl(refSpecs, additionalPatterns);
		} catch (TransportException err) {
			close();
			throw err;
		} catch (IOException | RuntimeException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		}
	}

	private void lsRefsImpl(Collection<RefSpec> refSpecs,
			String... additionalPatterns) throws IOException {
		pckOut.writeString("command=" + COMMAND_LS_REFS); //$NON-NLS-1$
		// Add the user-agent
		String agent = UserAgent.get();
		if (agent != null && isCapableOf(OPTION_AGENT)) {
			pckOut.writeString(OPTION_AGENT + '=' + agent);
		}
		pckOut.writeDelim();
		pckOut.writeString("peel"); //$NON-NLS-1$
		pckOut.writeString("symrefs"); //$NON-NLS-1$
		for (String refPrefix : getRefPrefixes(refSpecs, additionalPatterns)) {
			pckOut.writeString("ref-prefix " + refPrefix); //$NON-NLS-1$
		}
		pckOut.end();
		final Map<String, Ref> avail = new LinkedHashMap<>();
		final Map<String, String> symRefs = new LinkedHashMap<>();
		for (;;) {
			String line = readLine();
			if (line == null) {
				break;
			}
			// Expecting to get a line in the form "sha1 refname"
			if (line.length() < 41 || line.charAt(40) != ' ') {
				throw invalidRefAdvertisementLine(line);
			}
			String name = line.substring(41, line.length());
			final ObjectId id = toId(line, line.substring(0, 40));
			if (name.equals(".have")) { //$NON-NLS-1$
				additionalHaves.add(id);
			} else {
				processLineV2(line, id, name, avail, symRefs);
			}
		}
		updateWithSymRefs(avail, symRefs);
		available(avail);
	}

	private Collection<String> getRefPrefixes(Collection<RefSpec> refSpecs,
			String... additionalPatterns) {
		if (refSpecs.isEmpty() && (additionalPatterns == null
				|| additionalPatterns.length == 0)) {
			return Collections.emptyList();
		}
		Set<String> patterns = new HashSet<>();
		if (additionalPatterns != null) {
			Arrays.stream(additionalPatterns).filter(Objects::nonNull)
					.forEach(patterns::add);
		}
		for (RefSpec spec : refSpecs) {
			// TODO: for now we only do protocol V2 for fetch. For push
			// RefSpecs, the logic would need to be different. (At the
			// minimum, take spec.getDestination().)
			String src = spec.getSource();
			if (ObjectId.isId(src)) {
				continue;
			}
			if (spec.isWildcard()) {
				patterns.add(src.substring(0, src.indexOf('*')));
			} else {
				patterns.add(src);
				patterns.add(Constants.R_REFS + src);
				patterns.add(Constants.R_HEADS + src);
				patterns.add(Constants.R_TAGS + src);
			}
		}
		return patterns;
	}

	private void readCapabilitiesV2() throws IOException {
		// In git protocol V2, capabilities are different. If it's a key-value
		// pair, the key may be a command name, and the value a space-separated
		// list of capabilities for that command. We still store it in the same
		// map as for protocol v0/v1. Protocol v2 code has to account for this.
		for (;;) {
			String line = readLine();
			if (line == null) {
				break;
			}
			addCapability(line);
		}
	}

	private void addCapability(String capability) {
		String parts[] = capability.split("=", 2); //$NON-NLS-1$
		if (parts.length == 2) {
			remoteCapabilities.put(parts[0], parts[1]);
		}
		remoteCapabilities.put(capability, null);
	}

	private ObjectId toId(String line, String value)
			throws PackProtocolException {
		try {
			return ObjectId.fromString(value);
		} catch (InvalidObjectIdException e) {
			PackProtocolException ppe = invalidRefAdvertisementLine(line);
			ppe.initCause(e);
			throw ppe;
		}
	}

	private void processLineV1(String name, ObjectId id, Map<String, Ref> avail)
			throws IOException {
		if (name.endsWith("^{}")) { //$NON-NLS-1$
			name = name.substring(0, name.length() - 3);
			final Ref prior = avail.get(name);
			if (prior == null) {
				throw new PackProtocolException(uri, MessageFormat.format(
						JGitText.get().advertisementCameBefore, name, name));
			}
			if (prior.getPeeledObjectId() != null) {
				throw duplicateAdvertisement(name + "^{}"); //$NON-NLS-1$
			}
			avail.put(name, new ObjectIdRef.PeeledTag(Ref.Storage.NETWORK, name,
					prior.getObjectId(), id));
		} else {
			final Ref prior = avail.put(name, new ObjectIdRef.PeeledNonTag(
					Ref.Storage.NETWORK, name, id));
			if (prior != null) {
				throw duplicateAdvertisement(name);
			}
		}
	}

	private void processLineV2(String line, ObjectId id, String rest,
			Map<String, Ref> avail, Map<String, String> symRefs)
			throws IOException {
		String[] parts = rest.split(" "); //$NON-NLS-1$
		String name = parts[0];
		// Two attributes possible, symref-target or peeled
		String symRefTarget = null;
		String peeled = null;
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].startsWith(REF_ATTR_SYMREF_TARGET)) {
				if (symRefTarget != null) {
					throw new PackProtocolException(uri, MessageFormat.format(
							JGitText.get().duplicateRefAttribute, line));
				}
				symRefTarget = parts[i]
						.substring(REF_ATTR_SYMREF_TARGET.length());
			} else if (parts[i].startsWith(REF_ATTR_PEELED)) {
				if (peeled != null) {
					throw new PackProtocolException(uri, MessageFormat.format(
							JGitText.get().duplicateRefAttribute, line));
				}
				peeled = parts[i].substring(REF_ATTR_PEELED.length());
			}
			if (peeled != null && symRefTarget != null) {
				break;
			}
		}
		Ref idRef;
		if (peeled != null) {
			idRef = new ObjectIdRef.PeeledTag(Ref.Storage.NETWORK, name, id,
					toId(line, peeled));
		} else {
			idRef = new ObjectIdRef.PeeledNonTag(Ref.Storage.NETWORK, name, id);
		}
		Ref prior = avail.put(name, idRef);
		if (prior != null) {
			throw duplicateAdvertisement(name);
		}
		if (!StringUtils.isEmptyOrNull(symRefTarget)) {
			symRefs.put(name, symRefTarget);
		}
	}

	/**
	 * Updates the given refMap with {@link SymbolicRef}s defined by the given
	 * symRefs.
	 * <p>
	 * For each entry, symRef, in symRefs, whose value is a key in refMap, adds
	 * a new entry to refMap with that same key and value of a new
	 * {@link SymbolicRef} with source=symRef.key and
	 * target=refMap.get(symRef.value), then removes that entry from symRefs.
	 * <p>
	 * If refMap already contains an entry for symRef.key, it is replaced.
	 * </p>
	 * </p>
	 * <p>
	 * For example, given:
	 * </p>
	 *
	 * <pre>
	 * refMap.put("refs/heads/main", ref);
	 * symRefs.put("HEAD", "refs/heads/main");
	 * </pre>
	 *
	 * then:
	 *
	 * <pre>
	 * updateWithSymRefs(refMap, symRefs);
	 * </pre>
	 *
	 * has the <em>effect</em> of:
	 *
	 * <pre>
	 * refMap.put("HEAD",
	 * 		new SymbolicRef("HEAD", refMap.get(symRefs.remove("HEAD"))))
	 * </pre>
	 * <p>
	 * Any entry in symRefs whose value is not a key in refMap is ignored. Any
	 * circular symRefs are ignored.
	 * </p>
	 * <p>
	 * Upon completion, symRefs will contain only any unresolvable entries.
	 * </p>
	 *
	 * @param refMap
	 *            a non-null, modifiable, Map to update, and the provider of
	 *            symref targets.
	 * @param symRefs
	 *            a non-null, modifiable, Map of symrefs.
	 * @throws NullPointerException
	 *             if refMap or symRefs is null
	 */
	static void updateWithSymRefs(Map<String, Ref> refMap, Map<String, String> symRefs) {
		boolean haveNewRefMapEntries = !refMap.isEmpty();
		while (!symRefs.isEmpty() && haveNewRefMapEntries) {
			haveNewRefMapEntries = false;
			final Iterator<Map.Entry<String, String>> iterator = symRefs.entrySet().iterator();
			while (iterator.hasNext()) {
				final Map.Entry<String, String> symRef = iterator.next();
				if (!symRefs.containsKey(symRef.getValue())) { // defer forward reference
					final Ref r = refMap.get(symRef.getValue());
					if (r != null) {
						refMap.put(symRef.getKey(), new SymbolicRef(symRef.getKey(), r));
						haveNewRefMapEntries = true;
						iterator.remove();
					}
				}
			}
		}
		// If HEAD is still in the symRefs map here, the real ref was not
		// reported, but we know it must point to the object reported for HEAD.
		// So fill it in in the refMap.
		String headRefName = symRefs.get(Constants.HEAD);
		if (headRefName != null && !refMap.containsKey(headRefName)) {
			Ref headRef = refMap.get(Constants.HEAD);
			if (headRef != null) {
				ObjectId headObj = headRef.getObjectId();
				headRef = new ObjectIdRef.PeeledNonTag(Ref.Storage.NETWORK,
						headRefName, headObj);
				refMap.put(headRefName, headRef);
				headRef = new SymbolicRef(Constants.HEAD, headRef);
				refMap.put(Constants.HEAD, headRef);
				symRefs.remove(Constants.HEAD);
			}
		}
	}

	/**
	 * Create an exception to indicate problems finding a remote repository. The
	 * caller is expected to throw the returned exception.
	 *
	 * Subclasses may override this method to provide better diagnostics.
	 *
	 * @return a TransportException saying a repository cannot be found and
	 *         possibly why.
	 */
	protected TransportException noRepository() {
		return new NoRemoteRepositoryException(uri, JGitText.get().notFound);
	}

	/**
	 * Whether this option is supported
	 *
	 * @param option
	 *            option string
	 * @return whether this option is supported
	 */
	protected boolean isCapableOf(String option) {
		return remoteCapabilities.containsKey(option);
	}

	/**
	 * Request capability
	 *
	 * @param b
	 *            buffer
	 * @param option
	 *            option we want
	 * @return {@code true} if the requested option is supported
	 */
	protected boolean wantCapability(StringBuilder b, String option) {
		if (!isCapableOf(option))
			return false;
		b.append(' ');
		b.append(option);
		return true;
	}

	/**
	 * Return a capability value.
	 *
	 * @param option
	 *            to get
	 * @return the value stored, if any.
	 */
	protected String getCapability(String option) {
		return remoteCapabilities.get(option);
	}

	/**
	 * Add user agent capability
	 *
	 * @param b
	 *            a {@link java.lang.StringBuilder} object.
	 */
	protected void addUserAgentCapability(StringBuilder b) {
		String a = UserAgent.get();
		if (a != null && remoteCapabilities.get(OPTION_AGENT) != null) {
			b.append(' ').append(OPTION_AGENT).append('=').append(a);
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getPeerUserAgent() {
		String agent = remoteCapabilities.get(OPTION_AGENT);
		return agent != null ? agent : super.getPeerUserAgent();
	}

	private PackProtocolException duplicateAdvertisement(String name) {
		return new PackProtocolException(uri, MessageFormat.format(JGitText.get().duplicateAdvertisementsOf, name));
	}

	private PackProtocolException invalidRefAdvertisementLine(String line) {
		return new PackProtocolException(uri, MessageFormat.format(JGitText.get().invalidRefAdvertisementLine, line));
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (out != null) {
			try {
				if (outNeedsEnd) {
					outNeedsEnd = false;
					pckOut.end();
				}
				out.close();
			} catch (IOException err) {
				// Ignore any close errors.
			} finally {
				out = null;
				pckOut = null;
			}
		}

		if (in != null) {
			try {
				in.close();
			} catch (IOException err) {
				// Ignore any close errors.
			} finally {
				in = null;
				pckIn = null;
			}
		}

		if (myTimer != null) {
			try {
				myTimer.terminate();
			} finally {
				myTimer = null;
				timeoutIn = null;
				timeoutOut = null;
			}
		}
	}

	/**
	 * Tell the peer we are disconnecting, if it cares to know.
	 */
	protected void endOut() {
		if (outNeedsEnd && out != null) {
			try {
				outNeedsEnd = false;
				pckOut.end();
			} catch (IOException e) {
				try {
					out.close();
				} catch (IOException err) {
					// Ignore any close errors.
				} finally {
					out = null;
					pckOut = null;
				}
			}
		}
	}
}
