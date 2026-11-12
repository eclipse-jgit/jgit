/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;

/** Immutable first-representation metadata, separate from output index entries. */
final class ReceivedPackEntry {
	final ObjectId id;

	final int type;

	final long fullSize;

	final Source source;

	final ObjectId baseId;

	final boolean whole;

	final boolean selected;

	boolean outputEmitted;

	ReceivedPackEntry(PackedObjectInfo incoming, Source source,
			AnyObjectId baseId, boolean selected) {
		id = incoming.copy();
		type = incoming.getType();
		fullSize = incoming.getFullSize();
		this.source = source;
		this.baseId = baseId != null ? baseId.copy() : null;
		whole = source.encodedType != Constants.OBJ_OFS_DELTA
				&& source.encodedType != Constants.OBJ_REF_DELTA;
		this.selected = selected;
	}

	PackedObjectInfo replaySource() {
		PackedObjectInfo source = new PackedObjectInfo(id);
		source.setType(type);
		source.setFullSize(fullSize);
		source.setOffset(this.source.offset);
		source.setCRC(this.source.crc);
		return source;
	}

	/** Original INPUT bytes, never replaced by DATABASE rereads or output data. */
	static final class Source {
		final long offset;

		final int encodedType;

		/** Inflated delta instruction size for deltas, not reconstructed size. */
		final long declaredSize;

		private final byte[] header;

		final long payloadOffset;

		final long payloadLength;

		final int crc;

		Source(long offset, int encodedType, long declaredSize, byte[] header,
				long payloadOffset, long payloadLength, int crc) {
			this.offset = offset;
			this.encodedType = encodedType;
			this.declaredSize = declaredSize;
			this.header = header.clone();
			this.payloadOffset = payloadOffset;
			this.payloadLength = payloadLength;
			this.crc = crc;
		}

		byte[] originalHeader() {
			return header.clone();
		}
	}
}
