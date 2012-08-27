/*
 * Copyright (C) 2012, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.storage.file.ReflogReader;
import org.eclipse.jgit.util.FS;


/**
 * A subclassable Repository with delegation.
 *
 * The purpose is to allow specialization of JGit operations without passing
 * parameters around, e.g. replacements of grafts.
 * <p>
 * Beware that events, to the extent they are supported will relate to the
 * underlying repository. Some methods will throw an
 * {@link IllegalStateException} to indicate unexpected behavior.
 */
public class RepositoryDelegator extends Repository {

	private Repository delegate;

	/**
	 * Construct the RepositoryAdapter
	 *
	 * @param delegate
	 *            The actual underlying repository
	 */
	public RepositoryDelegator(final Repository delegate) {
		this.delegate = delegate;
	}

	@Override
	public ListenerList getListenerList() {
		throw new IllegalStateException();
	}

	@Override
	public void fireEvent(RepositoryEvent<?> event) {
		throw new IllegalStateException();
	}

	@Override
	public void create() throws IOException {
		throw new IllegalStateException();
	}

	@Override
	public void create(boolean bare) throws IOException {
		throw new IllegalStateException();
	}

	@Override
	public File getDirectory() {
		return delegate.getDirectory();
	}

	@Override
	public ObjectDatabase getObjectDatabase() {
		return delegate.getObjectDatabase();
	}

	@Override
	public ObjectInserter newObjectInserter() {
		return delegate.newObjectInserter();
	}

	@Override
	public ObjectReader newObjectReader() {
		return delegate.newObjectReader();
	}

	@Override
	public RefDatabase getRefDatabase() {
		return delegate.getRefDatabase();
	}

	@Override
	public GraftsDatabase getGraftsDatabase() {
		return delegate.getGraftsDatabase();
	}

	@Override
	public StoredConfig getConfig() {
		return delegate.getConfig();
	}

	@Override
	public FS getFS() {
		return delegate.getFS();
	}

	@Override
	public boolean hasObject(AnyObjectId objectId) {
		return delegate.hasObject(objectId);
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId)
			throws MissingObjectException, IOException {
		return delegate.open(objectId);
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return delegate.open(objectId, typeHint);
	}

	@Override
	public RefUpdate updateRef(String ref) throws IOException {
		return delegate.updateRef(ref);
	}

	@Override
	public RefUpdate updateRef(String ref, boolean detach) throws IOException {
		return delegate.updateRef(ref, detach);
	}

	@Override
	public RefRename renameRef(String fromRef, String toRef) throws IOException {
		return delegate.renameRef(fromRef, toRef);
	}

	@Override
	public ObjectId resolve(String revstr) throws AmbiguousObjectException,
			IOException {
		return delegate.resolve(revstr);
	}

	@Override
	public void incrementOpen() {
		throw new IllegalStateException();
	}

	@Override
	public void close() {
		delegate.close();
	}

	@Override
	public String toString() {
		return "RepositoryDelegator+" + delegate.toString();
	}

	@Override
	public String getFullBranch() throws IOException {
		return delegate.getFullBranch();
	}

	@Override
	public String getBranch() throws IOException {
		return delegate.getBranch();
	}

	@Override
	public Set<ObjectId> getAdditionalHaves() {
		return delegate.getAdditionalHaves();
	}

	@Override
	public Ref getRef(String name) throws IOException {
		return delegate.getRef(name);
	}

	@Override
	public Map<String, Ref> getAllRefs() {
		return delegate.getAllRefs();
	}

	@Override
	public Map<String, Ref> getTags() {
		return delegate.getTags();
	}

	@Override
	public Ref peel(Ref ref) {
		return delegate.peel(ref);
	}

	@Override
	public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() {
		return delegate.getAllRefsByPeeledObjectId();
	}

	@Override
	public File getIndexFile() throws NoWorkTreeException {
		return delegate.getIndexFile();
	}

	@Override
	public DirCache readDirCache() throws NoWorkTreeException,
			CorruptObjectException, IOException {
		return delegate.readDirCache();
	}

	@Override
	public DirCache lockDirCache() throws NoWorkTreeException,
			CorruptObjectException, IOException {
		return delegate.lockDirCache();
	}

	@Override
	public RepositoryState getRepositoryState() {
		return delegate.getRepositoryState();
	}

	@Override
	public boolean isBare() {
		return delegate.isBare();
	}

	@Override
	public File getWorkTree() throws NoWorkTreeException {
		return delegate.getWorkTree();
	}

	@Override
	public void scanForRepoChanges() throws IOException {
		throw new IllegalStateException();
	}

	@Override
	public void notifyIndexChanged() {
		throw new IllegalStateException();
	}

	@Override
	public ReflogReader getReflogReader(String refName) throws IOException {
		return delegate.getReflogReader(refName);
	}

	@Override
	public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
		return delegate.readMergeCommitMsg();
	}

	@Override
	public void writeMergeCommitMsg(String msg) throws IOException {
		delegate.writeMergeCommitMsg(msg);
	}

	@Override
	public List<ObjectId> readMergeHeads() throws IOException,
			NoWorkTreeException {
		return delegate.readMergeHeads();
	}

	@Override
	public void writeMergeHeads(List<ObjectId> heads) throws IOException {
		delegate.writeMergeHeads(heads);
	}

	public ObjectId readCherryPickHead() throws IOException,
			NoWorkTreeException {
		return delegate.readCherryPickHead();
	}

	public void writeCherryPickHead(ObjectId head) throws IOException {
		delegate.writeCherryPickHead(head);
	}

	public void writeOrigHead(ObjectId head) throws IOException {
		delegate.writeOrigHead(head);
	}

	public ObjectId readOrigHead() throws IOException, NoWorkTreeException {
		return delegate.readOrigHead();
	}

	public String readSquashCommitMsg() throws IOException {
		return delegate.readSquashCommitMsg();
	}

	public void writeSquashCommitMsg(String msg) throws IOException {
		delegate.writeSquashCommitMsg(msg);
	}

	public Map<AnyObjectId, List<ObjectId>> getGrafts() throws IOException {
		return delegate.getGrafts();
	}

	public Map<AnyObjectId, ObjectId> getReplacements() throws IOException {
		return delegate.getReplacements();
	}
}
