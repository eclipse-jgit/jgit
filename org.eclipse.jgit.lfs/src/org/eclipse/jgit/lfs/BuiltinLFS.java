/*
 * Copyright (C) 2017, Markus Duft <markus.duft@ssi-schaefer.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		}
		return loader;
	}

	@Override
	public LfsInputStream applyCleanFilter(Repository db, InputStream input,
			long length, Attribute attribute) throws IOException {
		if (isEnabled(db, attribute)) {
			return new LfsInputStream(LfsBlobFilter.cleanLfsBlob(db, input));
		}
		return new LfsInputStream(input, length);
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

	@Override
	@Nullable
	public PrePushHook getPrePushHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		if (isEnabled(repo)) {
			return new LfsPrePushHook(repo, outputStream, errorStream);
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
