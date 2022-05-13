/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Fetch request from git protocol v2.
 *
 * <p>
 * This is used as an input to {@link ProtocolV2Hook}.
 *
 * @since 5.1
 */
public final class FetchV2Request extends FetchRequest {
	private final List<ObjectId> peerHas;

	private final List<String> wantedRefs;

	private final boolean doneReceived;

	private final boolean waitForDone;

	@NonNull
	private final List<String> serverOptions;

	private final boolean sidebandAll;

	@NonNull
	private final List<String> packfileUriProtocols;

	FetchV2Request(@NonNull List<ObjectId> peerHas,
			@NonNull List<String> wantedRefs,
			@NonNull Set<ObjectId> wantIds,
			@NonNull Set<ObjectId> clientShallowCommits, int deepenSince,
			@NonNull List<String> deepenNots, int depth,
			@NonNull FilterSpec filterSpec,
			boolean doneReceived, boolean waitForDone,
			@NonNull Set<String> clientCapabilities,
			@Nullable String agent, @NonNull List<String> serverOptions,
			boolean sidebandAll, @NonNull List<String> packfileUriProtocols) {
		super(wantIds, depth, clientShallowCommits, filterSpec,
				clientCapabilities, deepenSince,
				deepenNots, agent);
		this.peerHas = requireNonNull(peerHas);
		this.wantedRefs = requireNonNull(wantedRefs);
		this.doneReceived = doneReceived;
		this.waitForDone = waitForDone;
		this.serverOptions = requireNonNull(serverOptions);
		this.sidebandAll = sidebandAll;
		this.packfileUriProtocols = packfileUriProtocols;
	}

	/**
	 * @return object ids received in the "have" lines
	 */
	@NonNull
	List<ObjectId> getPeerHas() {
		return peerHas;
	}

	/**
	 * @return list of references received in "want-ref" lines
	 *
	 * @since 5.4
	 */
	@NonNull
	public List<String> getWantedRefs() {
		return wantedRefs;
	}

	/**
	 * @return true if the request had a "done" line
	 */
	boolean wasDoneReceived() {
		return doneReceived;
	}

	/**
	 * @return true if the request had a "wait-for-done" line
	 */
	boolean wasWaitForDoneReceived() {
		return waitForDone;
	}

	/**
	 * Options received in server-option lines. The caller can choose to	 act on
	 * these in an application-specific way
	 *
	 * @return Immutable list of server options received in the request
	 *
	 * @since 5.2
	 */
	@NonNull
	public List<String> getServerOptions() {
		return serverOptions;
	}

	/**
	 * @return true if "sideband-all" was received
	 */
	boolean getSidebandAll() {
		return sidebandAll;
	}

	@NonNull
	List<String> getPackfileUriProtocols() {
		return packfileUriProtocols;
	}

	/** @return A builder of {@link FetchV2Request}. */
	static Builder builder() {
		return new Builder();
	}

	/** A builder for {@link FetchV2Request}. */
	static final class Builder {
		final List<ObjectId> peerHas = new ArrayList<>();

		final List<String> wantedRefs = new ArrayList<>();

		final Set<ObjectId> wantIds = new HashSet<>();

		final Set<ObjectId> clientShallowCommits = new HashSet<>();

		final List<String> deepenNots = new ArrayList<>();

		final Set<String> clientCapabilities = new HashSet<>();

		int depth;

		int deepenSince;

		FilterSpec filterSpec = FilterSpec.NO_FILTER;

		boolean doneReceived;

		boolean waitForDone;

		@Nullable
		String agent;

		final List<String> serverOptions = new ArrayList<>();

		boolean sidebandAll;

		final List<String> packfileUriProtocols = new ArrayList<>();

		private Builder() {
		}

		/**
		 * @param objectId
		 *            object id received in a "have" line
		 * @return this builder
		 */
		Builder addPeerHas(ObjectId objectId) {
			peerHas.add(objectId);
			return this;
		}

		/**
		 * Ref received in "want-ref" line and the object-id it refers to
		 *
		 * @param refName
		 *            reference name
		 * @return this builder
		 */
		Builder addWantedRef(String refName) {
			wantedRefs.add(refName);
			return this;
		}

		/**
		 * @param clientCapability
		 *            capability line sent by the client
		 * @return this builder
		 */
		Builder addClientCapability(String clientCapability) {
			clientCapabilities.add(clientCapability);
			return this;
		}

		/**
		 * @param wantId
		 *            object id received in a "want" line
		 * @return this builder
		 */
		Builder addWantId(ObjectId wantId) {
			wantIds.add(wantId);
			return this;
		}

		/**
		 * @param shallowOid
		 *            object id received in a "shallow" line
		 * @return this builder
		 */
		Builder addClientShallowCommit(ObjectId shallowOid) {
			clientShallowCommits.add(shallowOid);
			return this;
		}

		/**
		 * @param d
		 *            Depth received in a "deepen" line
		 * @return this builder
		 */
		Builder setDepth(int d) {
			depth = d;
			return this;
		}

		/**
		 * @return depth set in the request (via a "deepen" line). Defaulting to
		 *         0 if not set.
		 */
		int getDepth() {
			return depth;
		}

		/**
		 * @return true if there has been at least one "deepen not" line in the
		 *         request so far
		 */
		boolean hasDeepenNots() {
			return !deepenNots.isEmpty();
		}

		/**
		 * @param deepenNot
		 *            reference received in a "deepen not" line
		 * @return this builder
		 */
		Builder addDeepenNot(String deepenNot) {
			deepenNots.add(deepenNot);
			return this;
		}

		/**
		 * @param value
		 *            Unix timestamp received in a "deepen since" line
		 * @return this builder
		 */
		Builder setDeepenSince(int value) {
			deepenSince = value;
			return this;
		}

		/**
		 * @return shallow since value, sent before in a "deepen since" line. 0
		 *         by default.
		 */
		int getDeepenSince() {
			return deepenSince;
		}

		/**
		 * @param filter
		 *            spec set in a "filter" line
		 * @return this builder
		 */
		Builder setFilterSpec(@NonNull FilterSpec filter) {
			filterSpec = requireNonNull(filter);
			return this;
		}

		/**
		 * Mark that the "done" line has been received.
		 *
		 * @return this builder
		 */
		Builder setDoneReceived() {
			doneReceived = true;
			return this;
		}

		/**
		 * Mark that the "wait-for-done" line has been received.
		 *
		 * @return this builder
		 */
		Builder setWaitForDone() {
			waitForDone = true;
			return this;
		}

		/**
		 * Value of an agent line received after the command and before the
		 * arguments. E.g. "agent=a.b.c/1.0" should set "a.b.c/1.0".
		 *
		 * @param agentValue
		 *            the client-supplied agent capability, without the leading
		 *            "agent="
		 * @return this builder
		 */
		Builder setAgent(@Nullable String agentValue) {
			agent = agentValue;
			return this;
		}

		/**
		 * Records an application-specific option supplied in a server-option
		 * line, for later retrieval with
		 * {@link FetchV2Request#getServerOptions}.
		 *
		 * @param value
		 *            the client-supplied server-option capability, without
		 *            leading "server-option=".
		 * @return this builder
		 */
		Builder addServerOption(@NonNull String value) {
			serverOptions.add(value);
			return this;
		}

		/**
		 * @param value true if client sent "sideband-all"
		 * @return this builder
		 */
		Builder setSidebandAll(boolean value) {
			sidebandAll = value;
			return this;
		}

		Builder addPackfileUriProtocol(@NonNull String value) {
			packfileUriProtocols.add(value);
			return this;
		}

		/**
		 * @return Initialized fetch request
		 */
		FetchV2Request build() {
			return new FetchV2Request(peerHas, wantedRefs, wantIds,
					clientShallowCommits, deepenSince, deepenNots,
					depth, filterSpec, doneReceived, waitForDone, clientCapabilities,
					agent, Collections.unmodifiableList(serverOptions),
					sidebandAll,
					Collections.unmodifiableList(packfileUriProtocols));
		}
	}
}
