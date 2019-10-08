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

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Checks that a received pack only depends on objects which are reachable from
 * a defined set of references.
 */
public interface ConnectivityChecker {

	/**
	 * Checks connectivity of the commit graph after pack uploading.
	 *
	 * @param connectivityCheckInfo
	 *            Input for the connectivity check.
	 * @param haves
	 *            Set of references known for client.
	 * @param pm
	 *            Monitor to publish progress to.
	 * @throws IOException
	 *             an error occurred during connectivity checking.
	 *
	 */
	void checkConnectivity(ConnectivityCheckInfo connectivityCheckInfo,
			Set<ObjectId> haves, ProgressMonitor pm)
			throws IOException;

	/**
	 * POJO which is used to pass all information which is needed to perform
	 * connectivity check.
	 */
	public static class ConnectivityCheckInfo {
		private Repository repository;

		private PackParser parser;

		private boolean checkObjects;

		private List<ReceiveCommand> commands;

		private RevWalk walk;

		/**
		 * @return database we write the stored objects into.
		 */
		public Repository getRepository() {
			return repository;
		}

		/**
		 * @param repository
		 *            set database we write the stored objects into.
		 */
		public void setRepository(Repository repository) {
			this.repository = repository;
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
		public boolean isCheckObjects() {
			return checkObjects;
		}

		/**
		 * @param checkObjects
		 *            set if checker should check referenced objects outside of
		 *            the received pack are reachable.
		 */
		public void setCheckObjects(boolean checkObjects) {
			this.checkObjects = checkObjects;
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
}
