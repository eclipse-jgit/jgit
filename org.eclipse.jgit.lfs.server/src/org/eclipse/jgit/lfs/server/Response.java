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

import java.util.List;
import java.util.Map;

/**
 * POJOs for Gson serialization/de-serialization.
 *
 * See the <a href="https://github.com/github/git-lfs/tree/master/docs/api">LFS
 * API specification</a>
 *
 * @since 4.3
 */
public interface Response {
	/** Describes an action the client can execute on a single object */
	class Action {
		public String href;
		public Map<String, String> header;
	}

	/** Describes an error to be returned by the LFS batch API */
	class Error {
		public int code;
		public String message;
	}

	/** Describes the actions the LFS server offers for a single object */
	class ObjectInfo {
		public String oid;
		public long size;
		public Map<String, Action> actions;
		public Error error;
	}

	/** Describes the body of a LFS batch API response */
	class Body {
		public List<ObjectInfo> objects;
	}
}
