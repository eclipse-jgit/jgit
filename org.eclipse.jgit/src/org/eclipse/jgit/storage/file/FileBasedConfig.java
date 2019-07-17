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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The configuration file that is stored in the file of the file system.
 */
public class FileBasedConfig extends StoredConfig {
	private final static Logger LOG = LoggerFactory
			.getLogger(FileBasedConfig.class);

	private final File configFile;

	private final FS fs;

	private boolean utf8Bom;

	private volatile FileSnapshot snapshot;

	private volatile ObjectId hash;

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
		this.snapshot = FileSnapshot.DIRTY;
		this.hash = ObjectId.zeroId();
	}

	/** {@inheritDoc} */
	@Override
	protected boolean notifyUponTransientChanges() {
		// we will notify listeners upon save()
		return false;
	}

	/**
	 * Get location of the configuration file on disk
	 *
	 * @return location of the configuration file on disk
	 */
	public final File getFile() {
		return configFile;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Load the configuration as a Git text style configuration file.
	 * <p>
	 * If the file does not exist, this configuration is cleared, and thus
	 * behaves the same as though the file exists, but is empty.
	 */
	@Override
	public void load() throws IOException, ConfigInvalidException {
		load(true);
	}

	/**
	 * Load the configuration as a Git text style configuration file.
	 * <p>
	 * If the file does not exist, this configuration is cleared, and thus
	 * behaves the same as though the file exists, but is empty.
	 *
	 * @param useFileSnapshotWithConfig
	 *            if {@code true} use the FileSnapshot with config, otherwise
	 *            use it without config
	 * @throws IOException
	 *             if IO failed
	 * @throws ConfigInvalidException
	 *             if config is invalid
	 * @since 5.1.9
	 */
	public void load(boolean useFileSnapshotWithConfig)
			throws IOException, ConfigInvalidException {
		final int maxStaleRetries = 5;
		int retries = 0;
		while (true) {
			final FileSnapshot oldSnapshot = snapshot;
			final FileSnapshot newSnapshot;
			if (useFileSnapshotWithConfig) {
				newSnapshot = FileSnapshot.save(getFile());
			} else {
				// don't use config in this snapshot to avoid endless recursion
				newSnapshot = FileSnapshot.saveNoConfig(getFile());
			}
			try {
				final byte[] in = IO.readFully(getFile());
				final ObjectId newHash = hash(in);
				if (hash.equals(newHash)) {
					if (oldSnapshot.equals(newSnapshot)) {
						oldSnapshot.setClean(newSnapshot);
					} else {
						snapshot = newSnapshot;
					}
				} else {
					final String decoded;
					if (isUtf8(in)) {
						decoded = RawParseUtils.decode(UTF_8,
								in, 3, in.length);
						utf8Bom = true;
					} else {
						decoded = RawParseUtils.decode(in);
					}
					fromText(decoded);
					snapshot = newSnapshot;
					hash = newHash;
				}
				return;
			} catch (FileNotFoundException noFile) {
				if (configFile.exists()) {
					throw noFile;
				}
				clear();
				snapshot = newSnapshot;
				return;
			} catch (IOException e) {
				if (FileUtils.isStaleFileHandle(e)
						&& retries < maxStaleRetries) {
					if (LOG.isDebugEnabled()) {
						LOG.debug(MessageFormat.format(
								JGitText.get().configHandleIsStale,
								Integer.valueOf(retries)), e);
					}
					retries++;
					continue;
				}
				throw new IOException(MessageFormat
						.format(JGitText.get().cannotReadFile, getFile()), e);
			} catch (ConfigInvalidException e) {
				throw new ConfigInvalidException(MessageFormat
						.format(JGitText.get().cannotReadFile, getFile()), e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Save the configuration as a Git text style configuration file.
	 * <p>
	 * <b>Warning:</b> Although this method uses the traditional Git file
	 * locking approach to protect against concurrent writes of the
	 * configuration file, it does not ensure that the file has not been
	 * modified since the last read, which means updates performed by other
	 * objects accessing the same backing file may be lost.
	 */
	@Override
	public void save() throws IOException {
		final byte[] out;
		final String text = toText();
		if (utf8Bom) {
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(0xEF);
			bos.write(0xBB);
			bos.write(0xBF);
			bos.write(text.getBytes(UTF_8));
			out = bos.toByteArray();
		} else {
			out = Constants.encode(text);
		}

		final LockFile lf = new LockFile(getFile());
		if (!lf.lock())
			throw new LockFailedException(getFile());
		try {
			lf.setNeedSnapshot(true);
			lf.write(out);
			if (!lf.commit())
				throw new IOException(MessageFormat.format(JGitText.get().cannotCommitWriteTo, getFile()));
		} finally {
			lf.unlock();
		}
		snapshot = lf.getCommitSnapshot();
		hash = hash(out);
		// notify the listeners
		fireConfigChangedEvent();
	}

	/** {@inheritDoc} */
	@Override
	public void clear() {
		hash = hash(new byte[0]);
		super.clear();
	}

	private static ObjectId hash(byte[] rawText) {
		return ObjectId.fromRaw(Constants.newMessageDigest().digest(rawText));
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getFile().getPath() + "]";
	}

	/**
	 * Whether the currently loaded configuration file is outdated
	 *
	 * @return returns true if the currently loaded configuration file is older
	 *         than the file on disk
	 */
	public boolean isOutdated() {
		return snapshot.isModified(getFile());
	}

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.10
	 */
	@Override
	@Nullable
	protected byte[] readIncludedConfig(String relPath)
			throws ConfigInvalidException {
		final File file;
		if (relPath.startsWith("~/")) { //$NON-NLS-1$
			file = fs.resolve(fs.userHome(), relPath.substring(2));
		} else {
			file = fs.resolve(configFile.getParentFile(), relPath);
		}

		if (!file.exists()) {
			return null;
		}

		try {
			return IO.readFully(file);
		} catch (IOException ioe) {
			throw new ConfigInvalidException(MessageFormat
					.format(JGitText.get().cannotReadFile, relPath), ioe);
		}
	}
}
