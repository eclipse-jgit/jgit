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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Fetch request in the V0/V1 protocol.
 */
final class FetchV0Request extends FetchRequest {

	FetchV0Request(@NonNull Set<ObjectId> wantIds, int depth,
			@NonNull Set<ObjectId> clientShallowCommits,
			@NonNull FilterSpec filterSpec,
			@NonNull Set<String> clientCapabilities, int deepenSince,
			@NonNull List<String> deepenNotRefs, @Nullable String agent) {
		super(wantIds, depth, clientShallowCommits, filterSpec,
				clientCapabilities, deepenSince, deepenNotRefs, agent);
	}

	static final class Builder {

		int depth;

		final List<String> deepenNots = new ArrayList<>();

		int deepenSince;

		final Set<ObjectId> wantIds = new HashSet<>();

		final Set<ObjectId> clientShallowCommits = new HashSet<>();

		FilterSpec filterSpec = FilterSpec.NO_FILTER;

		final Set<String> clientCaps = new HashSet<>();

		String agent;

		/**
		 * @param objectId
		 *            object id received in a "want" line
		 * @return this builder
		 */
		Builder addWantId(ObjectId objectId) {
			wantIds.add(objectId);
			return this;
		}

		/**
		 * @param d
		 *            depth set in a "deepen" line
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
		 * @param shallowOid
		 *            object id received in a "shallow" line
		 * @return this builder
		 */
		Builder addClientShallowCommit(ObjectId shallowOid) {
			clientShallowCommits.add(shallowOid);
			return this;
		}

		/**
		 * @param clientCapabilities
		 *            client capabilities sent by the client in the first want
		 *            line of the request
		 * @return this builder
		 */
		Builder addClientCapabilities(Collection<String> clientCapabilities) {
			clientCaps.addAll(clientCapabilities);
			return this;
		}

		/**
		 * @param clientAgent
		 *            agent line sent by the client in the request body
		 * @return this builder
		 */
		Builder setAgent(String clientAgent) {
			agent = clientAgent;
			return this;
		}

		/**
		 * @param filter
		 *            the filter set in a filter line
		 * @return this builder
		 */
		Builder setFilterSpec(@NonNull FilterSpec filter) {
			filterSpec = requireNonNull(filter);
			return this;
		}

		FetchV0Request build() {
			return new FetchV0Request(wantIds, depth, clientShallowCommits,
					filterSpec, clientCaps, deepenSince, deepenNots, agent);
		}

	}
}
