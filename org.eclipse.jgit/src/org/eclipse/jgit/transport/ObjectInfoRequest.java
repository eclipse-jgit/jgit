/*
 * Copyright (C) 2021, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.util.Collections;
import java.util.List;

/**
 * object-info request.
 *
 * <p>
 * This is used as input to {@link ProtocolV2Hook}.
 *
 * @since 5.13
 */
public final class ObjectInfoRequest {
	private final List<String> objectIDs;

	private ObjectInfoRequest(List<String> objectIDs) {
		this.objectIDs = objectIDs;
	}

	/** @return object IDs that the client requested. */
	public List<String> getObjectIDs() {
		return this.objectIDs;
	}

	/** @return A builder of {@link ObjectInfoRequest}. */
	public static Builder builder() {
		return new Builder();
	}

	/** A builder for {@link ObjectInfoRequest}. */
	public static final class Builder {
		private List<String> objectIDs = Collections.emptyList();

		private Builder() {
		}

		/**
		 * @param value
		 * @return the Builder
		 */
		public Builder setObjectIDs(List<String> value) {
			objectIDs = value;
			return this;
		}

		/** @return ObjectInfoRequest */
		public ObjectInfoRequest build() {
			return new ObjectInfoRequest(
					Collections.unmodifiableList(objectIDs));
		}
	}
}
