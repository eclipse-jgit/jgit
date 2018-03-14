/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.pgm.debug;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.zip.InflaterInputStream;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.storage.pack.BinaryDelta;
import org.eclipse.jgit.internal.storage.pack.ObjectReuseAsIs;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.kohsuke.args4j.Argument;

@Command(usage = "usage_ShowPackDelta")
class ShowPackDelta extends TextBuiltin {
	@Argument(index = 0)
	private ObjectId objectId;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		ObjectReader reader = db.newObjectReader();
		RevObject obj;
		try (RevWalk rw = new RevWalk(reader)) {
			obj = rw.parseAny(objectId);
		}
		byte[] delta = getDelta(reader, obj);

		// We're crossing our fingers that this will be a delta. Double
		// check the size field in the header, it should match.
		//
		long size = reader.getObjectSize(obj, obj.getType());
		try {
			if (BinaryDelta.getResultSize(delta) != size)
				throw die("Object " + obj.name() + " is not a delta"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (ArrayIndexOutOfBoundsException bad) {
			throw die("Object " + obj.name() + " is not a delta"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		outw.println(BinaryDelta.format(delta));
	}

	private static byte[] getDelta(ObjectReader reader, RevObject obj)
			throws IOException, MissingObjectException,
			StoredObjectRepresentationNotAvailableException {
		ObjectReuseAsIs asis = (ObjectReuseAsIs) reader;
		ObjectToPack target = asis.newObjectToPack(obj, obj.getType());

		PackWriter pw = new PackWriter(reader) {
			@Override
			public void select(ObjectToPack otp, StoredObjectRepresentation next) {
				otp.select(next);
			}
		};

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		asis.selectObjectRepresentation(pw, NullProgressMonitor.INSTANCE,
				Collections.singleton(target));
		asis.copyObjectAsIs(new PackOutputStream(NullProgressMonitor.INSTANCE,
				buf, pw), target, true);

		// At this point the object header has no delta information,
		// because it was output as though it were a whole object.
		// Skip over the header and inflate.
		//
		byte[] bufArray = buf.toByteArray();
		int ptr = 0;
		while ((bufArray[ptr] & 0x80) != 0)
			ptr++;
		ptr++;

		try (TemporaryBuffer.Heap raw = new TemporaryBuffer.Heap(
				bufArray.length);
				InflaterInputStream inf = new InflaterInputStream(
						new ByteArrayInputStream(bufArray, ptr,
								bufArray.length))) {
			raw.copy(inf);
			return raw.toByteArray();
		}
	}
}
