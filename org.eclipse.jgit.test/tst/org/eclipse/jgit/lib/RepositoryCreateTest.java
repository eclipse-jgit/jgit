/*
 * Copyright (C) 2015, Ugur Zongur <zongur@gmail.com>
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

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;
import org.junit.Before;

public class RepositoryCreateTest {

	private RepositoryBuilder builder;

	private File NONBAREGITDIR;

	private File BAREGITDIR;

	private File WORKTREE;

	@SuppressWarnings("deprecation")
	private void createRepoAndAssert(boolean callCreateWithBareParam,
			boolean bare) throws IOException {
		final Repository repo = builder.build();
		if (callCreateWithBareParam) {
			repo.create(bare);
		} else {
			repo.create();
		}
		assertEquals(Boolean.valueOf(builder.isBare()), Boolean.valueOf(bare));
		assertEquals(Boolean.valueOf(repo.isBare()), Boolean.valueOf(bare));
	}

	@Before
	public void setUp() throws IOException {
		builder = new RepositoryBuilder();
		NONBAREGITDIR = new File(
				Files.createTempDirectory("JGitRepositoryCreateTest").toFile(),
				".git");
		BAREGITDIR = new File(
				Files.createTempDirectory("JGitRepositoryCreateTest").toFile(),
				"GITDIR");
		WORKTREE = new File(
				Files.createTempDirectory("JGitRepositoryCreateTest").toFile(),
				"WORKTREE");
	}

	@Test(expected = IllegalArgumentException.class)
	public void noParameter_build_Test() throws IOException {
		builder.build();
		// should throw an IllegalArgumentException.
	}

	@Test(expected = IllegalArgumentException.class)
	public void justSetBare_build_Test() throws IOException {
		builder.setBare().build();
		// should throw an IllegalArgumentException.
	}

	@Test(expected = IllegalArgumentException.class)
	public void setWorkTree_setBare_build_Test() throws IOException {
		builder.setWorkTree(WORKTREE).setBare().build();
		// should throw an IllegalArgumentException.
	}

	@Test()
	public void setGitDirImplicitNonBare_create_Test() throws IOException {
		builder.setGitDir(NONBAREGITDIR);
		// .create() should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */false);
	}

	@Test()
	public void setGitDirImplicitNonBare_createNonBare_Test()
			throws IOException {
		builder.setGitDir(NONBAREGITDIR);
		// .create(false) should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setGitDirImplicitNonBare_createBare_Test() throws IOException {
		builder.setGitDir(NONBAREGITDIR);
		// .create(true) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);
	}

	@Test()
	public void setGitDirImplicitBare_create_Test() throws IOException {
		builder.setGitDir(BAREGITDIR);
		// .create() should create a BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */true);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setGitDirImplicitBare_createNonBare_Test() throws IOException {
		builder.setGitDir(BAREGITDIR);
		// .create(false) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test()
	public void setGitDirImplicitBare_createBare_Test() throws IOException {
		builder.setGitDir(BAREGITDIR);
		// .create(true) should create a BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);
	}

	@Test()
	public void setWorkTree_create_Test() throws IOException {
		builder.setWorkTree(WORKTREE);
		// .create() should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */false);
	}

	@Test()
	public void setWorkTree_createNonBare_Test() throws IOException {
		builder.setWorkTree(WORKTREE);
		// .create(false) should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setWorkTree_createBare_Test() throws IOException {
		builder.setWorkTree(WORKTREE);
		// .create(true) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);
	}

	@Test()
	public void setBare_setGitDir_create_Test() throws IOException {
		builder.setBare().setGitDir(NONBAREGITDIR);
		// .create() should create a BARE repository.
		// setBare should override the behavior of inferring bare through folder
		// name
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */true);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setBare_setGitDir_createNonBare_Test() throws IOException {
		builder.setBare().setGitDir(NONBAREGITDIR);
		// .create(false) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test()
	public void setBare_setGitDir_createBare_Test() throws IOException {
		builder.setBare().setGitDir(NONBAREGITDIR);
		// .create(true) should create a BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);

	}

	@Test()
	public void setBare_setWorkTree_create_Test() throws IOException {
		builder.setBare().setWorkTree(WORKTREE);
		// .create() should create a NON-BARE repository.
		// calling setWorkTree after setBare should revert the effects of
		// setBare.
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */false);
	}

	@Test()
	public void setBare_setWorkTree_createNonBare_Test() throws IOException {
		builder.setBare().setWorkTree(WORKTREE);
		// .create(false) should create a NON-BARE repository.
		// calling setWorkTree after setBare should cancel the effects of
		// setBare.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setBare_setWorkTree_createBare_Test() throws IOException {
		builder.setBare().setWorkTree(WORKTREE);
		// .create(true) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);
	}

	@Test() // setting work tree should create a non-bare repository.
	public void setGitDir_setWorkTree_create_Test() throws IOException {
		builder.setGitDir(BAREGITDIR).setWorkTree(WORKTREE);
		// .create() should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */false);
	}

	@Test()
	public void setGitDir_setWorkTree_createNonBare_Test() throws IOException {
		builder.setGitDir(BAREGITDIR).setBare().setWorkTree(WORKTREE);
		// .create(false) should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setGitDir_setWorkTree_createBare_Test() throws IOException {
		builder.setGitDir(BAREGITDIR).setWorkTree(WORKTREE);
		// .create(true) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);
	}


	@Test()
	public void setBare_setGitDir_setWorkTree_create_Test() throws IOException {
		builder.setBare().setGitDir(NONBAREGITDIR).setWorkTree(WORKTREE);
		// .create() should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */false);
	}

	@Test()
	public void setBare_setGitDir_setWorkTree_createNonBare_Test()
			throws IOException {
		builder.setBare().setGitDir(NONBAREGITDIR).setWorkTree(WORKTREE);
		// .create(false) should create a NON-BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setBare_setGitDir_setWorkTree_createBare_Test()
			throws IOException {
		builder.setBare().setGitDir(NONBAREGITDIR).setWorkTree(WORKTREE);
		// .create(true) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);
	}

	@Test()
	public void setGitDir_setWorkTree_setBare_create_Test() throws IOException {
		builder.setGitDir(NONBAREGITDIR).setWorkTree(WORKTREE).setBare();
		// .create() should create a BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */false, /* bare */true);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setGitDir_setWorkTree_setBare_createNonBare_Test()
			throws IOException {
		builder.setGitDir(NONBAREGITDIR).setWorkTree(WORKTREE).setBare();
		// .create(false) should throw an IllegalArgumentException.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */false);
	}

	@Test()
	public void setGitDir_setWorkTree_setBare_createBare_Test()
			throws IOException {
		builder.setGitDir(NONBAREGITDIR).setWorkTree(WORKTREE).setBare();
		// .create(true) should create a BARE repository.
		createRepoAndAssert(/* callCreateWithBareParam */true, /* bare */true);
	}
}
