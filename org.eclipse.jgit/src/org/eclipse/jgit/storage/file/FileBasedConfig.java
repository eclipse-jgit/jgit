/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Thad Hughes <thadh@thad.corp.google.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

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
	private static final Logger LOG = LoggerFactory
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
		final int maxRetries = 5;
		int retryDelayMillis = 20;
		int retries = 0;
		while (true) {
			final FileSnapshot oldSnapshot = snapshot;
			final FileSnapshot newSnapshot;
			// don't use config in this snapshot to avoid endless recursion
			newSnapshot = FileSnapshot.saveNoConfig(getFile());
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
				// might be locked by another process (see exception Javadoc)
				if (retries < maxRetries && configFile.exists()) {
					if (LOG.isDebugEnabled()) {
						LOG.debug(MessageFormat.format(
								JGitText.get().configHandleMayBeLocked,
								Integer.valueOf(retries)), noFile);
					}
					try {
						Thread.sleep(retryDelayMillis);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					retries++;
					retryDelayMillis *= 2; // max wait 1260 ms
					continue;
				}
				if (configFile.exists()) {
					throw noFile;
				}
				clear();
				snapshot = newSnapshot;
				return;
			} catch (IOException e) {
				if (FileUtils.isStaleFileHandle(e)
						&& retries < maxRetries) {
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
