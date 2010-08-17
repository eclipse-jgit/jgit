/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Thad Hughes <thadh@thad.corp.google.com>
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

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * The configuration file that is stored in the file of the file system.
 */
public class FileBasedConfig extends StoredConfig {
	private final File configFile;
	private volatile long lastModified;
	private final FS fs;

	/**
	 * Create a configuration with no default fallback.
	 *
	 * @param cfgLocation
	 *            the location of the configuration file on the file system
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 */
	public FileBasedConfig(File cfgLocation, FS fs) {
		this(null, cfgLocation, fs);
	}

	/**
	 * The constructor
	 *
	 * @param base
	 *            the base configuration file
	 * @param cfgLocation
	 *            the location of the configuration file on the file system
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 */
	public FileBasedConfig(Config base, File cfgLocation, FS fs) {
		super(base);
		configFile = cfgLocation;
		this.fs = fs;
	}

	@Override
	protected boolean notifyUponTransientChanges() {
		// we will notify listeners upon save()
		return false;
	}

	/** @return location of the configuration file on disk */
	public final File getFile() {
		return configFile;
	}

	/**
	 * Load the configuration as a Git text style configuration file.
	 * <p>
	 * If the file does not exist, this configuration is cleared, and thus
	 * behaves the same as though the file exists, but is empty.
	 *
	 * @throws IOException
	 *             the file could not be read (but does exist).
	 * @throws ConfigInvalidException
	 *             the file is not a properly formatted configuration file.
	 */
	@Override
	public void load() throws IOException, ConfigInvalidException {
		lastModified = getFile().lastModified();
		try {
			fromText(RawParseUtils.decode(IO.readFully(getFile())));
		} catch (FileNotFoundException noFile) {
			clear();
		} catch (IOException e) {
			final IOException e2 = new IOException(MessageFormat.format(JGitText.get().cannotReadFile, getFile()));
			e2.initCause(e);
			throw e2;
		} catch (ConfigInvalidException e) {
			throw new ConfigInvalidException(MessageFormat.format(JGitText.get().cannotReadFile, getFile()), e);
		}
	}

	/**
	 * Save the configuration as a Git text style configuration file.
	 * <p>
	 * <b>Warning:</b> Although this method uses the traditional Git file
	 * locking approach to protect against concurrent writes of the
	 * configuration file, it does not ensure that the file has not been
	 * modified since the last read, which means updates performed by other
	 * objects accessing the same backing file may be lost.
	 *
	 * @throws IOException
	 *             the file could not be written.
	 */
	public void save() throws IOException {
		final byte[] out = Constants.encode(toText());
		final LockFile lf = new LockFile(getFile(), fs);
		if (!lf.lock())
			throw new IOException(MessageFormat.format(JGitText.get().cannotLockFile, getFile()));
		try {
			lf.setNeedStatInformation(true);
			lf.write(out);
			if (!lf.commit())
				throw new IOException(MessageFormat.format(JGitText.get().cannotCommitWriteTo, getFile()));
		} finally {
			lf.unlock();
		}
		lastModified = lf.getCommitLastModified();
		// notify the listeners
		fireConfigChangedEvent();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getFile().getPath() + "]";
	}

	/**
	 * @return returns true if the currently loaded configuration file is older
	 * than the file on disk
	 */
	public boolean isOutdated() {
		return getFile().lastModified() != lastModified;
	}
}
