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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

/**
 * Manages the {@code .eclipse_iplog} file in a project.
 */
public class IpLogMeta {
	/** Default name of the {@code .eclipse_iplog} file. */
	public static final String IPLOG_CONFIG_FILE = ".eclipse_iplog";

	private static final String S_PROJECT = "project";

	private static final String S_CQ = "CQ";

	private static final String S_CONSUMES = "consumes";

	private static final String S_REVIEW = "review";

	private static final String K_URL = "url";

	private static final String K_NAME = "name";

	private static final String K_VERSION = "version";

	private static final String K_COMMENTS = "comments";

	private static final String K_SKIP_COMMIT = "skipCommit";

	private static final String K_LICENSE = "license";

	private static final String K_DESCRIPTION = "description";

	private static final String K_USE = "use";

	private static final String K_STATE = "state";

	private List<Project> projects = new ArrayList<Project>();

	private List<Project> consumedProjects = new ArrayList<Project>();

	private Set<CQ> cqs = new HashSet<CQ>();

	private String reviewUrl;

	List<Project> getProjects() {
		return projects;
	}

	List<Project> getConsumedProjects() {
		return consumedProjects;
	}

	Set<CQ> getCQs() {
		return cqs;
	}

	String getReviewUrl() {
		return reviewUrl;
	}

	void loadFrom(Config cfg) {
		projects.clear();
		consumedProjects.clear();
		cqs.clear();

		projects.addAll(parseProjects(cfg, S_PROJECT));
		consumedProjects.addAll(parseProjects(cfg, S_CONSUMES));

		for (String id : cfg.getSubsections(S_CQ)) {
			CQ cq = new CQ(Long.parseLong(id));
			cq.setDescription(cfg.getString(S_CQ, id, K_DESCRIPTION));
			cq.setLicense(cfg.getString(S_CQ, id, K_LICENSE));
			cq.setUse(cfg.getString(S_CQ, id, K_USE));
			cq.setState(cfg.getString(S_CQ, id, K_STATE));
			cq.setComments(cfg.getString(S_CQ, id, K_COMMENTS));
			cqs.add(cq);
		}

		reviewUrl = cfg.getString(S_REVIEW, null, K_URL);
	}

	private static List<Project> parseProjects(final Config cfg,
			final String sectionName) {
		final List<Project> dst = new ArrayList<Project>();
		for (String id : cfg.getSubsections(sectionName)) {
			String name = cfg.getString(sectionName, id, K_NAME);
			Project project = new Project(id, name);
			project.setVersion(cfg.getString(sectionName, id, K_VERSION));
			project.setComments(cfg.getString(sectionName, id, K_COMMENTS));

			for (String c : cfg.getStringList(sectionName, id, K_SKIP_COMMIT))
				project.addSkipCommit(ObjectId.fromString(c));
			for (String license : cfg.getStringList(sectionName, id, K_LICENSE))
				project.addLicense(license);
			dst.add(project);
		}
		return dst;
	}

	/**
	 * Query the Eclipse Foundation's IPzilla database for CQ records.
	 * <p>
	 * Updates the local {@code .eclipse_iplog} configuration file with current
	 * information by deleting CQs which are no longer relevant, and adding or
	 * updating any CQs which currently exist in the database.
	 *
	 * @param file
	 *            local file to update with current CQ records.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param base
	 *            base https:// URL of the IPzilla server.
	 * @param username
	 *            username to login to IPzilla as. Must be a Bugzilla username
	 *            of someone authorized to query the project's IPzilla records.
	 * @param password
	 *            password for {@code username}.
	 * @throws IOException
	 *             IPzilla cannot be queried, or the local file cannot be read
	 *             from or written to.
	 * @throws ConfigInvalidException
	 *             the local file cannot be read, as it is not a valid
	 *             configuration file format.
	 */
	public void syncCQs(File file, FS fs, URL base, String username,
			String password) throws IOException, ConfigInvalidException {
		FileUtils.mkdirs(file.getParentFile(), true);

		LockFile lf = new LockFile(file, fs);
		if (!lf.lock())
			throw new IOException(MessageFormat.format(IpLogText.get().cannotLock, file));
		try {
			FileBasedConfig cfg = new FileBasedConfig(file, fs);
			cfg.load();
			loadFrom(cfg);

			IPZillaQuery ipzilla = new IPZillaQuery(base, username, password);
			Set<CQ> current = ipzilla.getCQs(projects);

			for (CQ cq : sort(current, CQ.COMPARATOR)) {
				String id = Long.toString(cq.getID());

				set(cfg, S_CQ, id, K_DESCRIPTION, cq.getDescription());
				set(cfg, S_CQ, id, K_LICENSE, cq.getLicense());
				set(cfg, S_CQ, id, K_USE, cq.getUse());
				set(cfg, S_CQ, id, K_STATE, cq.getState());
				set(cfg, S_CQ, id, K_COMMENTS, cq.getComments());
			}

			for (CQ cq : cqs) {
				if (!current.contains(cq))
					cfg.unsetSection(S_CQ, Long.toString(cq.getID()));
			}

			lf.write(Constants.encode(cfg.toText()));
			if (!lf.commit())
				throw new IOException(MessageFormat.format(IpLogText.get().cannotWrite, file));
		} finally {
			lf.unlock();
		}
	}

	private static void set(Config cfg, String section, String subsection,
			String key, String value) {
		if (value == null || "".equals(value))
			cfg.unset(section, subsection, key);
		else
			cfg.setString(section, subsection, key, value);
	}

	private static <T, Q extends Comparator<T>> Iterable<T> sort(
			Collection<T> objs, Q cmp) {
		ArrayList<T> sorted = new ArrayList<T>(objs);
		Collections.sort(sorted, cmp);
		return sorted;
	}
}
