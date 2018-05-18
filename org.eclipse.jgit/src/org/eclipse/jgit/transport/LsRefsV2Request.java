/*
 * Copyright (C) 2018, Google LLC.
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

import java.util.Collections;
import java.util.List;

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

	private LsRefsV2Request(List<String> refPrefixes, boolean symrefs,
			boolean peel) {
		this.refPrefixes = refPrefixes;
		this.symrefs = symrefs;
		this.peel = peel;
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

	/** @return A builder of {@link LsRefsV2Request}. */
	public static Builder builder() {
		return new Builder();
	}

	/** A builder for {@link LsRefsV2Request}. */
	public static final class Builder {
		private List<String> refPrefixes = Collections.emptyList();

		private boolean symrefs;

		private boolean peel;

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

		/** @return LsRefsV2Request */
		public LsRefsV2Request build() {
			return new LsRefsV2Request(
					Collections.unmodifiableList(refPrefixes), symrefs, peel);
		}
	}
}
