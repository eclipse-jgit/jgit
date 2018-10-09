/*
 * Copyright (C) 2017, Markus Duft <markus.duft@ssi-schaefer.com>
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
import java.io.PrintStream;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.LfsFactory;

/**
 * Implementation of {@link LfsFactory}, using built-in (optional) LFS support.
 *
 * @since 4.11
 */
public class BuiltinLFS extends LfsFactory {

	private BuiltinLFS() {
		SmudgeFilter.register();
		CleanFilter.register();
	}

	/**
	 * Activates the built-in LFS support.
	 */
	public static void register() {
		setInstance(new BuiltinLFS());
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public ObjectLoader applySmudgeFilter(Repository db, ObjectLoader loader,
			Attribute attribute) throws IOException {
		if (isEnabled(db) && (attribute == null || isEnabled(db, attribute))) {
			return LfsBlobFilter.smudgeLfsBlob(db, loader);
		} else {
			return loader;
		}
	}

	@Override
	public LfsInputStream applyCleanFilter(Repository db, InputStream input,
			long length, Attribute attribute) throws IOException {
		if (isEnabled(db, attribute)) {
			return new LfsInputStream(LfsBlobFilter.cleanLfsBlob(db, input));
		} else {
			return new LfsInputStream(input, length);
		}
	}

	@Override
	@Nullable
	public PrePushHook getPrePushHook(Repository repo,
			PrintStream outputStream) {
		if (isEnabled(repo)) {
			return new LfsPrePushHook(repo, outputStream);
		}
		return null;
	}

	/**
	 * @param db
	 *            the repository
	 * @return whether LFS is requested for the given repo.
	 */
	@Override
	public boolean isEnabled(Repository db) {
		if (db == null) {
			return false;
		}
		return db.getConfig().getBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_USEJGITBUILTIN,
				false);
	}

	/**
	 * @param db
	 *            the repository
	 * @param attribute
	 *            the attribute to check
	 * @return whether LFS filter is enabled for the given .gitattribute
	 *         attribute.
	 */
	private boolean isEnabled(Repository db, Attribute attribute) {
		if (attribute == null) {
			return false;
		}
		return isEnabled(db) && ConfigConstants.CONFIG_SECTION_LFS
				.equals(attribute.getValue());
	}

	@Override
	public LfsInstallCommand getInstallCommand() {
		return new InstallBuiltinLfsCommand();
	}

}
