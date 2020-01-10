/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op;

import static org.eclipse.jgit.niofs.internal.op.commands.PathUtil.normalize;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteListCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.internal.ketch.KetchLeader;
import org.eclipse.jgit.internal.ketch.KetchLeaderCache;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.fs.attribute.FileDiff;
import org.eclipse.jgit.niofs.internal.JGitPathImpl;
import org.eclipse.jgit.niofs.internal.config.ConfigProperties;
import org.eclipse.jgit.niofs.internal.op.commands.BlobAsInputStream;
import org.eclipse.jgit.niofs.internal.op.commands.CherryPick;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.ConflictBranchesChecker;
import org.eclipse.jgit.niofs.internal.op.commands.ConvertRefTree;
import org.eclipse.jgit.niofs.internal.op.commands.CreateBranch;
import org.eclipse.jgit.niofs.internal.op.commands.DeleteBranch;
import org.eclipse.jgit.niofs.internal.op.commands.DiffBranches;
import org.eclipse.jgit.niofs.internal.op.commands.Fetch;
import org.eclipse.jgit.niofs.internal.op.commands.GarbageCollector;
import org.eclipse.jgit.niofs.internal.op.commands.GetCommit;
import org.eclipse.jgit.niofs.internal.op.commands.GetCommonAncestorCommit;
import org.eclipse.jgit.niofs.internal.op.commands.GetFirstCommit;
import org.eclipse.jgit.niofs.internal.op.commands.GetLastCommit;
import org.eclipse.jgit.niofs.internal.op.commands.GetPathInfo;
import org.eclipse.jgit.niofs.internal.op.commands.GetRef;
import org.eclipse.jgit.niofs.internal.op.commands.GetTreeFromRef;
import org.eclipse.jgit.niofs.internal.op.commands.ListCommits;
import org.eclipse.jgit.niofs.internal.op.commands.ListDiffs;
import org.eclipse.jgit.niofs.internal.op.commands.ListPathContent;
import org.eclipse.jgit.niofs.internal.op.commands.ListRefs;
import org.eclipse.jgit.niofs.internal.op.commands.MapDiffContent;
import org.eclipse.jgit.niofs.internal.op.commands.Merge;
import org.eclipse.jgit.niofs.internal.op.commands.Push;
import org.eclipse.jgit.niofs.internal.op.commands.RefTreeUpdateCommand;
import org.eclipse.jgit.niofs.internal.op.commands.ResolveObjectIds;
import org.eclipse.jgit.niofs.internal.op.commands.ResolveRevCommit;
import org.eclipse.jgit.niofs.internal.op.commands.RevertMerge;
import org.eclipse.jgit.niofs.internal.op.commands.SimpleRefUpdateCommand;
import org.eclipse.jgit.niofs.internal.op.commands.Squash;
import org.eclipse.jgit.niofs.internal.op.commands.SyncRemote;
import org.eclipse.jgit.niofs.internal.op.commands.TextualDiffBranches;
import org.eclipse.jgit.niofs.internal.op.commands.UpdateRemoteConfig;
import org.eclipse.jgit.niofs.internal.op.model.CommitContent;
import org.eclipse.jgit.niofs.internal.op.model.CommitHistory;
import org.eclipse.jgit.niofs.internal.op.model.CommitInfo;
import org.eclipse.jgit.niofs.internal.op.model.PathInfo;
import org.eclipse.jgit.niofs.internal.op.model.TextualDiff;
import org.eclipse.jgit.niofs.internal.util.ThrowableSupplier;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitImpl implements Git {

	private static final Logger LOG = LoggerFactory.getLogger(GitImpl.class);
	private static final String DEFAULT_JGIT_RETRY_SLEEP_TIME = "50";
	private static int JGIT_RETRY_TIMES = initRetryValue();
	private static final int JGIT_RETRY_SLEEP_TIME = initSleepTime();
	private boolean isEnabled = false;

	private static int initSleepTime() {
		final ConfigProperties config = new ConfigProperties(System.getProperties());
		return config.get("nio.git.retry.onfail.sleep", DEFAULT_JGIT_RETRY_SLEEP_TIME).getIntValue();
	}

	private static int initRetryValue() {
		final ConfigProperties config = new ConfigProperties(System.getProperties());
		final String osName = config.get("os.name", "any").getValue();
		final String defaultRetryTimes;
		if (osName.toLowerCase().contains("windows")) {
			defaultRetryTimes = "10";
		} else {
			defaultRetryTimes = "0";
		}
		try {
			return config.get("nio.git.retry.onfail.times", defaultRetryTimes).getIntValue();
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private org.eclipse.jgit.api.Git git;
	private KetchLeaderCache leaders;
	private final AtomicBoolean isHeadInitialized = new AtomicBoolean(false);

	public GitImpl(final org.eclipse.jgit.api.Git git) {
		this(git, null);
	}

	public GitImpl(final org.eclipse.jgit.api.Git git, final KetchLeaderCache leaders) {
		this.git = git;
		this.leaders = leaders;
	}

	@Override
	public void convertRefTree() {
		try {
			new ConvertRefTree(this).execute();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public void deleteRef(final Ref ref) throws IOException {
		new DeleteBranch(this, ref).execute();
	}

	@Override
	public Ref getRef(final String ref) {
		return new GetRef(git.getRepository(), ref).execute();
	}

	@Override
	public void push(final CredentialsProvider credentialsProvider, final Map.Entry<String, String> remote,
			final boolean force, final Collection<RefSpec> refSpecs) throws InvalidRemoteException {
		new Push(this, credentialsProvider, remote, force, refSpecs).execute();
	}

	@Override
	public void gc() {
		new GarbageCollector(this).execute();
	}

	@Override
	public RevCommit getCommit(final String commitId) {
		return new GetCommit(this, commitId).execute();
	}

	@Override
	public RevCommit getLastCommit(final String refName) {
		return retryIfNeeded(RuntimeException.class, () -> new GetLastCommit(this, refName).execute());
	}

	@Override
	public RevCommit getLastCommit(final Ref ref) throws IOException {
		return new GetLastCommit(this, ref).execute();
	}

	@Override
	public RevCommit getCommonAncestorCommit(final String branchA, final String branchB) {
		return new GetCommonAncestorCommit(this, getLastCommit(branchA), getLastCommit(branchB)).execute();
	}

	@Override
	public CommitHistory listCommits(final Ref ref, final String path) throws IOException, GitAPIException {
		return new ListCommits(this, ref, path).execute();
	}

	@Override
	public List<RevCommit> listCommits(final String startCommitId, final String endCommitId) {
		return listCommits(new GetCommit(this, startCommitId).execute(), new GetCommit(this, endCommitId).execute());
	}

	@Override
	public List<RevCommit> listCommits(final ObjectId startRange, final ObjectId endRange) {
		return retryIfNeeded(RuntimeException.class,
				() -> new ListCommits(this, startRange, endRange).execute().getCommits());
	}

	@Override
	public Repository getRepository() {
		return git.getRepository();
	}

	public DeleteBranchCommand _branchDelete() {
		return git.branchDelete();
	}

	public ListBranchCommand _branchList() {
		return git.branchList();
	}

	public CreateBranchCommand _branchCreate() {
		return git.branchCreate();
	}

	public FetchCommand _fetch() {
		return git.fetch();
	}

	public GarbageCollectCommand _gc() {
		return git.gc();
	}

	public PushCommand _push() {
		return git.push();
	}

	@Override
	public ObjectId getTreeFromRef(final String treeRef) {
		return new GetTreeFromRef(this, treeRef).execute();
	}

	@Override
	public void fetch(final CredentialsProvider credential, final Map.Entry<String, String> remote,
			final Collection<RefSpec> refSpecs) throws InvalidRemoteException {
		new Fetch(this, credential, remote, refSpecs).execute();
	}

	@Override
	public void syncRemote(final Map.Entry<String, String> remote) throws InvalidRemoteException {
		new SyncRemote(this, remote).execute();
	}

	@Override
	public List<String> merge(final String source, final String target) throws IOException {
		return new Merge(this, source, target).execute();
	}

	@Override
	public List<String> merge(final String source, final String target, final boolean noFastForward)
			throws IOException {
		return new Merge(this, source, target, noFastForward).execute();
	}

	@Override
	public boolean revertMerge(final String source, final String target, final String commonAncestorCommitId,
			final String mergeCommitId) {
		return new RevertMerge(this, source, target, commonAncestorCommitId, mergeCommitId).execute();
	}

	@Override
	public void cherryPick(final JGitPathImpl target, final String... commits) throws IOException {
		new CherryPick(this, target.getRefTree(), commits).execute();
	}

	@Override
	public void cherryPick(final String targetBranch, final String... commitsIDs) throws IOException {
		new CherryPick(this, targetBranch, commitsIDs).execute();
	}

	@Override
	public void createRef(final String source, final String target) {
		new CreateBranch(this, source, target).execute();
	}

	@Override
	public List<FileDiff> diffRefs(final String branchA, final String branchB) {
		return new DiffBranches(this, branchA, branchB).execute();
	}

	@Override
	public List<TextualDiff> textualDiffRefs(final String branchA, final String branchB) {
		return new TextualDiffBranches(this, branchA, branchB).execute();
	}

	@Override
	public List<TextualDiff> textualDiffRefs(final String branchA, final String branchB, final String commitIdBranchA,
			final String commitIdBranchB) {
		return new TextualDiffBranches(this, branchA, branchB, commitIdBranchA, commitIdBranchB).execute();
	}

	@Override
	public List<String> conflictBranchesChecker(final String branchA, final String branchB) {
		return new ConflictBranchesChecker(this, branchA, branchB).execute();
	}

	@Override
	public void squash(final String branch, final String startCommit, final String commitMessage) {
		new Squash(this, branch, startCommit, commitMessage).execute();
	}

	public LogCommand _log() {
		return git.log();
	}

	@Override
	public boolean commit(final String branchName, final CommitInfo commitInfo, final boolean amend,
			final ObjectId originId, final CommitContent content) {
		return new Commit(this, branchName, commitInfo, amend, originId, content).execute();
	}

	@Override
	public List<DiffEntry> listDiffs(final String startCommitId, final String endCommitId) {
		return listDiffs(getCommit(startCommitId).getTree(), getCommit(endCommitId).getTree());
	}

	@Override
	public List<DiffEntry> listDiffs(final ObjectId refA, final ObjectId refB) {
		return new ListDiffs(this, refA, refB).execute();
	}

	@Override
	public Map<String, File> mapDiffContent(final String branch, final String startCommitId, final String endCommitId) {
		return new MapDiffContent(this, branch, startCommitId, endCommitId).execute();
	}

	@Override
	public InputStream blobAsInputStream(final String treeRef, final String path) throws NoSuchFileException {
		return retryIfNeeded(NoSuchFileException.class,
				() -> new BlobAsInputStream(this, treeRef, normalize(path)).execute().get());
	}

	@Override
	public RevCommit getFirstCommit(final Ref ref) throws IOException {
		return new GetFirstCommit(this, ref).execute();
	}

	@Override
	public List<Ref> listRefs() {
		return new ListRefs(git.getRepository()).execute();
	}

	@Override
	public List<ObjectId> resolveObjectIds(final String... commits) {
		return new ResolveObjectIds(this, commits).execute();
	}

	@Override
	public RevCommit resolveRevCommit(final ObjectId objectId) throws IOException {
		return new ResolveRevCommit(git.getRepository(), objectId).execute();
	}

	@Override
	public List<RefSpec> updateRemoteConfig(final Map.Entry<String, String> remote, final Collection<RefSpec> refSpecs)
			throws IOException, URISyntaxException {
		return new UpdateRemoteConfig(this, remote, refSpecs).execute();
	}

	public AddCommand _add() {
		return git.add();
	}

	public CommitCommand _commit() {
		return git.commit();
	}

	public RemoteListCommand _remoteList() {
		return git.remoteList();
	}

	public static CloneCommand _cloneRepository() {
		return org.eclipse.jgit.api.Git.cloneRepository();
	}

	@Override
	public PathInfo getPathInfo(final String branchName, final String path) {
		return retryIfNeeded(RuntimeException.class, () -> new GetPathInfo(this, branchName, path).execute());
	}

	@Override
	public List<PathInfo> listPathContent(final String branchName, final String path) {
		return retryIfNeeded(RuntimeException.class, () -> new ListPathContent(this, branchName, path).execute());
	}

	@Override
	public boolean isHEADInitialized() {
		return isHeadInitialized.get();
	}

	@Override
	public void setHeadAsInitialized() {
		isHeadInitialized.set(true);
	}

	@Override
	public void refUpdate(final String branch, final RevCommit commit)
			throws IOException, ConcurrentRefUpdateException {
		if (getRepository().getRefDatabase() instanceof RefTreeDatabase) {
			new RefTreeUpdateCommand(this, branch, commit).execute();
		} else {
			new SimpleRefUpdateCommand(this, branch, commit).execute();
		}
	}

	@Override
	public KetchLeader getKetchLeader() {
		try {
			return leaders.get(getRepository());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isKetchEnabled() {
		return isEnabled;
	}

	@Override
	public void enableKetch() {
		isEnabled = true;
	}

	@Override
	public void updateRepo(final Repository repo) {
		this.git = new org.eclipse.jgit.api.Git(repo);
	}

	@Override
	public void updateLeaders(final KetchLeaderCache leaders) {
		this.leaders = leaders;
	}

	// just for test purposes
	static void setRetryTimes(int retryTimes) {
		JGIT_RETRY_TIMES = retryTimes;
	}

	public static <E extends Throwable, T> T retryIfNeeded(final Class<E> eclazz, final ThrowableSupplier<T> supplier)
			throws E {
		int i = 0;
		do {
			try {
				return supplier.get();
			} catch (final Throwable ex) {
				if (i < (JGIT_RETRY_TIMES - 1)) {
					try {
						Thread.sleep(JGIT_RETRY_SLEEP_TIME);
					} catch (final InterruptedException ignored) {
					}
					LOG.debug(String.format("Unexpected exception (%d/%d).", i + 1, JGIT_RETRY_TIMES), ex);
				} else {
					LOG.error(String.format("Unexpected exception (%d/%d).", i + 1, JGIT_RETRY_TIMES), ex);
					if (ex.getClass().isAssignableFrom(eclazz)) {
						throw (E) ex;
					}
					throw new RuntimeException(ex);
				}
			}

			i++;
		} while (i < JGIT_RETRY_TIMES);

		return null;
	}
}
