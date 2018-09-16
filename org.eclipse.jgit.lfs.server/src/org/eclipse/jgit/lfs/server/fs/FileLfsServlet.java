/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.lfs.server.fs;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lfs.errors.InvalidLongObjectIdException;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Servlet supporting upload and download of large objects as defined by the
 * GitHub Large File Storage extension API extending git to allow separate
 * storage of large files
 * (https://github.com/github/git-lfs/tree/master/docs/api).
 *
 * @since 4.3
 */
@WebServlet(asyncSupported = true)
public class FileLfsServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private final FileLfsRepository repository;

	private final long timeout;

	private static Gson gson = createGson();

	/**
	 * @param repository
	 *            the repository storing the large objects
	 * @param timeout
	 *            timeout for object upload / download in milliseconds
	 */
	public FileLfsServlet(FileLfsRepository repository, long timeout) {
		this.repository = repository;
		this.timeout = timeout;
	}

	/**
	 * Handles object downloads
	 *
	 * @param req
	 *            servlet request
	 * @param rsp
	 *            servlet response
	 * @throws ServletException
	 *             if a servlet-specific error occurs
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	protected void doGet(HttpServletRequest req,
			HttpServletResponse rsp) throws ServletException, IOException {
		AnyLongObjectId obj = getObjectToTransfer(req, rsp);
		if (obj != null) {
			if (repository.getSize(obj) == -1) {
				sendError(rsp, HttpStatus.SC_NOT_FOUND, MessageFormat
						.format(LfsServerText.get().objectNotFound,
								obj.getName()));
				return;
			}
			AsyncContext context = req.startAsync();
			context.setTimeout(timeout);
			rsp.getOutputStream()
					.setWriteListener(new ObjectDownloadListener(repository,
							context, rsp, obj));
		}
	}

	/**
	 * Retrieve object id from request
	 *
	 * @param req
	 *            servlet request
	 * @param rsp
	 *            servlet response
	 * @return object id, or <code>null</code> if the object id could not be
	 *         retrieved
	 * @throws IOException
	 *             if an I/O error occurs
         * @since 4.6
	 */
	protected AnyLongObjectId getObjectToTransfer(HttpServletRequest req,
			HttpServletResponse rsp) throws IOException {
		String info = req.getPathInfo();
		int length = 1 + Constants.LONG_OBJECT_ID_STRING_LENGTH;
		if (info.length() != length) {
			sendError(rsp, HttpStatus.SC_UNPROCESSABLE_ENTITY, MessageFormat
					.format(LfsServerText.get().invalidPathInfo, info));
			return null;
		}
		try {
			return LongObjectId.fromString(info.substring(1, length));
		} catch (InvalidLongObjectIdException e) {
			sendError(rsp, HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getMessage());
			return null;
		}
	}

	/**
	 * Handle object uploads
	 *
	 * @param req
	 *            servlet request
	 * @param rsp
	 *            servlet response
	 * @throws ServletException
	 *             if a servlet-specific error occurs
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	protected void doPut(HttpServletRequest req,
			HttpServletResponse rsp) throws ServletException, IOException {
		AnyLongObjectId id = getObjectToTransfer(req, rsp);
		if (id != null) {
			AsyncContext context = req.startAsync();
			context.setTimeout(timeout);
			req.getInputStream().setReadListener(new ObjectUploadListener(
					repository, context, req, rsp, id));
		}
	}

	static class Error {
		String message;

		Error(String m) {
			this.message = m;
		}
	}

	/**
	 * Send an error response.
	 *
	 * @param rsp
	 *            the servlet response
	 * @param status
	 *            HTTP status code
	 * @param message
	 *            error message
	 * @throws IOException
	 *             on failure to send the response
	 * @since 4.6
	 */
	protected static void sendError(HttpServletResponse rsp, int status, String message)
			throws IOException {
		if (rsp.isCommitted()) {
			rsp.getOutputStream().close();
			return;
		}
		rsp.reset();
		rsp.setStatus(status);
		PrintWriter writer = rsp.getWriter();
		gson.toJson(new Error(message), writer);
		writer.flush();
		writer.close();
		rsp.flushBuffer();
	}

	private static Gson createGson() {
		GsonBuilder gb = new GsonBuilder()
				.setFieldNamingPolicy(
						FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.setPrettyPrinting().disableHtmlEscaping();
		return gb.create();
	}
}
