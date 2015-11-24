/*
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com>
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

package org.eclipse.jgit.lfs.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lfs.lib.LargeFileRepository;
import org.eclipse.jgit.lfs.lib.LongObjectId;

class TransferHandler {

	private static final String DOWNLOAD = "download"; //$NON-NLS-1$
	private static final String UPLOAD = "upload"; //$NON-NLS-1$

	private final LargeFileRepository repository;
	private final List<LfsObject> objects;

	TransferHandler(LargeFileRepository repository, List<LfsObject> objects) {
		this.repository = repository;
		this.objects = objects;
	}

	Response.Body process() {
		Response.Body body = new Response.Body();
		if (objects.size() > 0) {
			body.objects = new ArrayList<>();
			for (LfsObject o : objects) {
				Response.ObjectInfo info = new Response.ObjectInfo();
				body.objects.add(info);
				info.oid = o.oid;
				info.size = o.size;
				info.actions = new HashMap<>();

				LongObjectId oid = LongObjectId.fromString(o.oid);
				addAction(UPLOAD, oid, info.actions);
				if (repository.exists(oid)) {
					addAction(DOWNLOAD, oid, info.actions);
				}
			}
		}
		return body;
	}

	private void addAction(String name, LongObjectId oid,
			Map<String, Response.Action> actions) {
		Response.Action action = new Response.Action();
		action.href = repository.getUrl(oid) + oid.getName();
		action.header = new HashMap<>();
		// TODO: when should this be used:
		action.header.put("Authorization", "not:required");
		actions.put(name, action);
	}
}
