/*
 * Copyright (C) 2021, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gitrepo;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.gitrepo.RepoCommand.ManifestErrorException;
import org.eclipse.jgit.gitrepo.RepoCommand.RemoteFile;
import org.eclipse.jgit.gitrepo.RepoCommand.RemoteReader;
import org.eclipse.jgit.gitrepo.RepoCommand.RemoteUnavailableException;
import org.eclipse.jgit.gitrepo.RepoProject.CopyFile;
import org.eclipse.jgit.gitrepo.RepoProject.LinkFile;
import org.eclipse.jgit.gitrepo.internal.RepoText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FileUtils;

/**
 * Writes .gitmodules and gitlinks of parsed manifest projects into a bare
 * repository.
 *
 * To write on a regular repository, see {@link RegularSuperprojectWriter}.
 */
class BareSuperprojectWriter {
	private static final int LOCK_FAILURE_MAX_RETRIES = 5;

	// Retry exponentially with delays in this range
	private static final int LOCK_FAILURE_MIN_RETRY_DELAY_MILLIS = 50;

	private static final int LOCK_FAILURE_MAX_RETRY_DELAY_MILLIS = 5000;

	private final Repository repo;

	private final URI targetUri;

	private final String targetBranch;

	private final RemoteReader callback;

	private final BareWriterConfig config;

	private final PersonIdent author;

	private List<ExtraContent> extraContents;

	static class BareWriterConfig {
		boolean ignoreRemoteFailures = false;

		boolean recordRemoteBranch = true;

		boolean recordSubmoduleLabels = true;

		boolean recordShallowSubmodules = true;

		static BareWriterConfig getDefault() {
			return new BareWriterConfig();
		}

		private BareWriterConfig() {
		}
	}

	static class ExtraContent {
		final String path;

		final String content;

		ExtraContent(String path, String content) {
			this.path = path;
			this.content = content;
		}
	}

	BareSuperprojectWriter(Repository repo, URI targetUri,
			String targetBranch,
			PersonIdent author, RemoteReader callback,
			BareWriterConfig config,
			List<ExtraContent> extraContents) {
		assert (repo.isBare());
		this.repo = repo;
		this.targetUri = targetUri;
		this.targetBranch = targetBranch;
		this.author = author;
		this.callback = callback;
		this.config = config;
		this.extraContents = extraContents;
	}

	RevCommit write(List<RepoProject> repoProjects)
			throws GitAPIException {
		ObjectInserter inserter = repo.newObjectInserter();
		IndexWriter treeWriter = new IndexWriter(inserter);

		try (RevWalk rw = new RevWalk(repo)) {
			prepareIndex(repoProjects, treeWriter);
			ObjectId treeId = treeWriter.create();
			long prevDelay = 0;
			for (int i = 0; i < LOCK_FAILURE_MAX_RETRIES - 1; i++) {
				try {
					return commitTreeOnCurrentTip(inserter, rw, treeId);
				} catch (ConcurrentRefUpdateException e) {
					prevDelay = FileUtils.delay(prevDelay,
							LOCK_FAILURE_MIN_RETRY_DELAY_MILLIS,
							LOCK_FAILURE_MAX_RETRY_DELAY_MILLIS);
					Thread.sleep(prevDelay);
					repo.getRefDatabase().refresh();
				}
			}
			// In the last try, just propagate the exceptions
			return commitTreeOnCurrentTip(inserter, rw, treeId);
		} catch (IOException | InterruptedException e) {
			throw new ManifestErrorException(e);
		}
	}

	private void prepareIndex(List<RepoProject> projects, IndexWriter tw)
			throws IOException, GitAPIException {
		Config cfg = new Config();
		StringBuilder attributes = new StringBuilder();
		for (RepoProject proj : projects) {
			String name = proj.getName();
			String path = proj.getPath();
			String url = proj.getUrl();
			ObjectId objectId;
			if (ObjectId.isId(proj.getRevision())) {
				objectId = ObjectId.fromString(proj.getRevision());
			} else {
				objectId = callback.sha1(url, proj.getRevision());
				if (objectId == null && !config.ignoreRemoteFailures) {
					throw new RemoteUnavailableException(url);
				}
				if (config.recordRemoteBranch) {
					// "branch" field is only for non-tag references.
					// Keep tags in "ref" field as hint for other tools.
					String field = proj.getRevision().startsWith(R_TAGS) ? "ref" //$NON-NLS-1$
							: "branch"; //$NON-NLS-1$
					cfg.setString("submodule", name, field, //$NON-NLS-1$
							proj.getRevision());
				}

				if (config.recordShallowSubmodules
						&& proj.getRecommendShallow() != null) {
					// The shallow recommendation is losing information.
					// As the repo manifests stores the recommended
					// depth in the 'clone-depth' field, while
					// git core only uses a binary 'shallow = true/false'
					// hint, we'll map any depth to 'shallow = true'
					cfg.setBoolean("submodule", name, "shallow", //$NON-NLS-1$ //$NON-NLS-2$
							true);
				}
			}
			if (config.recordSubmoduleLabels) {
				StringBuilder rec = new StringBuilder();
				rec.append("/"); //$NON-NLS-1$
				rec.append(path);
				for (String group : proj.getGroups()) {
					rec.append(" "); //$NON-NLS-1$
					rec.append(group);
				}
				rec.append("\n"); //$NON-NLS-1$
				attributes.append(rec.toString());
			}

			URI submodUrl = URI.create(url);
			if (targetUri != null) {
				submodUrl = RepoCommand.relativize(targetUri, submodUrl);
			}
			cfg.setString("submodule", name, "path", path); //$NON-NLS-1$ //$NON-NLS-2$
			cfg.setString("submodule", name, "url", //$NON-NLS-1$ //$NON-NLS-2$
					submodUrl.toString());

			// create gitlink
			if (objectId != null) {
				tw.addGitlink(path, objectId);


				for (CopyFile copyfile : proj.getCopyFiles()) {
					RemoteFile rf = callback.readFileWithMode(url,
							proj.getRevision(), copyfile.src);
					tw.addFileWithMode(copyfile.dest, rf.getContents(),
							rf.getFileMode());
				}
				for (LinkFile linkfile : proj.getLinkFiles()) {
					String link;
					if (linkfile.dest.contains("/")) { //$NON-NLS-1$
						link = FileUtils.relativizeGitPath(
								linkfile.dest.substring(0,
										linkfile.dest.lastIndexOf('/')),
								proj.getPath() + "/" + linkfile.src); //$NON-NLS-1$
					} else {
						link = proj.getPath() + "/" + linkfile.src; //$NON-NLS-1$
					}

					tw.addSymlink(linkfile.dest, link);
				}
			}
		}
		String gitModulesContent = cfg.toText();
		tw.addRegularFile(Constants.DOT_GIT_MODULES, gitModulesContent);

		if (config.recordSubmoduleLabels) {
			tw.addRegularFile(Constants.DOT_GIT_ATTRIBUTES,
					attributes.toString());
		}

		for (ExtraContent ec : extraContents) {
			tw.addRegularFile(ec.path, ec.content);
		}
	}

	private RevCommit commitTreeOnCurrentTip(ObjectInserter inserter,
			RevWalk rw, ObjectId treeId)
			throws IOException, ConcurrentRefUpdateException {
		ObjectId headId = repo.resolve(targetBranch + "^{commit}"); //$NON-NLS-1$
		if (headId != null
				&& rw.parseCommit(headId).getTree().getId().equals(treeId)) {
			// No change. Do nothing.
			return rw.parseCommit(headId);
		}

		CommitBuilder commit = new CommitBuilder();
		commit.setTreeId(treeId);
		if (headId != null) {
			commit.setParentIds(headId);
		}
		commit.setAuthor(author);
		commit.setCommitter(author);
		commit.setMessage(RepoText.get().repoCommitMessage);

		ObjectId commitId = inserter.insert(commit);
		inserter.flush();

		RefUpdate ru = repo.updateRef(targetBranch);
		ru.setNewObjectId(commitId);
		ru.setExpectedOldObjectId(headId != null ? headId : ObjectId.zeroId());
		Result rc = ru.update(rw);
		switch (rc) {
		case NEW:
		case FORCED:
		case FAST_FORWARD:
			// Successful. Do nothing.
			break;
		case REJECTED:
		case LOCK_FAILURE:
			throw new ConcurrentRefUpdateException(MessageFormat.format(
					JGitText.get().cannotLock, targetBranch), ru.getRef(), rc);
		default:
			throw new JGitInternalException(
					MessageFormat.format(JGitText.get().updatingRefFailed,
							targetBranch, commitId.name(), rc));
		}

		return rw.parseCommit(commitId);
	}

	private static class IndexWriter {
		private DirCache index;

		private DirCacheBuilder builder;

		private ObjectInserter inserter;

		IndexWriter(ObjectInserter inserter) {
			this.index = DirCache.newInCore();
			this.builder = index.builder();
			this.inserter = inserter;

		}

		void addRegularFile(String path, String contents) throws IOException {
			addFileWithMode(path, contents.getBytes(UTF_8),
					FileMode.REGULAR_FILE);
		}

		void addSymlink(String path, String link) throws IOException {
			addFileWithMode(path, link.getBytes(UTF_8), FileMode.SYMLINK);
		}

		void addFileWithMode(String path, byte[] contents, FileMode mode)
				throws IOException {
			DirCacheEntry dce = new DirCacheEntry(path);

			ObjectId oid = inserter.insert(Constants.OBJ_BLOB,
					contents);
			dce.setObjectId(oid);
			dce.setFileMode(mode);
			builder.add(dce);
		}

		void addGitlink(String path, ObjectId objectId) {
			DirCacheEntry dce = new DirCacheEntry(path);
			dce.setObjectId(objectId);
			dce.setFileMode(FileMode.GITLINK);
			builder.add(dce);
		}

		/**
		 * Write the tree to the object store and return its id.
		 *
		 * @return object id of the new tree object
		 * @throws UnmergedPathException
		 * @throws IOException
		 */
		ObjectId create() throws UnmergedPathException, IOException {
			builder.finish();
			return index.writeTree(inserter);
		}
	}
}
