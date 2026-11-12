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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;

/** Immutable first-representation metadata, separate from output index entries. */
final class ReceivedPackEntry {
	final ObjectId id;

	final int type;

	final long fullSize;

	final long offset;

	final int crc;

	final boolean whole;

	final boolean selected;

	boolean outputEmitted;

	ReceivedPackEntry(PackedObjectInfo incoming, boolean whole, boolean selected) {
		id = incoming.copy();
		type = incoming.getType();
		fullSize = incoming.getFullSize();
		offset = incoming.getOffset();
		crc = incoming.getCRC();
		this.whole = whole;
		this.selected = selected;
	}

	PackedObjectInfo replaySource() {
		PackedObjectInfo source = new PackedObjectInfo(id);
		source.setType(type);
		source.setFullSize(fullSize);
		source.setOffset(offset);
		source.setCRC(crc);
		return source;
	}
}
