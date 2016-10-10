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
import org.eclipse.jgit.lfs.server.fs.FileLfsTransferDescriptor.TransferData;
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

	private final FileLfsTransferDescriptor descriptor;

	private final long timeout;

	private static Gson gson = createGson();

	/**
	 * @param descriptor
	 *            to provide repository and object depending on request
	 * @param timeout
	 *            timeout for object upload / download in milliseconds
	 */
	public FileLfsServlet(FileLfsTransferDescriptor descriptor, long timeout) {
		this.descriptor = descriptor;
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
		TransferData data = getTransferData(req, rsp);
		if (data != null) {
			if (data.repository.getSize(data.obj) == -1) {
				sendError(rsp, HttpStatus.SC_NOT_FOUND, MessageFormat
						.format(LfsServerText.get().objectNotFound,
								data.obj.getName()));
				return;
			}
			AsyncContext context = req.startAsync();
			context.setTimeout(timeout);
			rsp.getOutputStream()
					.setWriteListener(new ObjectDownloadListener(
							data.repository, context, rsp, data.obj));
		}
	}

	private TransferData getTransferData(HttpServletRequest req,
			HttpServletResponse rsp) throws IOException {
		try {
			return descriptor.getTransferData(req);
		} catch (IllegalArgumentException e) {
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
		TransferData data = getTransferData(req, rsp);
		if (data != null) {
			AsyncContext context = req.startAsync();
			context.setTimeout(timeout);
			req.getInputStream().setReadListener(new ObjectUploadListener(
					data.repository, context, req, rsp, data.obj));
		}
	}

	static class Error {
		String message;

		Error(String m) {
			this.message = m;
		}
	}

	static void sendError(HttpServletResponse rsp, int status, String message)
			throws IOException {
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
