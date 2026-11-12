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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Object databases that hold large objects moved out of a repository, listed
 * as links in a file under {@code objects/info/}.
 * <p>
 * Each subclass handles one kind of store: it names its file and decides
 * which links it accepts, and its {@link Opener} opens the object database
 * behind a link. The file holds one link per line. {@link ObjectDirectory}
 * consults these databases only after its own packs, its own loose objects
 * and its file alternates have all missed.
 * <p>
 * The file may be created or changed while the repository is open. It is
 * re-read when its snapshot shows a change, and that check is only made when
 * a lookup reaches these databases, so lookups that hit local objects cost
 * nothing extra. Databases stay open across re-reads: only added links are
 * opened and only removed links are closed. A link that fails to open is
 * retried on a later lookup.
 */
abstract class LargeObjectAlternates {
	private static final Logger LOG = LoggerFactory
			.getLogger(LargeObjectAlternates.class);

	/** Opens the object database behind a link. */
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
		 *            the link, already accepted by
		 *            {@link LargeObjectAlternates#accepts(URI)}
		 * @return the database, or {@code null} if no implementation is
		 *         available for the link
		 * @throws IOException
		 *             the database could not be opened
		 */
		@Nullable
		ObjectDatabase open(Config repoConfig, File objectsDir, URI link)
				throws IOException;
	}

	private final File file;

	private final Config config;

	private final File objectsDir;

	private Opener opener;

	private FileSnapshot snapshot = FileSnapshot.DIRTY;

	/** Links read by the last load that opened all of them, else null. */
	@Nullable
	private List<String> loadedLinks;

	/** Open databases by link, in file order. */
	private Map<String, ObjectDatabase> opened = new LinkedHashMap<>();

	private List<ObjectDatabase> databases = Collections.emptyList();

	/**
	 * Create the alternates listed in one file.
	 *
	 * @param config
	 *            configuration of the repository
	 * @param objectsDir
	 *            the repository's {@code objects} directory
	 * @param fileName
	 *            path of the file listing the links, relative to
	 *            {@code objectsDir}
	 * @param defaultOpener
	 *            opener for accepted links
	 */
	LargeObjectAlternates(Config config, File objectsDir, String fileName,
			Opener defaultOpener) {
		this.file = new File(objectsDir, fileName);
		this.config = config;
		this.objectsDir = objectsDir;
		this.opener = defaultOpener;
	}

	/**
	 * Whether a link listed in the file is one this kind of store handles.
	 *
	 * @param link
	 *            a link from the file
	 * @return {@code true} if the link should be opened
	 */
	abstract boolean accepts(URI link);

	/**
	 * Describe the links this kind of store accepts, for log messages.
	 *
	 * @return an example link, e.g. {@code s3://<bucket>/<prefix>}
	 */
	abstract String linkExample();

	/**
	 * Replace the opener (for tests) and force the file to be re-read.
	 *
	 * @param newOpener
	 *            opener to use from now on
	 */
	synchronized void setOpener(Opener newOpener) {
		close();
		opener = newOpener;
	}

	boolean has(AnyObjectId id) {
		for (ObjectDatabase db : databases()) {
			try {
				if (db.has(id)) {
					return true;
				}
			} catch (IOException e) {
				LOG.warn(MessageFormat.format(
						JGitText.get().largeObjectAlternateLookupFailed,
						id.name(), db), e);
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
		closeAll(opened.values());
		opened = new LinkedHashMap<>();
		databases = Collections.emptyList();
		loadedLinks = null;
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
			LOG.warn(MessageFormat.format(
					JGitText.get().largeObjectAlternatesReadFailed,
					file.getAbsolutePath()), e);
			return; // keep the current databases and retry next time
		}

		if (links.equals(loadedLinks)) {
			// Unchanged, e.g. a snapshot taken too soon after the file was
			// written: keep the open databases.
			if (snapshot != FileSnapshot.DIRTY
					&& snapshot.equals(newSnapshot)) {
				snapshot.setClean(newSnapshot);
			} else {
				snapshot = newSnapshot;
			}
			return;
		}

		Map<String, ObjectDatabase> next = new LinkedHashMap<>();
		boolean failed = false;
		for (String line : links) {
			if (next.containsKey(line)) {
				continue;
			}
			ObjectDatabase db = opened.remove(line);
			if (db == null) {
				URI link = parseLink(line);
				if (link == null) {
					continue;
				}
				try {
					db = opener.open(config, objectsDir, link);
				} catch (IOException | RuntimeException e) {
					failed = true;
					LOG.warn(MessageFormat.format(
							JGitText.get().largeObjectAlternateOpenFailed,
							line, file.getAbsolutePath()), e);
					continue;
				}
				if (db == null) {
					LOG.warn(MessageFormat.format(
							JGitText.get().largeObjectAlternateNotAvailable,
							line, file.getAbsolutePath()));
					continue;
				}
			}
			next.put(line, db);
		}

		// Close only the databases whose links were removed.
		closeAll(opened.values());
		opened = next;
		databases = Collections
				.unmodifiableList(new ArrayList<>(next.values()));
		loadedLinks = failed ? null : links;
		snapshot = failed ? FileSnapshot.DIRTY : newSnapshot;
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
			// No file: no alternates of this kind.
		}
		return links;
	}

	@Nullable
	private URI parseLink(String line) {
		try {
			URI link = new URI(line);
			if (accepts(link)) {
				return link;
			}
		} catch (URISyntaxException e) {
			// Reported below.
		}
		LOG.warn(MessageFormat.format(
				JGitText.get().largeObjectAlternateInvalidLink, line,
				file.getAbsolutePath(), linkExample()));
		return null;
	}

	private static void closeAll(Collection<ObjectDatabase> dbs) {
		for (ObjectDatabase db : dbs) {
			db.close();
		}
	}
}
