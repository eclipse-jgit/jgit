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

import org.eclipse.jgit.lib.ObjectId;

/**
 * object-info request.
 *
 * <p>
 * This is the parsed request for an object-info call, used as input to
 * {@link ProtocolV2Hook}.
 *
 * @see <a href=
 *      "https://www.kernel.org/pub/software/scm/git/docs/technical/protocol-v2.html#_object_info">object-info
 *      documentation</a>
 *
 * @since 5.13
 */
public final class ObjectInfoRequest {
	private final List<ObjectId> objectIDs;

	private ObjectInfoRequest(List<ObjectId> objectIDs) {
		this.objectIDs = objectIDs;
	}

	/**
	 * Get object ids requested by the client
	 *
	 * @return object IDs that the client requested.
	 */
	public List<ObjectId> getObjectIDs() {
		return this.objectIDs;
	}

	/**
	 * Create builder
	 *
	 * @return A builder of {@link ObjectInfoRequest}.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** A builder for {@link ObjectInfoRequest}. */
	public static final class Builder {
		private List<ObjectId> objectIDs = Collections.emptyList();

		private Builder() {
		}

		/**
		 * Set object ids
		 *
		 * @param value
		 *            of objectIds
		 * @return the Builder
		 */
		public Builder setObjectIDs(List<ObjectId> value) {
			objectIDs = value;
			return this;
		}

		/**
		 * Build the request
		 *
		 * @return ObjectInfoRequest the request
		 */
		public ObjectInfoRequest build() {
			return new ObjectInfoRequest(
					Collections.unmodifiableList(objectIDs));
		}
	}
}
