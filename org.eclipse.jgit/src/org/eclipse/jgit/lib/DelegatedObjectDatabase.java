package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

/**
 * @author me
 *
 */
public class DelegatedObjectDatabase extends ObjectDatabase {
	ObjectDatabase delegate;

	public Map<AnyObjectId, List<ObjectId>> getGrafts() throws IOException {
		return delegate.getGrafts();
	}

	public Map<AnyObjectId, ObjectId> getReplacements() throws IOException {
		return delegate.getReplacements();
	}

	@Override
	public boolean exists() {
		return delegate.exists();
	}

	@Override
	public void create() throws IOException {
		delegate.create();
	}

	@Override
	public ObjectInserter newInserter() {
		return delegate.newInserter();
	}

	@Override
	public ObjectReader newReader() {
		return delegate.newReader();
	}

	@Override
	public void close() {
		throw new IllegalStateException();
	}

	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		return delegate.has(objectId);
	}

	public ObjectLoader open(AnyObjectId objectId) throws IOException {
		return delegate.open(objectId);
	}

	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return delegate.open(objectId, typeHint);
	}

	public ObjectDatabase newCachedDatabase() {
		return this;
	}

	public String toString() {
		return "Delegate" + delegate.toString();
	}

}
