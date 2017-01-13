/*
 * Copyright (C) 2017, Philipp Marx <philippmarx@gmx.de> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class DfsBlockCacheTest {

	@SuppressWarnings("boxing")
	private static void assertThatIsSameBlock(DfsBlock actual,
			DfsBlock expected) {
		assertThat(actual, notNullValue());
		assertThat(actual.pack, is(expected.pack));
		assertThat(actual.start, is(expected.start));
		assertThat(actual.end, is(expected.end));
		assertThat(actual, sameInstance(expected));
	}

	private static DfsBlock randomBlock() {
		DfsPackKey key = new DfsPackKey();
		long pos = 50;
		byte[] buf = new byte[100];
		ThreadLocalRandom.current().nextBytes(buf);

		return new DfsBlock(key, pos, buf);
	}

	private static DfsPackDescription randomPackDescription() {
		DfsRepositoryDescription repoDesc = new DfsRepositoryDescription(
				UUID.randomUUID().toString());
		return new DfsPackDescription(repoDesc, UUID.randomUUID().toString());
	}

	@SuppressWarnings("unchecked")
	private static DfsRepository randomRepository() throws Exception {
		InMemoryRepository repository = new InMemoryRepository(
				new DfsRepositoryDescription(UUID.randomUUID().toString()));
		TestRepository git = new TestRepository<>(repository);
		git.blob(UUID.randomUUID().toString());
		RevCommit commit1 = git.commit().message("0").create();
		git.blob(UUID.randomUUID().toString());
		RevCommit commit2 = git.commit().message("1").parent(commit1).create();
		git.update("master", commit2);

		return repository;
	}

	private DfsBlockCacheConfig config;

	private DfsBlockCache cut;

	@SuppressWarnings("boxing")
	@Test
	public void cachDoesNotExceedConfiguredLimit() throws Exception {
		DfsBlockCacheConfig config1 = new DfsBlockCacheConfig();
		config1.setBlockLimit(1000);
		config1.setBlockSize(256);
		DfsBlockCache.reconfigure(config1);
		DfsBlockCache cut1 = DfsBlockCache.getInstance();

		long bytesLoaded = -10;
		while (bytesLoaded < config1.getBlockLimit()) {
			DfsRepository repository = randomRepository();
			DfsPackFile[] packFiles = repository.getObjectDatabase().getPacks();
			DfsBlock block = cut1.getOrLoad(packFiles[0], 0,
					(DfsReader) repository.newObjectReader());
			bytesLoaded += block.size();

			assertThat(cut1.getCurrentSize(),
					lessThanOrEqualTo(config1.getBlockLimit()));
		}
	}

	@Test
	public void getExistingBlock() {
		DfsBlock block = randomBlock();
		cut.put(block);

		assertThatIsSameBlock(cut.get(block.pack, block.start), block);
	}

	@Test
	public void getNotExistingPackKey() {
		DfsBlock block = randomBlock();
		cut.put(block);

		assertThat(cut.get(new DfsPackKey(), 50), nullValue());
	}

	@Test
	public void getNotExistingPosition() {
		DfsBlock block = randomBlock();
		cut.put(block);

		assertThat(cut.get(block.pack, block.start + block.size() + 1),
				nullValue());
		assertThat(cut.get(block.pack, block.start + block.size() - 1),
				nullValue());
	}

	@Test
	public void getOrCreateExisting() {
		DfsPackDescription packDesc = randomPackDescription();
		DfsPackKey key = new DfsPackKey();
		DfsPackFile packFile1 = cut.getOrCreate(packDesc, key);
		DfsPackFile packFile2 = cut.getOrCreate(packDesc, key);

		assertThat(packFile2, sameInstance(packFile1));
	}

	@Test
	public void getOrCreateExistingWithoutPackKey() {
		DfsPackDescription packDesc = randomPackDescription();
		DfsPackFile packFile1 = cut.getOrCreate(packDesc, new DfsPackKey());
		DfsPackFile packFile2 = cut.getOrCreate(packDesc, null);

		assertThat(packFile2, sameInstance(packFile1));
	}

	@SuppressWarnings("boxing")
	@Test
	public void getOrCreateNotExisting() {
		DfsPackDescription packDesc = randomPackDescription();
		DfsPackKey key = new DfsPackKey();
		DfsPackFile packFile = cut.getOrCreate(packDesc, key);

		assertThat(packFile, notNullValue());
		assertThat(packFile.getCachedSize(), is(0L));
		assertThat(packFile.getPackDescription(), is(packDesc));
		assertThat(packFile.length, is(-1L));
		assertThat(packFile.key, sameInstance(key));
	}

	@Test
	public void getOrCreateNotExistingPackKey() {
		DfsPackDescription packDesc = randomPackDescription();
		DfsPackFile packFile1 = cut.getOrCreate(packDesc, new DfsPackKey());
		DfsPackFile packFile2 = cut.getOrCreate(packDesc, new DfsPackKey());

		assertThat(packFile2, sameInstance(packFile1));
		assertThat(packFile2.getPackDescription(),
				is(packFile1.getPackDescription()));
	}

	@Test
	public void getOrLoadDifferentPositionsExpectsSameBlock() throws Exception {
		DfsRepository repository = randomRepository();
		DfsPackFile[] packFiles = repository.getObjectDatabase().getPacks();
		DfsPackFile packFile = packFiles[0];
		DfsBlock block1 = cut.getOrLoad(packFile, 10,
				(DfsReader) repository.newObjectReader());
		DfsBlock block2 = cut.getOrLoad(packFile, 20,
				(DfsReader) repository.newObjectReader());

		assertThat(block2, sameInstance(block1));
	}

	@SuppressWarnings("boxing")
	@Test
	public void getOrLoadExistingPosition() throws Exception {
		DfsRepository repository = randomRepository();
		DfsPackFile[] packFiles = repository.getObjectDatabase().getPacks();
		DfsPackFile packFile = packFiles[0];
		DfsBlock block = cut.getOrLoad(packFile, 10,
				(DfsReader) repository.newObjectReader());

		assertThat(block, notNullValue());
		assertThat(block.pack, is(packFile.key));
		assertThat(block.start, is(0L));
		assertThat(block.end, is(packFile.length));
		assertThat((long) block.size(), is(packFile.length));

		byte[] copy = new byte[20];
		int length = block.copy(0, copy, 0, 20);
		assertThat(length, is(20));
	}

	@Test
	public void getOrLoadExistingPositionTwiceExpectsSameBlock()
			throws Exception {
		DfsRepository repository = randomRepository();
		DfsPackFile[] packFiles = repository.getObjectDatabase().getPacks();
		DfsPackFile packFile = packFiles[0];
		DfsBlock block1 = cut.getOrLoad(packFile, 10,
				(DfsReader) repository.newObjectReader());
		DfsBlock block2 = cut.getOrLoad(packFile, 10,
				(DfsReader) repository.newObjectReader());

		assertThat(block2, sameInstance(block1));
	}

	@SuppressWarnings("boxing")
	@Test
	public void metrics() {
		DfsBlockCacheConfig config1 = new DfsBlockCacheConfig();
		config1.setBlockLimit(1000);
		config1.setBlockSize(256);
		DfsBlockCache.reconfigure(config1);
		DfsBlockCache cut1 = DfsBlockCache.getInstance();

		DfsBlock block = randomBlock();
		cut1.put(block);

		cut1.get(block.pack, block.start);
		cut1.get(block.pack, block.start);
		cut1.get(block.pack, block.start);
		cut1.get(new DfsPackKey(), 1);
		cut1.get(new DfsPackKey(), 1);
		cut1.get(new DfsPackKey(), 1);

		assertThat(cut1.getHitCount(), is(3L));
		assertThat(cut1.getMissCount(), is(3L));
		assertThat(cut1.getHitRatio(), is(50L));
		assertThat(cut1.getCurrentSize(), is((long) block.size()));
		assertThat(cut1.getFillPercentage(),
				is(cut1.getCurrentSize() * 100 / config1.getBlockLimit()));
		assertThat(cut1.getTotalRequestCount(), is(6L));

		long bytesLoaded = config1.getBlockLimit() * -1;
		while (bytesLoaded < cut1.getBlockSize()) {
			DfsBlock block1 = randomBlock();
			cut1.put(block1);
			bytesLoaded += block1.size();
		}

		assertThat(cut1.getEvictions(), greaterThan(0L));
	}

	@SuppressWarnings("boxing")
	@Test
	public void metricsWithRepository() throws Exception {
		DfsRepository repository = randomRepository();
		DfsBlockCacheConfig config1 = new DfsBlockCacheConfig();
		config1.setBlockLimit(1000);
		config1.setBlockSize(256);
		DfsBlockCache.reconfigure(config1);
		DfsBlockCache cut1 = DfsBlockCache.getInstance();
		DfsPackFile[] packFiles = repository.getObjectDatabase().getPacks();

		cut1.getOrLoad(packFiles[0], 0,
				(DfsReader) repository.newObjectReader());
		cut1.getOrLoad(packFiles[0], 0,
				(DfsReader) repository.newObjectReader());
		cut1.getOrLoad(packFiles[0], 0,
				(DfsReader) repository.newObjectReader());

		assertThat(cut1.getHitCount(), is(2L));
		assertThat(cut1.getMissCount(), is(1L));
		assertThat(cut1.getHitRatio(), is(66L));
		assertThat(cut1.getCurrentSize(), is(packFiles[0].length));
		assertThat(cut1.getFillPercentage(),
				is(cut1.getCurrentSize() * 100 / config1.getBlockLimit()));
		assertThat(cut1.getTotalRequestCount(), is(3L));

		long bytesLoaded = config1.getBlockLimit() * -1;
		while (bytesLoaded < cut1.getBlockSize()) {
			DfsRepository repository1 = randomRepository();
			DfsPackFile[] packFiles1 = repository.getObjectDatabase()
					.getPacks();

			cut1.getOrLoad(packFiles1[0], 0,
					(DfsReader) repository1.newObjectReader());
			bytesLoaded += packFiles1[0].length;
		}

		assertThat(cut1.getEvictions(), greaterThan(0L));
	}

	@Test
	public void putBlockIsAvailable() {
		DfsBlock block = randomBlock();
		cut.put(block);

		assertThatIsSameBlock(cut.get(block.pack, block.start), block);
	}

	@Test
	public void putByParameterIsAvailable() {
		DfsBlock block = randomBlock();
		cut.put(block.pack, block.start, block.size(), block);

		assertThatIsSameBlock(cut.get(block.pack, block.start), block);
	}

	@SuppressWarnings("boxing")
	@Test
	public void removePacksReleasesMemory() throws Exception {
		DfsRepository repository = randomRepository();
		assertThat(cut.getCurrentSize(), greaterThan(0L));

		DfsPackFile[] packFiles = repository.getObjectDatabase().getPacks();
		for (DfsPackFile packFile : packFiles) {
			packFile.close();
		}

		assertThat(cut.getCurrentSize(), is(0L));
	}

	@Before
	public void setup() throws Exception {
		config = new DfsBlockCacheConfig();
		DfsBlockCache.reconfigure(config);
		cut = DfsBlockCache.getInstance();
	}

	@SuppressWarnings("boxing")
	@Test
	public void shouldCopyThroughCache() {
		assertThat(cut.shouldCopyThroughCache(
				(long) (config.getBlockLimit() * config.getStreamRatio() - 1)),
				is(true));
		assertThat(cut.shouldCopyThroughCache(
				(long) (config.getBlockLimit() * config.getStreamRatio())),
				is(true));
		assertThat(cut.shouldCopyThroughCache(
				(long) (config.getBlockLimit() * config.getStreamRatio() + 1)),
				is(false));
	}
}
