/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.iplog;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;

/** Description of a project. */
class Project {
	/** Sorts projects by unique identities. */
	static final Comparator<Project> COMPARATOR = new Comparator<Project>() {
		public int compare(Project a, Project b) {
			return a.getID().compareTo(b.getID());
		}
	};

	private final String id;

	private final String name;

	private String comments;

	private final Set<String> licenses = new TreeSet<String>();

	private final ObjectIdSubclassMap<ObjectId> skipCommits = new ObjectIdSubclassMap<ObjectId>();

	private String version;

	/**
	 * @param id
	 * @param name
	 */
	Project(String id, String name) {
		this.id = id;
		this.name = name;
	}

	/** @return unique identity of this project. */
	String getID() {
		return id;
	}

	/** @return name of this project. */
	String getName() {
		return name;
	}

	/** @return any additional comments about this project. */
	String getComments() {
		return comments;
	}

	void setComments(String comments) {
		this.comments = comments;
	}

	/** @return the licenses this project is released under. */
	Set<String> getLicenses() {
		return Collections.unmodifiableSet(licenses);
	}

	void addLicense(String licenseName) {
		licenses.add(licenseName);
	}

	void addSkipCommit(AnyObjectId commit) {
		skipCommits.add(commit.copy());
	}

	boolean isSkippedCommit(AnyObjectId commit) {
		return skipCommits.contains(commit);
	}

	String getVersion() {
		return version;
	}

	void setVersion(String v) {
		version = v;
	}

	@Override
	public String toString() {
		return MessageFormat.format(IpLogText.get().projectString, getID(), getName());
	}
}
