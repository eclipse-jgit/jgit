/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.NB;
import org.junit.Before;
import org.junit.Test;

public class CommitGraphWriterTest extends RepositoryTestCase {

	private TestRepository<FileRepository> tr;

	private ByteArrayOutputStream os;

	private CommitGraphWriter writer;

	private RevWalk walk;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		os = new ByteArrayOutputStream();
		tr = new TestRepository<>(db, new RevWalk(db), mockSystemReader);
		walk = new RevWalk(db);
		mockSystemReader.setJGitConfig(new MockConfig());
	}

	@Test
	public void testWriteInEmptyRepo() throws Exception {
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		writer = new CommitGraphWriter(
				GraphCommits.fromWalk(m, Collections.emptySet(), walk));
		writer.write(m, os);
		assertEquals(0, os.size());
	}

	@Test
	public void testWriterWithExtraEdgeList() throws Exception {
		RevCommit root = commit();
		RevCommit a = commit(root);
		RevCommit b = commit(root);
		RevCommit c = commit(root);
		RevCommit tip = commit(a, b, c);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		writer.write(m, os);

		assertEquals(5, graphCommits.size());
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] { 'C', 'G', 'P', 'H', 1, 1, 6, 0 },
				headers);
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_FANOUT,
				NB.decodeInt32(data, 8));
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_LOOKUP,
				NB.decodeInt32(data, 20));
		assertEquals(CommitGraphConstants.CHUNK_ID_COMMIT_DATA,
				NB.decodeInt32(data, 32));
		assertEquals(CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST,
				NB.decodeInt32(data, 44));
		assertEquals(CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_INDEX,
				NB.decodeInt32(data, 56));
		assertEquals(CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_DATA,
				NB.decodeInt32(data, 68));
	}

	@Test
	public void testWriterWithoutExtraEdgeList() throws Exception {
		RevCommit root = commit();
		RevCommit a = commit(root);
		RevCommit b = commit(root);
		RevCommit tip = commit(a, b);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		writer.write(m, os);

		assertEquals(4, graphCommits.size());
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] { 'C', 'G', 'P', 'H', 1, 1, 5, 0 },
				headers);
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_FANOUT,
				NB.decodeInt32(data, 8));
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_LOOKUP,
				NB.decodeInt32(data, 20));
		assertEquals(CommitGraphConstants.CHUNK_ID_COMMIT_DATA,
				NB.decodeInt32(data, 32));
		assertEquals(CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_INDEX,
				NB.decodeInt32(data, 44));
		assertEquals(CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_DATA,
				NB.decodeInt32(data, 56));
	}

	static HashSet<String> changedPathStrings(byte[] data) {
		int oidf_offset = -1;
		int bidx_offset = -1;
		int bdat_offset = -1;
		for (int i = 8; i < data.length - 4; i += 12) {
			switch (NB.decodeInt32(data, i)) {
			case CommitGraphConstants.CHUNK_ID_OID_FANOUT:
				oidf_offset = (int) NB.decodeInt64(data, i + 4);
				break;
			case CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_INDEX:
				bidx_offset = (int) NB.decodeInt64(data, i + 4);
				break;
			case CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_DATA:
				bdat_offset = (int) NB.decodeInt64(data, i + 4);
				break;
			}
		}
		assertTrue(oidf_offset > 0);
		assertTrue(bidx_offset > 0);
		assertTrue(bdat_offset > 0);
		bdat_offset += 12; // skip version, hash count, bits per entry
		int commit_count = NB.decodeInt32(data, oidf_offset + 255 * 4);
		int[] changed_path_length_cumuls = new int[commit_count];
		for (int i = 0; i < commit_count; i++) {
			changed_path_length_cumuls[i] = NB.decodeInt32(data,
					bidx_offset + i * 4);
		}
		HashSet<String> changed_paths = new HashSet<>();
		for (int i = 0; i < commit_count; i++) {
			int prior_cumul = i == 0 ? 0 : changed_path_length_cumuls[i - 1];
			String changed_path = "";
			for (int j = prior_cumul; j < changed_path_length_cumuls[i]; j++) {
				changed_path += data[bdat_offset + j] + ",";
			}
			changed_paths.add(changed_path);
		}
		return changed_paths;
	}

	/**
	 * Expected value generated using the following:
	 *
	 * <pre>
	 * # apply into git-repo: https://lore.kernel.org/git/cover.1684790529.git.jonathantanmy@google.com/
	 * (cd git-repo; make)
	 * git-repo/bin-wrappers/git init tested
	 * (cd tested; touch foo.txt; mkdir -p onedir/twodir; touch onedir/twodir/bar.txt)
	 * git-repo/bin-wrappers/git -C tested add foo.txt onedir
	 * git-repo/bin-wrappers/git -C tested commit -m first_commit
	 * (cd tested; mv foo.txt foo-new.txt; mv onedir/twodir/bar.txt onedir/twodir/bar-new.txt)
	 * git-repo/bin-wrappers/git -C tested add foo-new.txt onedir
	 * git-repo/bin-wrappers/git -C tested commit -a -m second_commit
	 * git-repo/bin-wrappers/git -C tested maintenance run
	 * git-repo/bin-wrappers/git -C tested commit-graph write --changed-paths
	 * (cd tested; $JGIT debug-read-changed-path-filter .git/objects/info/commit-graph)
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testChangedPathFilterRootAndNested() throws Exception {
		RevBlob emptyBlob = tr.blob(new byte[] {});
		RevCommit root = tr.commit(tr.tree(tr.file("foo.txt", emptyBlob),
				tr.file("onedir/twodir/bar.txt", emptyBlob)));
		RevCommit tip = tr.commit(tr.tree(tr.file("foo-new.txt", emptyBlob),
				tr.file("onedir/twodir/bar-new.txt", emptyBlob)), root);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		writer.write(m, os);

		HashSet<String> changedPaths = changedPathStrings(os.toByteArray());
		assertThat(changedPaths, containsInAnyOrder(
				"109,-33,2,60,20,79,-11,116,",
				"119,69,63,-8,0,"));
	}

	/**
	 * Expected value generated using the following:
	 *
	 * <pre>
	 * git -C git-repo checkout todo get version number when it is merged
	 * (cd git-repo; make)
	 * git-repo/bin-wrappers/git init tested
	 * (cd tested; mkdir -p onedir/twodir; touch onedir/twodir/a.txt; touch onedir/twodir/b.txt)
	 * git-repo/bin-wrappers/git -C tested add onedir
	 * git-repo/bin-wrappers/git -C tested commit -m first_commit
	 * (cd tested; mv onedir/twodir/a.txt onedir/twodir/c.txt; mv onedir/twodir/b.txt onedir/twodir/d.txt)
	 * git-repo/bin-wrappers/git -C tested add onedir
	 * git-repo/bin-wrappers/git -C tested commit -a -m second_commit
	 * git-repo/bin-wrappers/git -C tested maintenance run
	 * git-repo/bin-wrappers/git -C tested commit-graph write --changed-paths
	 * (cd tested; $JGIT debug-read-changed-path-filter .git/objects/info/commit-graph)
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testChangedPathFilterOverlappingNested() throws Exception {
		RevBlob emptyBlob = tr.blob(new byte[] {});
		RevCommit root = tr
				.commit(tr.tree(tr.file("onedir/twodir/a.txt", emptyBlob),
						tr.file("onedir/twodir/b.txt", emptyBlob)));
		RevCommit tip = tr
				.commit(tr.tree(tr.file("onedir/twodir/c.txt", emptyBlob),
						tr.file("onedir/twodir/d.txt", emptyBlob)), root);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		writer.write(m, os);

		HashSet<String> changedPaths = changedPathStrings(os.toByteArray());
		assertThat(changedPaths, containsInAnyOrder("61,30,23,-24,1,",
				"-58,-51,-46,60,29,-121,113,90,"));
	}

	/**
	 * Expected value generated using the following:
	 *
	 * <pre>
	 * git -C git-repo checkout todo get version number when it is merged
	 * (cd git-repo; make)
	 * git-repo/bin-wrappers/git init tested
	 * (cd tested; touch 你好)
	 * git-repo/bin-wrappers/git -C tested add 你好
	 * git-repo/bin-wrappers/git -C tested commit -m first_commit
	 * git-repo/bin-wrappers/git -C tested maintenance run
	 * git-repo/bin-wrappers/git -C tested commit-graph write --changed-paths
	 * (cd tested; $JGIT debug-read-changed-path-filter .git/objects/info/commit-graph)
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testChangedPathFilterHighBit() throws Exception {
		RevBlob emptyBlob = tr.blob(new byte[] {});
		// tr.file encodes using UTF-8
		RevCommit root = tr.commit(tr.tree(tr.file("你好", emptyBlob)));

		Set<ObjectId> wants = Collections.singleton(root);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		writer.write(m, os);

		HashSet<String> changedPaths = changedPathStrings(os.toByteArray());
		assertThat(changedPaths, containsInAnyOrder("16,16,"));
	}

	@Test
	public void testChangedPathFilterEmptyChange() throws Exception {
		RevCommit root = commit();

		Set<ObjectId> wants = Collections.singleton(root);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		writer.write(m, os);

		HashSet<String> changedPaths = changedPathStrings(os.toByteArray());
		assertThat(changedPaths, containsInAnyOrder("0,"));
	}

	@Test
	public void testChangedPathFilterManyChanges() throws Exception {
		RevBlob emptyBlob = tr.blob(new byte[] {});
		DirCacheEntry[] entries = new DirCacheEntry[513];
		for (int i = 0; i < entries.length; i++) {
			entries[i] = tr.file(i + ".txt", emptyBlob);
		}

		RevCommit root = tr.commit(tr.tree(entries));

		Set<ObjectId> wants = Collections.singleton(root);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		writer.write(m, os);

		HashSet<String> changedPaths = changedPathStrings(os.toByteArray());
		assertThat(changedPaths, containsInAnyOrder("-1,"));
	}

	@Test
	public void testReuseBloomFilters() throws Exception {
		RevBlob emptyBlob = tr.blob(new byte[] {});
		RevCommit root = tr.commit(tr.tree(tr.file("foo.txt", emptyBlob),
				tr.file("onedir/twodir/bar.txt", emptyBlob)));
		tr.branch("master").update(root);

		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_BLOOM_FILTER, true);
		GC gc = new GC(db);
		gc.gc().get();

		RevCommit tip = tr.commit(tr.tree(tr.file("foo-new.txt", emptyBlob),
				tr.file("onedir/twodir/bar-new.txt", emptyBlob)), root);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, true);
		CommitGraphWriter.Stats stats = writer.write(m, os);

		assertEquals(1, stats.getChangedPathFiltersReused());
		assertEquals(1, stats.getChangedPathFiltersComputed());

		// Expected strings are the same as in
		// #testChangedPathFilterRootAndNested
		HashSet<String> changedPaths = changedPathStrings(os.toByteArray());
		assertThat(changedPaths, containsInAnyOrder(
				"109,-33,2,60,20,79,-11,116,",
				"119,69,63,-8,0,"));
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}

	private static final class MockConfig extends FileBasedConfig {
		private MockConfig() {
			super(null, null);
		}

		@Override
		public void load() throws IOException, ConfigInvalidException {
			// Do nothing
		}

		@Override
		public void save() throws IOException {
			// Do nothing
		}

		@Override
		public boolean isOutdated() {
			return false;
		}

		@Override
		public String toString() {
			return "MockConfig";
		}

		@Override
		public boolean getBoolean(final String section, final String name,
				final boolean defaultValue) {
			if (section.equals(ConfigConstants.CONFIG_CORE_SECTION) && name
					.equals(ConfigConstants.CONFIG_KEY_READ_BLOOM_FILTER)) {
				return true;
			}
			return defaultValue;
		}
	}
}