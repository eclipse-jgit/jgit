/*
 * Copyright (C) 2026, NVIDIA Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Object databases listed in {@code objects/info/s3-alternates}.
 * <p>
 * The file holds one {@code s3://} link per line. Blank lines and lines
 * starting with {@code #} are ignored; any other line is logged and ignored.
 * {@link ObjectDirectory} consults these databases only after its own packs,
 * its own loose objects and its file alternates have all missed.
 */
class S3Alternates {
	private static final Logger LOG = LoggerFactory
			.getLogger(S3Alternates.class);

	/** Path of the file, relative to the {@code objects} directory. */
	static final String INFO_S3_ALTERNATES = "info/s3-alternates"; //$NON-NLS-1$

	private static final String S3_SCHEME = "s3"; //$NON-NLS-1$

	/** Opens the object database behind an {@code s3://} link. */
	@FunctionalInterface
	interface Opener {
		/**
		 * Open the object database behind a link.
		 * <p>
		 * Must be cheap and must not do network I/O. The returned database
		 * must answer {@code has()} and object sizes from local data, and
		 * loaders it returns must stay usable after the reader that opened
		 * them is closed.
		 *
		 * @param repoConfig
		 *            configuration of the repository
		 * @param objectsDir
		 *            the repository's {@code objects} directory
		 * @param link
		 *            the {@code s3://} link
		 * @return the database, or {@code null} if no implementation is
		 *         available for the link
		 * @throws IOException
		 *             the database could not be opened
		 */
		@Nullable
		ObjectDatabase open(Config repoConfig, File objectsDir, URI link)
				throws IOException;
	}

	/**
	 * Default opener. JGit has no S3 object database yet, so links are
	 * reported as unsupported.
	 */
	static final Opener DEFAULT_OPENER = (cfg, dir, link) -> null;

	private final File file;

	private final Config config;

	private final File objectsDir;

	private Opener opener = DEFAULT_OPENER;

	private FileSnapshot snapshot = FileSnapshot.DIRTY;

	private List<ObjectDatabase> databases = Collections.emptyList();

	S3Alternates(Config config, File objectsDir) {
		this.file = new File(objectsDir, INFO_S3_ALTERNATES);
		this.config = config;
		this.objectsDir = objectsDir;
	}

	/**
	 * Replace the opener (for tests) and force the file to be re-read.
	 *
	 * @param newOpener
	 *            opener to use from now on
	 */
	synchronized void setOpener(Opener newOpener) {
		opener = newOpener;
		snapshot = FileSnapshot.DIRTY;
	}

	boolean has(AnyObjectId id) {
		for (ObjectDatabase db : databases()) {
			try {
				if (db.has(id)) {
					return true;
				}
			} catch (IOException e) {
				LOG.warn(MessageFormat.format(
						JGitText.get().s3AlternateLookupFailed, id.name(), db),
						e);
			}
		}
		return false;
	}

	@Nullable
	ObjectLoader open(AnyObjectId id) throws IOException {
		for (ObjectDatabase db : databases()) {
			try (ObjectReader reader = db.newReader()) {
				if (reader.has(id)) {
					return reader.open(id);
				}
			}
		}
		return null;
	}

	long getObjectSize(AnyObjectId id) throws IOException {
		for (ObjectDatabase db : databases()) {
			try (ObjectReader reader = db.newReader()) {
				if (reader.has(id)) {
					return reader.getObjectSize(id, ObjectReader.OBJ_ANY);
				}
			}
		}
		return -1;
	}

	synchronized void close() {
		closeAll(databases);
		databases = Collections.emptyList();
		snapshot = FileSnapshot.DIRTY;
	}

	private synchronized List<ObjectDatabase> databases() {
		if (snapshot.isModified(file)) {
			reload();
		}
		return databases;
	}

	private void reload() {
		FileSnapshot newSnapshot = FileSnapshot.save(file);
		List<String> links;
		try {
			links = readLinks();
		} catch (IOException e) {
			LOG.warn(MessageFormat.format(JGitText.get().s3AlternatesReadFailed,
					file.getAbsolutePath()), e);
			return; // keep the current databases and retry next time
		}

		List<ObjectDatabase> opened = new ArrayList<>(links.size());
		boolean failed = false;
		for (String line : links) {
			URI link = parseLink(line);
			if (link == null) {
				continue;
			}
			try {
				ObjectDatabase db = opener.open(config, objectsDir, link);
				if (db == null) {
					LOG.warn(MessageFormat.format(
							JGitText.get().s3AlternateNotAvailable, line,
							file.getAbsolutePath()));
				} else {
					opened.add(db);
				}
			} catch (IOException | RuntimeException e) {
				failed = true;
				LOG.warn(MessageFormat.format(
						JGitText.get().s3AlternateOpenFailed, line,
						file.getAbsolutePath()), e);
			}
		}

		List<ObjectDatabase> old = databases;
		databases = Collections.unmodifiableList(opened);
		snapshot = failed ? FileSnapshot.DIRTY : newSnapshot;
		closeAll(old);
	}

	private List<String> readLinks() throws IOException {
		List<String> links = new ArrayList<>();
		try (BufferedReader br = Files.newBufferedReader(file.toPath(),
				UTF_8)) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (!line.isEmpty() && !line.startsWith("#")) { //$NON-NLS-1$
					links.add(line);
				}
			}
		} catch (NoSuchFileException e) {
			// No file: no S3 alternates.
		}
		return links;
	}

	@Nullable
	private URI parseLink(String line) {
		try {
			URI link = new URI(line);
			if (S3_SCHEME.equalsIgnoreCase(link.getScheme())
					&& link.getHost() != null) {
				return link;
			}
		} catch (URISyntaxException e) {
			// Reported below.
		}
		LOG.warn(MessageFormat.format(JGitText.get().s3AlternateInvalidLink,
				line, file.getAbsolutePath()));
		return null;
	}

	private static void closeAll(List<ObjectDatabase> dbs) {
		for (ObjectDatabase db : dbs) {
			db.close();
		}
	}
}
