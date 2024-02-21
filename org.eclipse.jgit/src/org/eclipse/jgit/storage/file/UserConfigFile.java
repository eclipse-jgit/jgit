/*
 * Copyright (C) 2023, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.FS;

/**
 * User (global) git config based on two possible locations,
 * {@code ~/.gitconfig} and {@code $XDG_CONFIG_HOME/git/config}.
 * <p>
 * For reading, both locations are considered, first the XDG file, then the file
 * in the home directory. All updates occur in the last file read that exists,
 * or in the home directory file if neither exists. In other words: if only the
 * XDG file exists, it is updated, otherwise the home directory file is updated.
 * </p>
 *
 * @since 6.7
 */
public class UserConfigFile extends FileBasedConfig {

	private final FileBasedConfig parent;

	/**
	 * Creates a new {@link UserConfigFile}.
	 *
	 * @param parent
	 *            parent {@link Config}; may be {@code null}
	 * @param config
	 *            {@link File} for {@code ~/.gitconfig}
	 * @param xdgConfig
	 *            {@link File} for {@code $XDG_CONFIG_HOME/.gitconfig}
	 * @param fileSystem
	 *            {@link FS} to use for the two files; normally
	 *            {@link FS#DETECTED}
	 */
	public UserConfigFile(Config parent, @NonNull File config,
			@NonNull File xdgConfig, @NonNull FS fileSystem) {
		super(new FileBasedConfig(parent, xdgConfig, fileSystem), config,
				fileSystem);
		this.parent = (FileBasedConfig) getBaseConfig();
	}

	@Override
	public void setStringList(String section, String subsection, String name,
			List<String> values) {
		if (exists() || !parent.exists()) {
			super.setStringList(section, subsection, name, values);
		} else {
			parent.setStringList(section, subsection, name, values);
		}
	}

	@Override
	public void unset(String section, String subsection, String name) {
		if (exists() || !parent.exists()) {
			super.unset(section, subsection, name);
		} else {
			parent.unset(section, subsection, name);
		}
	}

	@Override
	public void unsetSection(String section, String subsection) {
		if (exists() || !parent.exists()) {
			super.unsetSection(section, subsection);
		} else {
			parent.unsetSection(section, subsection);
		}
	}

	@Override
	public boolean removeSection(String section, String subsection) {
		if (exists() || !parent.exists()) {
			return super.removeSection(section, subsection);
		}
		return parent.removeSection(section, subsection);
	}

	@Override
	public boolean isOutdated() {
		return super.isOutdated() || parent.isOutdated();
	}

	@Override
	public void load() throws IOException, ConfigInvalidException {
		if (super.isOutdated()) {
			super.load();
		}
		if (parent.isOutdated()) {
			parent.load();
		}
	}

	@Override
	public void save() throws IOException {
		if (exists() || !parent.exists()) {
			if (exists() || !toText().strip().isEmpty()) {
				super.save();
			}
		} else {
			parent.save();
		}
	}
}
