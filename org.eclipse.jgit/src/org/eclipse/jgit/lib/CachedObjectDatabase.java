/*
 * Copyright (C) 2010, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2010, JetBrains s.r.o.
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
 * The base class for object databases that wrap other database instances and
 * might optimize querying for database objects by caching some database
 * dependent information. The instances of this class and its subclasses are
 * returned from the method {@link ObjectDatabase#newCachedDatabase()}. This
 * class could be used in scenarios where database does not changes or when
 * changes in the database while some operation in progress is an acceptable
 * risk.
 *
 * The instance of Object database that delegates all requests to the wrapped
 * database. The instance might be indirectly invalidated if wrapped instance is
 * closed. Closing the delegating instance does not implies closing the wrapped
 * instance. For alternative databases, cached instances are used as well.
 *
 * @author Constantine Plotnikov <constantine.plotnikov@gmail.com>
 */
public class CachedObjectDatabase extends ObjectDatabase {
	/**
	 * The wrapped database instance
	 */
	protected final ObjectDatabase wrapped;

	/**
	 * Create the delegating database instance
	 *
	 * @param wrapped
	 *            the wrapped object database
	 */
	public CachedObjectDatabase(ObjectDatabase wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	protected boolean hasObject1(AnyObjectId objectId) {
		return wrapped.hasObject1(objectId);
	}

	@Override
	protected ObjectLoader openObject1(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		return wrapped.openObject1(curs, objectId);
	}

	@Override
	protected boolean hasObject2(String objectName) {
		return wrapped.hasObject2(objectName);
	}

	@Override
	protected ObjectDatabase[] loadAlternates() throws IOException {
		ObjectDatabase[] loaded = wrapped.getAlternates();
		ObjectDatabase[] result = new ObjectDatabase[loaded.length];
		for (int i = 0; i < loaded.length; i++) {
			result[i] = loaded[i].newCachedDatabase();
		}
		return result;
	}

	@Override
	protected ObjectLoader openObject2(WindowCursor curs, String objectName,
			AnyObjectId objectId) throws IOException {
		return wrapped.openObject2(curs, objectName, objectId);
	}

	@Override
	void openObjectInAllPacks1(Collection<PackedObjectLoader> out,
			WindowCursor curs, AnyObjectId objectId) throws IOException {
		wrapped.openObjectInAllPacks1(out, curs, objectId);
	}

	@Override
	protected boolean tryAgain1() {
		return wrapped.tryAgain1();
	}

	@Override
	public ObjectDatabase newCachedDatabase() {
		// In order not to create unneeded indirection ...
		return wrapped.newCachedDatabase();
	}
}
