/*
 * Copyright (C) 2010, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.Collection;

/**
 * An ObjectDatabase of another {@link Repository}.
 * <p>
 * This {@code ObjectDatabase} wraps around another {@code Repository}'s object
 * database, providing its contents to the caller, and closing the Repository
 * when this database is closed. The primary user of this class is
 * {@link ObjectDirectory}, when the {@code info/alternates} file points at the
 * {@code objects/} directory of another repository.
 */
public final class AlternateRepositoryDatabase extends ObjectDatabase {
	private final Repository repository;

	private final ObjectDatabase odb;

	/**
	 * @param alt
	 *            the alternate repository to wrap and export.
	 */
	public AlternateRepositoryDatabase(final Repository alt) {
		repository = alt;
		odb = repository.getObjectDatabase();
	}

	/** @return the alternate repository objects are borrowed from. */
	public Repository getRepository() {
		return repository;
	}

	public void closeSelf() {
		repository.close();
	}

	public void create() throws IOException {
		repository.create();
	}

	public boolean exists() {
		return odb.exists();
	}

	@Override
	protected boolean hasObject1(final AnyObjectId objectId) {
		return odb.hasObject1(objectId);
	}

	@Override
	protected boolean tryAgain1() {
		return odb.tryAgain1();
	}

	@Override
	protected boolean hasObject2(final String objectName) {
		return odb.hasObject2(objectName);
	}

	@Override
	protected ObjectLoader openObject1(final WindowCursor curs,
			final AnyObjectId objectId) throws IOException {
		return odb.openObject1(curs, objectId);
	}

	@Override
	protected ObjectLoader openObject2(final WindowCursor curs,
			final String objectName, final AnyObjectId objectId)
			throws IOException {
		return odb.openObject2(curs, objectName, objectId);
	}

	@Override
	void openObjectInAllPacks1(final Collection<PackedObjectLoader> out,
			final WindowCursor curs, final AnyObjectId objectId)
			throws IOException {
		odb.openObjectInAllPacks1(out, curs, objectId);
	}

	@Override
	protected ObjectDatabase[] loadAlternates() throws IOException {
		return odb.getAlternates();
	}

	@Override
	protected void closeAlternates(final ObjectDatabase[] alt) {
		// Do nothing; these belong to odb to close, not us.
	}

	@Override
	public ObjectDatabase newCachedDatabase() {
		return odb.newCachedDatabase();
	}
}
