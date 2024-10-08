/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexConstants;
import org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexFormatException;
import org.eclipse.jgit.internal.storage.file.midx.MultiPackIndexLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traditional file system for multi-pack-index
 * <p>
 * This is the multi-pack-index file representation for a Git object database.
 * Each call to {@link FileMultiPackIndex#get()} will recheck for newer
 * versions.
 */
public class FileMultiPackIndex {
	private final static Logger LOG = LoggerFactory.getLogger(
			FileMultiPackIndex.class);

	private final AtomicReference<MultiPackIndexSnapshot> baseMultiPackIndexSnapshot;

	/**
	 * Initialize a reference to an on-disk multi-pack-index
	 *
	 * @param objectsDir
	 * 		the location of the <code>objects</code> directory.
	 */
	FileMultiPackIndex(File objectsDir) {
		this.baseMultiPackIndexSnapshot = new AtomicReference<>(
				new MultiPackIndexSnapshot(new File(objectsDir,
						MultiPackIndexConstants.MIDX_FILE_NAME)));
	}

	/**
	 * The method will first scan whether the
	 * ".git/objects/pack/multi-pack-index" has been modified, if so, it will
	 * re-parse the file, otherwise it will return the same result as the last
	 * time.
	 *
	 * @return multi-pack-index or null if multi-pack-index file does not exist
	 * 		or corrupt.
	 */
	MultiPackIndex get() {
		MultiPackIndexSnapshot original = baseMultiPackIndexSnapshot.get();
		synchronized (baseMultiPackIndexSnapshot) {
			MultiPackIndexSnapshot o, n;
			do {
				o = baseMultiPackIndexSnapshot.get();
				if (o != original) {
					// Another thread did the scan for us, while we
					// were blocked on the monitor above.
					//
					return o.getMultiPackIndex();
				}
				n = o.refresh();
				if (n == o) {
					return n.getMultiPackIndex();
				}
			} while (!baseMultiPackIndexSnapshot.compareAndSet(o, n));
			return n.getMultiPackIndex();
		}
	}

	private static final class MultiPackIndexSnapshot {
		private final File file;

		private final FileSnapshot snapshot;

		private final MultiPackIndex midx;

		MultiPackIndexSnapshot(@NonNull File file) {
			this(file, null, null);
		}

		MultiPackIndexSnapshot(@NonNull File file, FileSnapshot snapshot,
				MultiPackIndex midx) {
			this.file = file;
			this.snapshot = snapshot;
			this.midx = midx;
		}

		MultiPackIndex getMultiPackIndex() {
			return midx;
		}

		MultiPackIndexSnapshot refresh() {
			// FIXME: This should use the value of `trustFolderStat` to
			//  potentially force the refresh or refresh file attributes prior
			//  to checking existence/modification
			if (midx == null && !file.exists()) {
				// multi-pack-index file does not exist
				return this;
			}
			if (snapshot != null && !snapshot.isModified(file)) {
				// multi-pack-index file was not modified
				return this;
			}
			return new MultiPackIndexSnapshot(file, FileSnapshot.save(file),
					open(file));
		}

		private static MultiPackIndex open(File file) {
			try {
				return MultiPackIndexLoader.open(file);
			} catch (FileNotFoundException noFile) {
				// ignore if file do not exist
				return null;
			} catch (IOException e) {
				if (e instanceof MultiPackIndexFormatException) {
					LOG.warn(MessageFormat.format(
							JGitText.get().corruptMultiPackIndex, file), e);
				} else {
					LOG.error(MessageFormat.format(
							JGitText.get().exceptionWhileLoadingMultiPackIndex,
							file), e);
				}
				return null;
			}
		}
	}
}
