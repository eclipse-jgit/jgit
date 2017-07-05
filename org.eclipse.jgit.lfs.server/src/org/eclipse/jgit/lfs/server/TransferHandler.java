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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.lfs.lib.Constants.DOWNLOAD;
import static org.eclipse.jgit.lfs.lib.Constants.UPLOAD;
import static org.eclipse.jgit.lfs.lib.Constants.VERIFY;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lfs.server.Response.Action;
import org.eclipse.jgit.lfs.server.Response.Body;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;

abstract class TransferHandler {

	static TransferHandler forOperation(String operation,
			LargeFileRepository repository, List<LfsObject> objects) {
		switch (operation) {
		case UPLOAD:
			return new Upload(repository, objects);
		case DOWNLOAD:
			return new Download(repository, objects);
		case VERIFY:
		default:
			throw new UnsupportedOperationException(MessageFormat.format(
					LfsServerText.get().unsupportedOperation, operation));
		}
	}

	private static class Upload extends TransferHandler {
		Upload(LargeFileRepository repository,
				List<LfsObject> objects) {
			super(repository, objects);
		}

		@Override
		Body process() throws IOException {
			Response.Body body = new Response.Body();
			if (objects.size() > 0) {
				body.objects = new ArrayList<>();
				for (LfsObject o : objects) {
					addObjectInfo(body, o);
				}
			}
			return body;
		}

		private void addObjectInfo(Response.Body body, LfsObject o)
				throws IOException {
			Response.ObjectInfo info = new Response.ObjectInfo();
			body.objects.add(info);
			info.oid = o.oid;
			info.size = o.size;

			LongObjectId oid = LongObjectId.fromString(o.oid);
			if (repository.getSize(oid) == -1) {
				info.actions = new HashMap<>();
				info.actions.put(UPLOAD,
						repository.getUploadAction(oid, o.size));
				Action verify = repository.getVerifyAction(oid);
				if (verify != null) {
					info.actions.put(VERIFY, verify);
				}
			}
		}
	}

	private static class Download extends TransferHandler {
		Download(LargeFileRepository repository,
				List<LfsObject> objects) {
			super(repository, objects);
		}

		@Override
		Body process() throws IOException {
			Response.Body body = new Response.Body();
			if (objects.size() > 0) {
				body.objects = new ArrayList<>();
				for (LfsObject o : objects) {
					addObjectInfo(body, o);
				}
			}
			return body;
		}

		private void addObjectInfo(Response.Body body, LfsObject o)
				throws IOException {
			Response.ObjectInfo info = new Response.ObjectInfo();
			body.objects.add(info);
			info.oid = o.oid;
			info.size = o.size;

			LongObjectId oid = LongObjectId.fromString(o.oid);
			if (repository.getSize(oid) >= 0) {
				info.actions = new HashMap<>();
				info.actions.put(DOWNLOAD,
						repository.getDownloadAction(oid));
			} else {
				info.error = new Response.Error();
				info.error.code = SC_NOT_FOUND;
				info.error.message = MessageFormat.format(
						LfsServerText.get().objectNotFound,
						oid.getName());
			}
		}
	}

	final LargeFileRepository repository;

	final List<LfsObject> objects;

	TransferHandler(LargeFileRepository repository,
			List<LfsObject> objects) {
		this.repository = repository;
		this.objects = objects;
	}

	abstract Response.Body process() throws IOException;
}
