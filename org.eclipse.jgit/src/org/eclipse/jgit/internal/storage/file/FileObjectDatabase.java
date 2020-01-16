/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.FS;

abstract class FileObjectDatabase extends ObjectDatabase {
	static enum InsertLooseObjectResult {
		INSERTED, EXISTS_PACKED, EXISTS_LOOSE, FAILURE;
	}

	/** {@inheritDoc} */
	@Override
	public ObjectReader newReader() {
		return new WindowCursor(this);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectDirectoryInserter newInserter() {
		return new ObjectDirectoryInserter(this, getConfig());
	}

	abstract void resolve(Set<ObjectId> matches, AbbreviatedObjectId id)
			throws IOException;

	abstract Config getConfig();

	abstract FS getFS();

	abstract Set<ObjectId> getShallowCommits() throws IOException;

	abstract void selectObjectRepresentation(PackWriter packer,
			ObjectToPack otp, WindowCursor curs) throws IOException;

	abstract File getDirectory();

	abstract File fileFor(AnyObjectId id);

	abstract ObjectLoader openObject(WindowCursor curs, AnyObjectId objectId)
			throws IOException;

	abstract long getObjectSize(WindowCursor curs, AnyObjectId objectId)
			throws IOException;

	abstract ObjectLoader openLooseObject(WindowCursor curs, AnyObjectId id)
			throws IOException;

	abstract InsertLooseObjectResult insertUnpackedObject(File tmp,
			ObjectId id, boolean createDuplicate) throws IOException;

	abstract PackFile openPack(File pack) throws IOException;

	abstract Collection<PackFile> getPacks();
}
