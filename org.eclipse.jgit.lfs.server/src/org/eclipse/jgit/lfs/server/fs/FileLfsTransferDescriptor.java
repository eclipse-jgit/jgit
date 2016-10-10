/*
 * Copyright (C) 2016, Jacek Centkowski <jcentkowski@collab.net>
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

import java.text.MessageFormat;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;

/**
 * Abstract that provides objects necessary to perform LFS transfer
 */
public abstract class FileLfsTransferDescriptor {
	/**
	 * @param req
	 *            source of transfer data
	 * @return transfer descriptor
	 * @throws IllegalArgumentException
	 *             when transfer couldn't have been retrieved
	 */
	public TransferData getTransferData(HttpServletRequest req)
			throws IllegalArgumentException {
		String info = req.getPathInfo();
		FileLfsRepository repository = getRepositoryFromPath(info);
		AnyLongObjectId id = getObjectFromPath(info);
		return new TransferData(id, repository);
	}

	/**
	 * @param path
	 *            to return object from
	 * @return object based on given path
	 * @throws IllegalArgumentException
	 *             when object cannot be retrieve from given path
	 */
	protected AnyLongObjectId getObjectFromPath(String path)
			throws IllegalArgumentException {
		if (path.length() != 1 + Constants.LONG_OBJECT_ID_STRING_LENGTH) {
			throw new IllegalArgumentException(MessageFormat
					.format(LfsServerText.get().invalidPathInfo, path));
		}

		return LongObjectId.fromString(
				path.substring(1, 1 + Constants.LONG_OBJECT_ID_STRING_LENGTH));
	}

	/**
	 * @param path
	 *            to provide repository from
	 * @return repository based on given path
	 * @throws IllegalArgumentException
	 *             when repository cannot be provided from given path
	 */
	abstract protected FileLfsRepository getRepositoryFromPath(String path)
			throws IllegalArgumentException;

	/**
	 * Default implementation that return provided repository
	 */
	public static class DefaultFileLfsTransferDescriptor
			extends FileLfsTransferDescriptor {
		private final FileLfsRepository repository;

		/**
		 * @param repository
		 *            for transfer
		 */
		public DefaultFileLfsTransferDescriptor(FileLfsRepository repository) {
			this.repository = repository;
		}

		@Override
		protected FileLfsRepository getRepositoryFromPath(String path)
				throws IllegalArgumentException {
			return repository;
		}
	}

	/**
	 * Contains object necessary to perform LFS transfer
	 */
	public static class TransferData {
		/**
		 * LFS object id
		 */
		public final AnyLongObjectId obj;

		/**
		 * LFS repository that contains given object
		 */
		public final FileLfsRepository repository;

		/**
		 * @param obj
		 *            object to be transfered
		 * @param repository
		 *            repository to perform transfer
		 */
		TransferData(AnyLongObjectId obj,
				FileLfsRepository repository) {
			this.obj = obj;
			this.repository = repository;
		}
	}
}
