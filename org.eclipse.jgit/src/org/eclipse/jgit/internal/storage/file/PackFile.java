/*
 * Copyright (c) 2021 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.LongList;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A pack file (or pack related) File.
 *
 * Example: "pack-0123456789012345678901234567890123456789.idx"
 */
public class PackFile extends File {
	private static final long serialVersionUID = 1L;

	private static final String PREFIX = "pack-"; //$NON-NLS-1$

	private final String base; // PREFIX + id i.e.
								// pack-0123456789012345678901234567890123456789

	private final String id; // i.e. 0123456789012345678901234567890123456789

	private final boolean hasOldPrefix;

	private final PackExt packExt;

	private static String createName(String id, PackExt extension) {
		return PREFIX + id + '.' + extension.getExtension();
	}

	/**
	 * Create a PackFile for a pack or related file.
	 *
	 * @param file
	 *            File pointing to the location of the file.
	 */
	public PackFile(File file) {
		this(file.getParentFile(), file.getName());
	}

	/**
	 * Create a PackFile for a pack or related file.
	 *
	 * @param directory
	 *            Directory to create the PackFile in.
	 * @param id
	 *            the {@link ObjectId} for this pack
	 * @param ext
	 *            the <code>packExt</code> of the name.
	 */
	public PackFile(File directory, ObjectId id, PackExt ext) {
		this(directory, id.name(), ext);
	}

	/**
	 * Create a PackFile for a pack or related file.
	 *
	 * @param directory
	 *            Directory to create the PackFile in.
	 * @param id
	 *            the <code>id</code> (40 Hex char) section of the pack name.
	 * @param ext
	 *            the <code>packExt</code> of the name.
	 */
	public PackFile(File directory, String id, PackExt ext) {
		this(directory, createName(id, ext));
	}

	/**
	 * Create a PackFile for a pack or related file.
	 *
	 * @param directory
	 *            Directory to create the PackFile in.
	 * @param name
	 *            Filename (last path section) of the PackFile
	 */
	public PackFile(File directory, String name) {
		super(directory, name);
		int dot = name.lastIndexOf('.');

		if (dot < 0) {
			base = name;
			hasOldPrefix = false;
			packExt = null;
		} else {
			base = name.substring(0, dot);
			String tail = name.substring(dot + 1); // ["old-"] + extension
			packExt = getPackExt(tail);
			String old = tail.substring(0,
					tail.length() - getExtension().length());
			hasOldPrefix = old.equals(getExtPrefix(true));
		}

<<<<<<< HEAD
		id = base.startsWith(PREFIX) ? base.substring(PREFIX.length()) : base;
=======
	/**
	 * Check if an in-memory PackFile exists on the underlying filesystem.
	 *
	 * @return true if the PackFile exists, false otherwise
	 * @throws IOException
	 *             if the PackFile exists but failed to be read.
	 */
	@SuppressWarnings("nls")
	public synchronized boolean exists() throws IOException {
		RandomAccessFile fdOrig = fd;
		try {
			if (fdOrig == null) {
				doOpen();
			}
			read(0, 1);
			return true;
		} catch (PackInvalidException | FileNotFoundException e) {
			LOG.warn("Packfile {} is not accessible", packFile, e);
			return false;
		} catch (IOException e) {
			if (FileUtils.isStaleFileHandle(e)
					|| FileUtils.isStaleFileHandleInCausalChain(e)) {
				LOG.warn("Packfile {} is pointing to a stale file handle",
						packFile, e);
				return false;
			}
			throw e;
		} finally {
			if (fdOrig == null) {
				doClose();
			}
		}
	}

	void resolve(Set<ObjectId> matches, AbbreviatedObjectId id, int matchLimit)
			throws IOException {
		idx().resolve(matches, id, matchLimit);
>>>>>>> 90400ca1e... Verify packfile existence when returned from WindowCursor
	}

	/**
	 * Getter for the field <code>id</code>.
	 *
	 * @return the <code>id</code> (40 Hex char) section of the name.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Getter for the field <code>packExt</code>.
	 *
	 * @return the <code>packExt</code> of the name.
	 */
	public PackExt getPackExt() {
		return packExt;
	}

	/**
	 * Create a new similar PackFile with the given extension instead.
	 *
	 * @param ext
	 *            PackExt the extension to use.
	 * @return a PackFile instance with specified extension
	 */
	public PackFile create(PackExt ext) {
		return new PackFile(getParentFile(), getName(ext));
	}

	/**
	 * Create a new similar PackFile in the given directory.
	 *
	 * @param directory
	 *            Directory to create the new PackFile in.
	 * @return a PackFile in the given directory
	 */
	public PackFile createForDirectory(File directory) {
		return new PackFile(directory, getName(false));
	}

	/**
	 * Create a new similar preserved PackFile in the given directory.
	 *
	 * @param directory
	 *            Directory to create the new PackFile in.
	 * @return a PackFile in the given directory with "old-" prefixing the
	 *         extension
	 */
	public PackFile createPreservedForDirectory(File directory) {
		return new PackFile(directory, getName(true));
	}

	private String getName(PackExt ext) {
		return base + '.' + getExtPrefix(hasOldPrefix) + ext.getExtension();
	}

	private String getName(boolean isPreserved) {
		return base + '.' + getExtPrefix(isPreserved) + getExtension();
	}

	private String getExtension() {
		return packExt == null ? "" : packExt.getExtension(); //$NON-NLS-1$
	}

	private static String getExtPrefix(boolean isPreserved) {
		return isPreserved ? "old-" : ""; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static PackExt getPackExt(String endsWithExtension) {
		for (PackExt ext : PackExt.values()) {
			if (endsWithExtension.endsWith(ext.getExtension())) {
				return ext;
			}
		}
		throw new IllegalArgumentException(MessageFormat.format(
				JGitText.get().unrecognizedPackExtension, endsWithExtension));
	}
}
