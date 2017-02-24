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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INSUFFICIENT_STORAGE;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.eclipse.jgit.lfs.lib.Constants.DOWNLOAD;
import static org.eclipse.jgit.lfs.lib.Constants.UPLOAD;
import static org.eclipse.jgit.lfs.lib.Constants.VERIFY;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lfs.errors.LfsBandwidthLimitExceeded;
import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.errors.LfsInsufficientStorage;
import org.eclipse.jgit.lfs.errors.LfsRateLimitExceeded;
import org.eclipse.jgit.lfs.errors.LfsRepositoryNotFound;
import org.eclipse.jgit.lfs.errors.LfsRepositoryReadOnly;
import org.eclipse.jgit.lfs.errors.LfsUnauthorized;
import org.eclipse.jgit.lfs.errors.LfsUnavailable;
import org.eclipse.jgit.lfs.errors.LfsValidationError;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * LFS protocol handler implementing the LFS batch API [1]
 *
 * [1] https://github.com/github/git-lfs/blob/master/docs/api/v1/http-v1-batch.md
 *
 * @since 4.3
 */
public abstract class LfsProtocolServlet extends HttpServlet {
	private static Logger LOG = LoggerFactory
			.getLogger(LfsProtocolServlet.class);

	private static final long serialVersionUID = 1L;

	private static final String CONTENTTYPE_VND_GIT_LFS_JSON =
			"application/vnd.git-lfs+json; charset=utf-8"; //$NON-NLS-1$

	private static final int SC_RATE_LIMIT_EXCEEDED = 429;

	private static final int SC_BANDWIDTH_LIMIT_EXCEEDED = 509;

	private Gson gson = createGson();

	/**
	 * Get the large file repository for the given request and path.
	 *
	 * @param request
	 *            the request
	 * @param path
	 *            the path
	 *
	 * @return the large file repository storing large files.
	 * @throws LfsException
	 *             implementations should throw more specific exceptions to
	 *             signal which type of error occurred:
	 *             <dl>
	 *             <dt>{@link LfsValidationError}</dt>
	 *             <dd>when there is a validation error with one or more of the
	 *             objects in the request</dd>
	 *             <dt>{@link LfsRepositoryNotFound}</dt>
	 *             <dd>when the repository does not exist for the user</dd>
	 *             <dt>{@link LfsRepositoryReadOnly}</dt>
	 *             <dd>when the user has read, but not write access. Only
	 *             applicable when the operation in the request is "upload"</dd>
	 *             <dt>{@link LfsRateLimitExceeded}</dt>
	 *             <dd>when the user has hit a rate limit with the server</dd>
	 *             <dt>{@link LfsBandwidthLimitExceeded}</dt>
	 *             <dd>when the bandwidth limit for the user or repository has
	 *             been exceeded</dd>
	 *             <dt>{@link LfsInsufficientStorage}</dt>
	 *             <dd>when there is insufficient storage on the server</dd>
	 *             <dt>{@link LfsUnavailable}</dt>
	 *             <dd>when LFS is not available</dd>
	 *             <dt>{@link LfsException}</dt>
	 *             <dd>when an unexpected internal server error occurred</dd>
	 *             </dl>
	 * @since 4.5
	 * @deprecated use
	 *             {@link #getLargeFileRepository(LfsRequest, String, String)}
	 */
	@Deprecated
	protected LargeFileRepository getLargeFileRepository(LfsRequest request,
			String path) throws LfsException {
		return getLargeFileRepository(request, path, null);
	}

	/**
	 * Get the large file repository for the given request and path.
	 *
	 * @param request
	 *            the request
	 * @param path
	 *            the path
	 * @param auth
	 *            the Authorization HTTP header
	 *
	 * @return the large file repository storing large files.
	 * @throws LfsException
	 *             implementations should throw more specific exceptions to
	 *             signal which type of error occurred:
	 *             <dl>
	 *             <dt>{@link LfsValidationError}</dt>
	 *             <dd>when there is a validation error with one or more of the
	 *             objects in the request</dd>
	 *             <dt>{@link LfsRepositoryNotFound}</dt>
	 *             <dd>when the repository does not exist for the user</dd>
	 *             <dt>{@link LfsRepositoryReadOnly}</dt>
	 *             <dd>when the user has read, but not write access. Only
	 *             applicable when the operation in the request is "upload"</dd>
	 *             <dt>{@link LfsRateLimitExceeded}</dt>
	 *             <dd>when the user has hit a rate limit with the server</dd>
	 *             <dt>{@link LfsBandwidthLimitExceeded}</dt>
	 *             <dd>when the bandwidth limit for the user or repository has
	 *             been exceeded</dd>
	 *             <dt>{@link LfsInsufficientStorage}</dt>
	 *             <dd>when there is insufficient storage on the server</dd>
	 *             <dt>{@link LfsUnavailable}</dt>
	 *             <dd>when LFS is not available</dd>
	 *             <dt>{@link LfsException}</dt>
	 *             <dd>when an unexpected internal server error occurred</dd>
	 *             </dl>
	 * @since 4.7
	 */
	protected abstract LargeFileRepository getLargeFileRepository(
			LfsRequest request, String path, String auth) throws LfsException;

	/**
	 * LFS request.
	 *
	 * @since 4.5
	 */
	protected static class LfsRequest {
		private String operation;

		private List<LfsObject> objects;

		/**
		 * Get the LFS operation.
		 *
		 * @return the operation
		 */
		public String getOperation() {
			return operation;
		}

		/**
		 * Get the LFS objects.
		 *
		 * @return the objects
		 */
		public List<LfsObject> getObjects() {
			return objects;
		}

		/**
		 * @return true if the operation is upload.
		 * @since 4.7
		 */
		public boolean isUpload() {
			return operation.equals(UPLOAD);
		}

		/**
		 * @return true if the operation is download.
		 * @since 4.7
		 */
		public boolean isDownload() {
			return operation.equals(DOWNLOAD);
		}

		/**
		 * @return true if the operation is verify.
		 * @since 4.7
		 */
		public boolean isVerify() {
			return operation.equals(VERIFY);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		Writer w = new BufferedWriter(
				new OutputStreamWriter(res.getOutputStream(), UTF_8));

		Reader r = new BufferedReader(
				new InputStreamReader(req.getInputStream(), UTF_8));
		LfsRequest request = gson.fromJson(r, LfsRequest.class);
		String path = req.getPathInfo();

		res.setContentType(CONTENTTYPE_VND_GIT_LFS_JSON);
		LargeFileRepository repo = null;
		try {
			repo = getLargeFileRepository(request, path,
					req.getHeader(HDR_AUTHORIZATION));
			if (repo == null) {
				String error = MessageFormat
						.format(LfsText.get().lfsFailedToGetRepository, path);
				LOG.error(error);
				throw new LfsException(error);
			}
			res.setStatus(SC_OK);
			TransferHandler handler = TransferHandler
					.forOperation(request.operation, repo, request.objects);
			gson.toJson(handler.process(), w);
		} catch (LfsValidationError e) {
			sendError(res, w, SC_UNPROCESSABLE_ENTITY, e.getMessage());
		} catch (LfsRepositoryNotFound e) {
			sendError(res, w, SC_NOT_FOUND, e.getMessage());
		} catch (LfsRepositoryReadOnly e) {
			sendError(res, w, SC_FORBIDDEN, e.getMessage());
		} catch (LfsRateLimitExceeded e) {
			sendError(res, w, SC_RATE_LIMIT_EXCEEDED, e.getMessage());
		} catch (LfsBandwidthLimitExceeded e) {
			sendError(res, w, SC_BANDWIDTH_LIMIT_EXCEEDED, e.getMessage());
		} catch (LfsInsufficientStorage e) {
			sendError(res, w, SC_INSUFFICIENT_STORAGE, e.getMessage());
		} catch (LfsUnavailable e) {
			sendError(res, w, SC_SERVICE_UNAVAILABLE, e.getMessage());
		} catch (LfsUnauthorized e) {
			sendError(res, w, SC_UNAUTHORIZED, e.getMessage());
		} catch (LfsException e) {
			sendError(res, w, SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} finally {
			w.flush();
		}
	}

	static class Error {
		String message;

		Error(String m) {
			this.message = m;
		}
	}

	private void sendError(HttpServletResponse rsp, Writer writer, int status,
			String message) {
		rsp.setStatus(status);
		gson.toJson(new Error(message), writer);
	}

	private Gson createGson() {
		return new GsonBuilder()
				.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.disableHtmlEscaping()
				.create();
	}
}
