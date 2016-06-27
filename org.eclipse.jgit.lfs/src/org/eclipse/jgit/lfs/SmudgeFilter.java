/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.lfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Built-in LFS smudge filter
 *
 * When content is read from git's object-database and written to the filesystem
 * and this filter is configured for that content, then this filter will replace
 * the content of LFS pointer files with the original content. This happens e.g.
 * when a checkout needs to update a working tree file which is under LFS
 * control. This implementation expects that the origin content is already
 * available in the .git/lfs/objects folder. This implementation will not
 * contact any LFS servers in order to get the missing content.
 *
 * @since 4.6
 */
public class SmudgeFilter extends FilterCommand {
	/**
	 * The factory is responsible for creating instances of {@link SmudgeFilter}
	 */
	public final static FilterCommandFactory FACTORY = new FilterCommandFactory() {
		@Override
		public FilterCommand create(Repository db, InputStream in,
				OutputStream out) throws IOException {
			return new SmudgeFilter(db, in, out);
		}
	};

	/**
	 * Registers this filter in JGit by calling
	 */
	public final static void register() {
		FilterCommandRegistry.register(
				org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
						+ "lfs/smudge", //$NON-NLS-1$
				FACTORY);
	}

	private Lfs lfs;

	/**
	 * @param db
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public SmudgeFilter(Repository db, InputStream in, OutputStream out)
			throws IOException {
		super(in, out);
		lfs = new Lfs(db.getDirectory().toPath().resolve(Constants.LFS));
		LfsPointer res = LfsPointer.parseLfsPointer(in);
		if (res != null) {
			Path mediaFile = lfs.getMediaFile(res.getOid());
			if (Files.exists(mediaFile)) {
				this.in = Files.newInputStream(mediaFile);
			}
		}
	}

	@Override
	public int run() throws IOException {
		int b;
		if (in != null) {
			while ((b = in.read()) != -1) {
				out.write(b);
			}
			in.close();
		}
		out.close();
		return -1;
	}
}
