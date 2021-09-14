/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2015, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import java.util.List;
import java.util.Map;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This interface describes the network protocol used between lfs client and lfs
 * server
 *
 * @since 4.11
 */
public interface Protocol {
	/** A request sent to an LFS server */
	class Request {
		/** The operation of this request */
		public String operation;

		/** The objects of this request */
		public List<ObjectSpec> objects;
	}

	/** A response received from an LFS server */
	class Response {
		public List<ObjectInfo> objects;
	}

	/**
	 * MetaData of an LFS object. Needs to be specified when requesting objects
	 * from the LFS server and is also returned in the response
	 */
	class ObjectSpec {
		public String oid; // the objectid

		public long size; // the size of the object
	}

	/**
	 * Describes in a response all actions the LFS server offers for a single
	 * object
	 */
	class ObjectInfo extends ObjectSpec {
		public Map<String, Action> actions; // Maps operation to action

		public Error error;
	}

	/**
	 * Describes in a Response a single action the client can execute on a
	 * single object
	 */
	class Action {
		public String href;

		public Map<String, String> header;
	}

	/**
	 * An action with an additional expiration timestamp
	 *
	 * @since 4.11
	 */
	class ExpiringAction extends Action {
		/**
		 * Absolute date/time in format "yyyy-MM-dd'T'HH:mm:ss.SSSX"
		 */
		public String expiresAt;

		/**
		 * Validity time in milliseconds (preferred over expiresAt as specified:
		 * https://github.com/git-lfs/git-lfs/blob/master/docs/api/authentication.md)
		 */
		public String expiresIn;
	}

	/** Describes an error to be returned by the LFS batch API */
	class Error {
		public int code;

		public String message;
	}

	/**
	 * The "download" operation
	 */
	String OPERATION_DOWNLOAD = "download"; //$NON-NLS-1$

	/**
	 * The "upload" operation
	 */
	String OPERATION_UPLOAD = "upload"; //$NON-NLS-1$

	/**
	 * The "upload" action
	 */
	String ACTION_UPLOAD = "upload"; //$NON-NLS-1$

	/**
	 * The "verify" action
	 */
	String ACTION_VERIFY = "verify"; //$NON-NLS-1$

	/**
	 * The contenttype used in LFS requests
	 */
	String CONTENTTYPE_VND_GIT_LFS_JSON = "application/vnd.git-lfs+json; charset=utf-8"; //$NON-NLS-1$

	/**
	 * Authorization header when auto-discovering via SSH.
	 */
	String HDR_AUTH = "Authorization"; //$NON-NLS-1$

	/**
	 * Prefix of authentication token obtained through SSH.
	 */
	String HDR_AUTH_SSH_PREFIX = "Ssh: "; //$NON-NLS-1$

	/**
	 * Path to the LFS info servlet.
	 */
	String INFO_LFS_ENDPOINT = "/info/lfs"; //$NON-NLS-1$

	/**
	 * Path to the LFS objects servlet.
	 */
	String OBJECTS_LFS_ENDPOINT = "/objects/batch"; //$NON-NLS-1$

	/**
	 * @return a {@link Gson} instance suitable for handling this
	 *         {@link Protocol}
	 *
	 * @since 4.11
	 */
	public static Gson gson() {
		return new GsonBuilder()
				.setFieldNamingPolicy(
						FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.disableHtmlEscaping().create();
	}
}
