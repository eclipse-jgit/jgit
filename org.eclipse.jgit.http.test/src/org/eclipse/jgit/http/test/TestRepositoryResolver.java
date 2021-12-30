/*
 * Copyright (C) 2016, 2017 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.http.test;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * A simple repository resolver for tests.
 */
public final class TestRepositoryResolver
		implements RepositoryResolver<HttpServletRequest> {

	private final TestRepository<Repository> repo;

	private final String repoName;

	/**
	 * Create a new {@link org.eclipse.jgit.http.test.TestRepositoryResolver}
	 * that resolves the given name to the given repository.
	 *
	 * @param repo
	 *            to resolve to
	 * @param repoName
	 *            to match
	 */
	public TestRepositoryResolver(TestRepository<Repository> repo, String repoName) {
		this.repo = repo;
		this.repoName = repoName;
	}

	/** {@inheritDoc} */
	@Override
	public Repository open(HttpServletRequest req, String name)
			throws RepositoryNotFoundException, ServiceNotEnabledException {
		if (!name.equals(repoName)) {
			throw new RepositoryNotFoundException(name);
		}
		Repository db = repo.getRepository();
		db.incrementOpen();
		return db;
	}
}
