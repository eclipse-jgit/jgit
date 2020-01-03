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

/**
 * Capabilities protocol v2 request.
 *
 * <p>
 * This is used as an input to {@link ProtocolV2Hook}.
 *
 * @since 5.1
 */
public final class CapabilitiesV2Request {
	private CapabilitiesV2Request() {
	}

	/** @return A builder of {@link CapabilitiesV2Request}. */
	public static Builder builder() {
		return new Builder();
	}

	/** A builder for {@link CapabilitiesV2Request}. */
	public static final class Builder {
		private Builder() {
		}

		/** @return CapabilitiesV2Request */
		public CapabilitiesV2Request build() {
			return new CapabilitiesV2Request();
		}
	}
}
