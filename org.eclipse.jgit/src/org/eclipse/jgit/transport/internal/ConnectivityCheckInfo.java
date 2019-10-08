/*
 * Copyright (c) 2019, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.internal;

import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 *
 * POJO which is used to pass all information which is needed to perform
 * connectivity check.
 *
 * @since 5.6
 */
public class ConnectivityCheckInfo {
	private Repository db;

	private PackParser parser;

	private boolean checkReferencedObjectsAreReachable;

	private List<ReceiveCommand> commands;

	private RevWalk walk;

	/**
	 * @return database we write the stored objects into.
	 */
	public Repository getDb() {
		return db;
	}

	/**
	 * @param db
	 *            set database we write the stored objects into.
	 */
	public void setDb(Repository db) {
		this.db = db;
	}

	/**
	 * @return the parser used to parse pack.
	 */
	public PackParser getParser() {
		return parser;
	}

	/**
	 * @param parser
	 *            the parser to set
	 */
	public void setParser(PackParser parser) {
		this.parser = parser;
	}

	/**
	 * @return if checker should check objects.
	 */
	public boolean isCheckReferencedObjectsAreReachable() {
		return checkReferencedObjectsAreReachable;
	}

	/**
	 * @param checkReferencedObjectsAreReachable
	 *            set if checker should check objects
	 */
	public void setCheckReferencedObjectsAreReachable(
			boolean checkReferencedObjectsAreReachable) {
		this.checkReferencedObjectsAreReachable = checkReferencedObjectsAreReachable;
	}

	/**
	 * @return command received by the current request.
	 */
	public List<ReceiveCommand> getCommands() {
		return commands;
	}

	/**
	 * @param commands
	 *            set command received by the current request.
	 */
	public void setCommands(List<ReceiveCommand> commands) {
		this.commands = commands;
	}

	/**
	 * @param walk
	 *            the walk to parse commits
	 */
	public void setWalk(RevWalk walk) {
		this.walk = walk;
	}

	/**
	 * @return the walk to parse commits
	 */
	public RevWalk getWalk() {
		return walk;
	}
}
