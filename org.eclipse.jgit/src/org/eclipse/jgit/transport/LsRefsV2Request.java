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
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;

/**
 * ls-refs protocol v2 request.
 *
 * <p>
 * This is used as an input to {@link ProtocolV2Hook}.
 *
 * @since 5.1
 */
public final class LsRefsV2Request {
	private final List<String> refPrefixes;

	private final boolean symrefs;

	private final boolean peel;

	@Nullable
	private final String agent;

	@NonNull
	private final List<String> serverOptions;

	private LsRefsV2Request(List<String> refPrefixes, boolean symrefs,
			boolean peel, @Nullable String agent,
			@NonNull List<String> serverOptions) {
		this.refPrefixes = refPrefixes;
		this.symrefs = symrefs;
		this.peel = peel;
		this.agent = agent;
		this.serverOptions = requireNonNull(serverOptions);
	}

	/** @return ref prefixes that the client requested. */
	public List<String> getRefPrefixes() {
		return refPrefixes;
	}

	/** @return true if the client requests symbolic references. */
	public boolean getSymrefs() {
		return symrefs;
	}

	/** @return true if the client requests tags to be peeled. */
	public boolean getPeel() {
		return peel;
	}

	/**
	 * @return agent as reported by the client
	 *
	 * @since 5.2
	 */
	@Nullable
	public String getAgent() {
		return agent;
	}

	/**
	 * Get application-specific options provided by the client using
	 * --server-option.
	 * <p>
	 * It returns just the content, without the "server-option=" prefix. E.g. a
	 * request with server-option=A and server-option=B lines returns the list
	 * [A, B].
	 *
	 * @return application-specific options from the client as an unmodifiable
	 *         list
	 *
	 * @since 5.2
	 */
	@NonNull
	public List<String> getServerOptions() {
		return serverOptions;
	}

	/** @return A builder of {@link LsRefsV2Request}. */
	public static Builder builder() {
		return new Builder();
	}

	/** A builder for {@link LsRefsV2Request}. */
	public static final class Builder {
		private List<String> refPrefixes = Collections.emptyList();

		private boolean symrefs;

		private boolean peel;

		private final List<String> serverOptions = new ArrayList<>();

		private String agent;

		private Builder() {
		}

		/**
		 * @param value
		 * @return the Builder
		 */
		public Builder setRefPrefixes(List<String> value) {
			refPrefixes = value;
			return this;
		}

		/**
		 * @param value
		 * @return the Builder
		 */
		public Builder setSymrefs(boolean value) {
			symrefs = value;
			return this;
		}

		/**
		 * @param value
		 * @return the Builder
		 */
		public Builder setPeel(boolean value) {
			peel = value;
			return this;
		}

		/**
		 * Records an application-specific option supplied in a server-option
		 * line, for later retrieval with
		 * {@link LsRefsV2Request#getServerOptions}.
		 *
		 * @param value
		 *            the client-supplied server-option capability, without
		 *            leading "server-option=".
		 * @return this builder
		 * @since 5.2
		 */
		public Builder addServerOption(@NonNull String value) {
			serverOptions.add(value);
			return this;
		}

		/**
		 * Value of an agent line received after the command and before the
		 * arguments. E.g. "agent=a.b.c/1.0" should set "a.b.c/1.0".
		 *
		 * @param value
		 *            the client-supplied agent capability, without leading
		 *            "agent="
		 * @return this builder
		 *
		 * @since 5.2
		 */
		public Builder setAgent(@Nullable String value) {
			agent = value;
			return this;
		}

		/** @return LsRefsV2Request */
		public LsRefsV2Request build() {
			return new LsRefsV2Request(
					Collections.unmodifiableList(refPrefixes), symrefs, peel,
					agent, Collections.unmodifiableList(serverOptions));
		}
	}
}
