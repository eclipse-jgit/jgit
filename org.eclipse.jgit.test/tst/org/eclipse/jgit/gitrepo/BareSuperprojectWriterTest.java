package org.eclipse.jgit.gitrepo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.gitrepo.BareSuperprojectWriter.BareWriterConfig;
import org.eclipse.jgit.gitrepo.RepoCommand.RemoteReader;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class BareSuperprojectWriterTest extends RepositoryTestCase {

	private static final String SHA1_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	@Override
	public void setUp() throws Exception {
		super.setUp();
	}

	@Test
	public void write_setGitModulesContents() throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject repoProject = new RepoProject("subprojectX", "path/to",
					"refs/heads/branch-x", "remote", "");
			repoProject.setUrl("http://example.com/a");

			RemoteReader mockRemoteReader = mock(RemoteReader.class);
			when(mockRemoteReader.sha1("http://example.com/a",
					"refs/heads/branch-x"))
							.thenReturn(ObjectId.fromString(SHA1_A));

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, mockRemoteReader,
					BareWriterConfig.getDefault(), new HashMap<>());

			RevCommit commit = w.write(Arrays.asList(repoProject));

			String contents = readContents(bareRepo, commit, ".gitmodules");
			List<String> contentLines = Arrays
					.asList(contents.split("\n"));
			assertThat(contentLines.get(0),
					is("[submodule \"subprojectX\"]"));
			assertThat(contentLines.subList(1, contentLines.size()),
					containsInAnyOrder(is("\tbranch = refs/heads/branch-x"),
							is("\tpath = path/to"),
							is("\turl = http://example.com/a")));
		}
	}

	@Test
	public void write_recordSubmoduleLabels() throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject repoProject = new RepoProject("subprojectX", "path/to",
					SHA1_A, "remote", "groupA,groupB");
			repoProject.setUrl("http://example.com/a");

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, null,
					BareWriterConfig.getDefault(), new HashMap<>());

			RevCommit commit = w.write(Arrays.asList(repoProject));

			String contents = readContents(bareRepo, commit, ".gitattributes");
			Optional<String> subprojectXLine = Arrays
					.asList(contents.split("\n")).stream()
					.filter(line -> line.startsWith("/path/to ")).findFirst();
			assertTrue(subprojectXLine.isPresent());
			assertTrue(subprojectXLine.get().contains(" groupA"));
			assertTrue(subprojectXLine.get().contains(" groupB"));
		}
	}

	@Test
	public void write_doNotRecordSubmoduleLabels_noExtraAttr()
			throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject repoProject = new RepoProject("subprojectX", "path/to",
					SHA1_A, "remote", "groupA,groupB");
			repoProject.setUrl("http://example.com/a");

			BareWriterConfig cfg = BareWriterConfig.getDefault();
			cfg.recordSubmoduleLabels = false;

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, null,
					cfg, new HashMap<>());

			RevCommit commit = w.write(Arrays.asList(repoProject));

			// No attributes, no submodule labels -> no .gitattributes files
			String idStr = commit.getId().name() + ":.gitattributes";
			ObjectId modId = bareRepo.resolve(idStr);
			assertTrue(modId == null);
		}
	}

	@Test
	public void write_doNotRecordSubmoduleLabels_withExtraAttr()
			throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject repoProject = new RepoProject("subprojectX", "path/to",
					SHA1_A, "remote", "groupA,groupB");
			repoProject.setUrl("http://example.com/a");

			BareWriterConfig cfg = BareWriterConfig.getDefault();
			cfg.recordSubmoduleLabels = false;

			Map<String, String> gitModulesAttr = new HashMap<>();
			gitModulesAttr.put("a-key", "a-value");

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, null,
					BareWriterConfig.getDefault(), gitModulesAttr);

			RevCommit commit = w.write(Arrays.asList(repoProject));

			String contents = readContents(bareRepo, commit, ".gitattributes");
			Optional<String> gitModulesLine = Arrays
					.asList(contents.split("\n")).stream()
					.filter(line -> line.startsWith("path/to ")).findFirst();
			assertFalse(gitModulesLine.isPresent());
		}
	}

	@Test
	public void write_setGitModulesAttributes()
			throws Exception {
		try (Repository bareRepo = createBareRepository()) {
			RepoProject repoProject = new RepoProject("subprojectX", "path/to",
					SHA1_A, "remote", "groupA,groupB");
			repoProject.setUrl("http://example.com/a");

			Map<String, String> attr = new HashMap<>();
			attr.put("a-key", "a-value");
			attr.put("-b-key", null);

			BareSuperprojectWriter w = new BareSuperprojectWriter(bareRepo,
					null, "refs/heads/master", author, null,
					BareWriterConfig.getDefault(), attr);

			RevCommit commit = w.write(Arrays.asList(repoProject));

			String contents = readContents(bareRepo, commit, ".gitattributes");
			Optional<String> gitModulesLine = Arrays
					.asList(contents.split("\n")).stream()
					.filter(line -> line.startsWith(".gitmodules")).findFirst();
			assertTrue(gitModulesLine.isPresent());
			assertTrue(gitModulesLine.get().contains(" a-key=a-value"));
			assertTrue(gitModulesLine.get().contains(" -b-key"));
		}
	}

	private String readContents(Repository repo, RevCommit commit,
			String path) throws Exception {
		String idStr = commit.getId().name() + ":" + path;
		ObjectId modId = repo.resolve(idStr);
		try (ObjectReader reader = repo.newObjectReader()) {
			return new String(
					reader.open(modId).getCachedBytes(Integer.MAX_VALUE));

		}
	}
}
