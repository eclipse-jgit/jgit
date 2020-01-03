/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import org.eclipse.jgit.lfs.server.internal.LfsGson;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;

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

	/**
	 * <p>Constructor for FileLfsServlet.</p>
	 *
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
	 * {@inheritDoc}
	 *
	 * Handle object downloads
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
	 * @throws java.io.IOException
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
	 * {@inheritDoc}
	 *
	 * Handle object uploads
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

	/**
	 * Send an error response.
	 *
	 * @param rsp
	 *            the servlet response
	 * @param status
	 *            HTTP status code
	 * @param message
	 *            error message
	 * @throws java.io.IOException
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
		try (PrintWriter writer = rsp.getWriter()) {
			LfsGson.toJson(message, writer);
			writer.flush();
		}
		rsp.flushBuffer();
	}
}
