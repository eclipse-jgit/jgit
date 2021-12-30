/*
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.server;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
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
			if (!objects.isEmpty()) {
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
			if (!objects.isEmpty()) {
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
