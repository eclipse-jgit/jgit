/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.RefList;

class ListRefCursor extends RefCursor {
	private final RefList<Ref> list;

	private int index;
	private Ref ref;
	private String seeking;

	ListRefCursor(Collection<Ref> refs) {
		RefList.Builder<Ref> b = new RefList.Builder<>(refs.size());
		for (Ref r : refs) {
			b.add(r);
		}
		b.sort();
		list = b.toRefList();
	}

	@Override
	public void seekToFirstRef() throws IOException {
		index = 0;
		seeking = null;
		ref = null;
	}

	@Override
	public void seek(String refName) throws IOException {
		seeking = refName;
		ref = null;

		if (refName.endsWith("/")) { //$NON-NLS-1$
			refName += '\1';
		}

		index = list.find(refName);
		if (index < 0) {
			index = -(index + 1);
		}
	}

	@Override
	public boolean next() throws IOException {
		if (index >= list.size()) {
			return false;
		}

		ref = list.get(index++);
		if (seeking == null) {
			return true;
		}

		String name = ref.getName();
		return seeking.endsWith("/") //$NON-NLS-1$
				? name.startsWith(seeking)
				: name.equals(seeking);
	}

	@Override
	public Ref getRef() {
		return ref;
	}

	@Override
	public void close() {
		// Nothing to do, its an in-memory list.
	}
}
