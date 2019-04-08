/*
 * Copyright (C) 2012, Roberto Tyley <roberto.tyley@gmail.com>
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

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ObjectDirectoryTest extends RepositoryTestCase {

	@Parameter
	public Boolean trustFolderStats;

	@Parameters(name= "core.trustfolderstat={0}")
	public static Iterable<? extends Object> data() {
		return Arrays.asList(Boolean.TRUE, Boolean.FALSE);
	}

	@Test
	public void testConcurrentInsertionOfBlobsToTheSameNewFanOutDirectory()
			throws Exception {
		ExecutorService e = Executors.newCachedThreadPool();
		for (int i=0; i < 100; ++i) {
			ObjectDirectory dir = createBareRepository().getObjectDatabase();
			for (Future f : e.invokeAll(blobInsertersForTheSameFanOutDir(dir))) {
				f.get();
			}
		}
	}

	@Test
	public void testShouldNotSearchPacksAgainTheSecondTime() throws Exception {
		FileRepository bareRepository = newTestRepositoryWithOnePackfile();
		ObjectDirectory dir = bareRepository.getObjectDatabase();

		// Make sure that timestamps are modified and read so that a full
		// file snapshot check is performed
		Thread.sleep(3000L);

		assertTrue(dir.searchPacksAgain(dir.packList.get()));
		assertFalse(dir.searchPacksAgain(dir.packList.get()));
	}

	private FileRepository newTestRepositoryWithOnePackfile() throws Exception {
		FileRepository repository = createBareRepository();
		TestRepository<FileRepository> testRepository = new TestRepository<FileRepository>(repository);
		testRepository.commit();
		testRepository.packAndPrune();

		FileBasedConfig repoConfig = repository.getConfig();
		repoConfig.setBoolean(ConfigConstants.CONFIG_CORE_SECTION,null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT,
				trustFolderStats.booleanValue());
		repoConfig.save();

		return repository;
	}

	private Collection<Callable<ObjectId>> blobInsertersForTheSameFanOutDir(
			final ObjectDirectory dir) {
		Callable<ObjectId> callable = new Callable<ObjectId>() {
			public ObjectId call() throws Exception {
				return dir.newInserter().insert(Constants.OBJ_BLOB, new byte[0]);
			}
		};
		return Collections.nCopies(4, callable);
	}

}
