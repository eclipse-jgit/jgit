/*
 * Copyright (C) 2014, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gitrepo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.gitrepo.BareSuperprojectWriter.ExtraContent;
import org.eclipse.jgit.gitrepo.ManifestParser.IncludedFileReader;
import org.eclipse.jgit.gitrepo.internal.RepoText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a repo command.
 *
 * This will parse a repo XML manifest, convert it into .gitmodules file and the
 * repository config file.
 *
 * If called against a bare repository, it will replace all the existing content
 * of the repository with the contents populated from the manifest.
 *
 * repo manifest allows projects overlapping, e.g. one project's manifestPath is
 * &quot;foo&quot; and another project's manifestPath is &quot;foo/bar&quot;. This won't
 * work in git submodule, so we'll skip all the sub projects
 * (&quot;foo/bar&quot; in the example) while converting.
 *
 * @see <a href="https://code.google.com/p/git-repo/">git-repo project page</a>
 * @since 3.4
 */
public class RepoCommand extends GitCommand<RevCommit> {


	private String manifestPath;
	private String baseUri;
	private URI targetUri;
	private String groupsParam;
	private String branch;
	private String targetBranch = Constants.HEAD;
	private PersonIdent author;
	private RemoteReader callback;
	private InputStream inputStream;
	private IncludedFileReader includedReader;

	private BareSuperprojectWriter.BareWriterConfig bareWriterConfig = BareSuperprojectWriter.BareWriterConfig
			.getDefault();

	private ProgressMonitor monitor;

	private final List<ExtraContent> extraContents = new ArrayList<>();

	/**
	 * A callback to get ref sha1 of a repository from its uri.
	 *
	 * We provided a default implementation {@link DefaultRemoteReader} to
	 * use ls-remote command to read the sha1 from the repository and clone the
	 * repository to read the file. Callers may have their own quicker
	 * implementation.
	 *
	 * @since 3.4
	 */
	public interface RemoteReader {
		/**
		 * Read a remote ref sha1.
		 *
		 * @param uri
		 *            The URI of the remote repository
		 * @param ref
		 *            Name of the ref to lookup. May be a short-hand form, e.g.
		 *            "master" which is automatically expanded to
		 *            "refs/heads/master" if "refs/heads/master" already exists.
		 * @return the sha1 of the remote repository, or null if the ref does
		 *         not exist.
		 * @throws GitAPIException
		 */
		@Nullable
		public ObjectId sha1(String uri, String ref) throws GitAPIException;

		/**
		 * Read a file from a remote repository.
		 *
		 * @param uri
		 *            The URI of the remote repository
		 * @param ref
		 *            The ref (branch/tag/etc.) to read
		 * @param path
		 *            The relative path (inside the repo) to the file to read
		 * @return the file content.
		 * @throws GitAPIException
		 * @throws IOException
		 * @since 3.5
		 *
		 * @deprecated Use {@link #readFileWithMode(String, String, String)}
		 *             instead
		 */
		@Deprecated
		public default byte[] readFile(String uri, String ref, String path)
				throws GitAPIException, IOException {
			return readFileWithMode(uri, ref, path).getContents();
		}

		/**
		 * Read contents and mode (i.e. permissions) of the file from a remote
		 * repository.
		 *
		 * @param uri
		 *            The URI of the remote repository
		 * @param ref
		 *            Name of the ref to lookup. May be a short-hand form, e.g.
		 *            "master" which is automatically expanded to
		 *            "refs/heads/master" if "refs/heads/master" already exists.
		 * @param path
		 *            The relative path (inside the repo) to the file to read
		 * @return The contents and file mode of the file in the given
		 *         repository and branch. Never null.
		 * @throws GitAPIException
		 *             If the ref have an invalid or ambiguous name, or it does
		 *             not exist in the repository,
		 * @throws IOException
		 *             If the object does not exist or is too large
		 * @since 5.2
		 */
		@NonNull
		public RemoteFile readFileWithMode(String uri, String ref, String path)
				throws GitAPIException, IOException;
	}

	/**
	 * Read-only view of contents and file mode (i.e. permissions) for a file in
	 * a remote repository.
	 *
	 * @since 5.2
	 */
	public static final class RemoteFile {
		@NonNull
		private final byte[] contents;

		@NonNull
		private final FileMode fileMode;

		/**
		 * @param contents
		 *            Raw contents of the file.
		 * @param fileMode
		 *            Git file mode for this file (e.g. executable or regular)
		 */
		public RemoteFile(@NonNull byte[] contents,
				@NonNull FileMode fileMode) {
			this.contents = Objects.requireNonNull(contents);
			this.fileMode = Objects.requireNonNull(fileMode);
		}

		/**
		 * Contents of the file.
		 * <p>
		 * Callers who receive this reference must not modify its contents (as
		 * it can point to internal cached data).
		 *
		 * @return Raw contents of the file. Do not modify it.
		 */
		@NonNull
		public byte[] getContents() {
			return contents;
		}

		/**
		 * @return Git file mode for this file (e.g. executable or regular)
		 */
		@NonNull
		public FileMode getFileMode() {
			return fileMode;
		}

	}

	/** A default implementation of {@link RemoteReader} callback. */
	public static class DefaultRemoteReader implements RemoteReader {

		@Override
		public ObjectId sha1(String uri, String ref) throws GitAPIException {
			Map<String, Ref> map = Git
					.lsRemoteRepository()
					.setRemote(uri)
					.callAsMap();
			Ref r = RefDatabase.findRef(map, ref);
			return r != null ? r.getObjectId() : null;
		}

		@Override
		public RemoteFile readFileWithMode(String uri, String ref, String path)
				throws GitAPIException, IOException {
			File dir = FileUtils.createTempDir("jgit_", ".git", null); //$NON-NLS-1$ //$NON-NLS-2$
			try (Git git = Git.cloneRepository().setBare(true).setDirectory(dir)
					.setURI(uri).call()) {
				Repository repo = git.getRepository();
				ObjectId refCommitId = sha1(uri, ref);
				if (refCommitId == null) {
					throw new InvalidRefNameException(MessageFormat
							.format(JGitText.get().refNotResolved, ref));
				}
				RevCommit commit = repo.parseCommit(refCommitId);
				TreeWalk tw = TreeWalk.forPath(repo, path, commit.getTree());

				// TODO(ifrade): Cope better with big files (e.g. using
				// InputStream instead of byte[])
				return new RemoteFile(
						tw.getObjectReader().open(tw.getObjectId(0))
								.getCachedBytes(Integer.MAX_VALUE),
						tw.getFileMode(0));
			} finally {
				FileUtils.delete(dir, FileUtils.RECURSIVE);
			}
		}
	}

	@SuppressWarnings("serial")
	static class ManifestErrorException extends GitAPIException {
		ManifestErrorException(Throwable cause) {
			super(RepoText.get().invalidManifest, cause);
		}
	}

	@SuppressWarnings("serial")
	static class RemoteUnavailableException extends GitAPIException {
		RemoteUnavailableException(String uri) {
			super(MessageFormat.format(RepoText.get().errorRemoteUnavailable, uri));
		}
	}

	/**
	 * Constructor for RepoCommand
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	public RepoCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set path to the manifest XML file.
	 * <p>
	 * Calling {@link #setInputStream} will ignore the path set here.
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public RepoCommand setPath(String path) {
		this.manifestPath = path;
		return this;
	}

	/**
	 * Set the input stream to the manifest XML.
	 * <p>
	 * Setting inputStream will ignore the path set. It will be closed in
	 * {@link #call}.
	 *
	 * @param inputStream a {@link java.io.InputStream} object.
	 * @return this command
	 * @since 3.5
	 */
	public RepoCommand setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;
		return this;
	}

	/**
	 * Set base URI of the paths inside the XML. This is typically the name of
	 * the directory holding the manifest repository, eg. for
	 * https://android.googlesource.com/platform/manifest, this should be
	 * /platform (if you would run this on android.googlesource.com) or
	 * https://android.googlesource.com/platform elsewhere.
	 *
	 * @param uri
	 *            the base URI
	 * @return this command
	 */
	public RepoCommand setURI(String uri) {
		this.baseUri = uri;
		return this;
	}

	/**
	 * Set the URI of the superproject (this repository), so the .gitmodules
	 * file can specify the submodule URLs relative to the superproject.
	 *
	 * @param uri
	 *            the URI of the repository holding the superproject.
	 * @return this command
	 * @since 4.8
	 */
	public RepoCommand setTargetURI(String uri) {
		// The repo name is interpreted as a directory, for example
		// Gerrit (http://gerrit.googlesource.com/gerrit) has a
		// .gitmodules referencing ../plugins/hooks, which is
		// on http://gerrit.googlesource.com/plugins/hooks,
		this.targetUri = URI.create(uri + "/"); //$NON-NLS-1$
		return this;
	}

	/**
	 * Set groups to sync
	 *
	 * @param groups groups separated by comma, examples: default|all|G1,-G2,-G3
	 * @return this command
	 */
	public RepoCommand setGroups(String groups) {
		this.groupsParam = groups;
		return this;
	}

	/**
	 * Set default branch.
	 * <p>
	 * This is generally the name of the branch the manifest file was in. If
	 * there's no default revision (branch) specified in manifest and no
	 * revision specified in project, this branch will be used.
	 *
	 * @param branch
	 *            a branch name
	 * @return this command
	 */
	public RepoCommand setBranch(String branch) {
		this.branch = branch;
		return this;
	}

	/**
	 * Set target branch.
	 * <p>
	 * This is the target branch of the super project to be updated. If not set,
	 * default is HEAD.
	 * <p>
	 * For non-bare repositories, HEAD will always be used and this will be
	 * ignored.
	 *
	 * @param branch
	 *            branch name
	 * @return this command
	 * @since 4.1
	 */
	public RepoCommand setTargetBranch(String branch) {
		this.targetBranch = Constants.R_HEADS + branch;
		return this;
	}

	/**
	 * Set whether the branch name should be recorded in .gitmodules.
	 * <p>
	 * Submodule entries in .gitmodules can include a "branch" field
	 * to indicate what remote branch each submodule tracks.
	 * <p>
	 * That field is used by "git submodule update --remote" to update
	 * to the tip of the tracked branch when asked and by Gerrit to
	 * update the superproject when a change on that branch is merged.
	 * <p>
	 * Subprojects that request a specific commit or tag will not have
	 * a branch name recorded.
	 * <p>
	 * Not implemented for non-bare repositories.
	 *
	 * @param enable Whether to record the branch name
	 * @return this command
	 * @since 4.2
	 */
	public RepoCommand setRecordRemoteBranch(boolean enable) {
		this.bareWriterConfig.recordRemoteBranch = enable;
		return this;
	}

	/**
	 * Set whether the labels field should be recorded as a label in
	 * .gitattributes.
	 * <p>
	 * Not implemented for non-bare repositories.
	 *
	 * @param enable Whether to record the labels in the .gitattributes
	 * @return this command
	 * @since 4.4
	 */
	public RepoCommand setRecordSubmoduleLabels(boolean enable) {
		this.bareWriterConfig.recordSubmoduleLabels = enable;
		return this;
	}

	/**
	 * Set whether the clone-depth field should be recorded as a shallow
	 * recommendation in .gitmodules.
	 * <p>
	 * Not implemented for non-bare repositories.
	 *
	 * @param enable Whether to record the shallow recommendation.
	 * @return this command
	 * @since 4.4
	 */
	public RepoCommand setRecommendShallow(boolean enable) {
		this.bareWriterConfig.recordShallowSubmodules = enable;
		return this;
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see org.eclipse.jgit.lib.NullProgressMonitor
	 * @param monitor
	 *            a {@link org.eclipse.jgit.lib.ProgressMonitor}
	 * @return this command
	 */
	public RepoCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Set whether to skip projects whose commits don't exist remotely.
	 * <p>
	 * When set to true, we'll just skip the manifest entry and continue
	 * on to the next one.
	 * <p>
	 * When set to false (default), we'll throw an error when remote
	 * failures occur.
	 * <p>
	 * Not implemented for non-bare repositories.
	 *
	 * @param ignore Whether to ignore the remote failures.
	 * @return this command
	 * @since 4.3
	 */
	public RepoCommand setIgnoreRemoteFailures(boolean ignore) {
		this.bareWriterConfig.ignoreRemoteFailures = ignore;
		return this;
	}

	/**
	 * Set the author/committer for the bare repository commit.
	 * <p>
	 * For non-bare repositories, the current user will be used and this will be
	 * ignored.
	 *
	 * @param author
	 *            the author's {@link org.eclipse.jgit.lib.PersonIdent}
	 * @return this command
	 */
	public RepoCommand setAuthor(PersonIdent author) {
		this.author = author;
		return this;
	}

	/**
	 * Set the GetHeadFromUri callback.
	 *
	 * This is only used in bare repositories.
	 *
	 * @param callback
	 *            a {@link org.eclipse.jgit.gitrepo.RepoCommand.RemoteReader}
	 *            object.
	 * @return this command
	 */
	public RepoCommand setRemoteReader(RemoteReader callback) {
		this.callback = callback;
		return this;
	}

	/**
	 * Set the IncludedFileReader callback.
	 *
	 * @param reader
	 *            a
	 *            {@link org.eclipse.jgit.gitrepo.ManifestParser.IncludedFileReader}
	 *            object.
	 * @return this command
	 * @since 4.0
	 */
	public RepoCommand setIncludedFileReader(IncludedFileReader reader) {
		this.includedReader = reader;
		return this;
	}

	/**
	 * Create a file with the given content in the destination repository
	 *
	 * @param path
	 *            where to create the file in the destination repository
	 * @param contents
	 *            content for the create file
	 * @return this command
	 *
	 * @since 6.1
	 */
	public RepoCommand addToDestination(String path, String contents) {
		this.extraContents.add(new ExtraContent(path, contents));
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public RevCommit call() throws GitAPIException {
		checkCallable();
		if (baseUri == null) {
			baseUri = ""; //$NON-NLS-1$
		}
		if (inputStream == null) {
			if (manifestPath == null || manifestPath.length() == 0)
				throw new IllegalArgumentException(
						JGitText.get().pathNotConfigured);
			try {
				inputStream = new FileInputStream(manifestPath);
			} catch (IOException e) {
				throw new IllegalArgumentException(
						JGitText.get().pathNotConfigured, e);
			}
		}

		List<RepoProject> filteredProjects;
		try {
			ManifestParser parser = new ManifestParser(includedReader,
					manifestPath, branch, baseUri, groupsParam, repo);
			parser.read(inputStream);
			filteredProjects = parser.getFilteredProjects();
		} catch (IOException e) {
			throw new ManifestErrorException(e);
		} finally {
			try {
				inputStream.close();
			} catch (IOException e) {
				// Just ignore it, it's not important.
			}
		}

		if (repo.isBare()) {
			List<RepoProject> renamedProjects = renameProjects(filteredProjects);
			BareSuperprojectWriter writer = new BareSuperprojectWriter(repo, targetUri,
					targetBranch,
					author == null ? new PersonIdent(repo) : author,
					callback == null ? new DefaultRemoteReader() : callback,
					bareWriterConfig, extraContents);
			return writer.write(renamedProjects);
		}


		RegularSuperprojectWriter writer = new RegularSuperprojectWriter(repo, monitor);
		return writer.write(filteredProjects);
	}

	/**
	 * Rename the projects if there's a conflict when converted to submodules.
	 *
	 * @param projects
	 *            parsed projects
	 * @return projects that are renamed if necessary
	 */
	private List<RepoProject> renameProjects(List<RepoProject> projects) {
		Map<String, List<RepoProject>> m = new TreeMap<>();
		for (RepoProject proj : projects) {
			List<RepoProject> l = m.get(proj.getName());
			if (l == null) {
				l = new ArrayList<>();
				m.put(proj.getName(), l);
			}
			l.add(proj);
		}

		List<RepoProject> ret = new ArrayList<>();
		for (List<RepoProject> ps : m.values()) {
			boolean nameConflict = ps.size() != 1;
			for (RepoProject proj : ps) {
				String name = proj.getName();
				if (nameConflict) {
					name += SLASH + proj.getPath();
				}
				RepoProject p = new RepoProject(name,
						proj.getPath(), proj.getRevision(), null,
						proj.getGroups(), proj.getRecommendShallow());
				p.setUrl(proj.getUrl());
				p.addCopyFiles(proj.getCopyFiles());
				p.addLinkFiles(proj.getLinkFiles());
				ret.add(p);
			}
		}
		return ret;
	}

	/*
	 * Assume we are document "a/b/index.html", what should we put in a href to get to "a/" ?
	 * Returns the child if either base or child is not a bare path. This provides a missing feature in
	 * java.net.URI (see http://bugs.java.com/view_bug.do?bug_id=6226081).
	 */
	private static final String SLASH = "/"; //$NON-NLS-1$
	static URI relativize(URI current, URI target) {
		if (!Objects.equals(current.getHost(), target.getHost())) {
			return target;
		}

		String cur = current.normalize().getPath();
		String dest = target.normalize().getPath();

		// TODO(hanwen): maybe (absolute, relative) should throw an exception.
		if (cur.startsWith(SLASH) != dest.startsWith(SLASH)) {
			return target;
		}

		while (cur.startsWith(SLASH)) {
			cur = cur.substring(1);
		}
		while (dest.startsWith(SLASH)) {
			dest = dest.substring(1);
		}

		if (cur.indexOf('/') == -1 || dest.indexOf('/') == -1) {
			// Avoid having to special-casing in the next two ifs.
			String prefix = "prefix/"; //$NON-NLS-1$
			cur = prefix + cur;
			dest = prefix + dest;
		}

		if (!cur.endsWith(SLASH)) {
			// The current file doesn't matter.
			int lastSlash = cur.lastIndexOf('/');
			cur = cur.substring(0, lastSlash);
		}
		String destFile = ""; //$NON-NLS-1$
		if (!dest.endsWith(SLASH)) {
			// We always have to provide the destination file.
			int lastSlash = dest.lastIndexOf('/');
			destFile = dest.substring(lastSlash + 1, dest.length());
			dest = dest.substring(0, dest.lastIndexOf('/'));
		}

		String[] cs = cur.split(SLASH);
		String[] ds = dest.split(SLASH);

		int common = 0;
		while (common < cs.length && common < ds.length && cs[common].equals(ds[common])) {
			common++;
		}

		StringJoiner j = new StringJoiner(SLASH);
		for (int i = common; i < cs.length; i++) {
			j.add(".."); //$NON-NLS-1$
		}
		for (int i = common; i < ds.length; i++) {
			j.add(ds[i]);
		}

		j.add(destFile);
		return URI.create(j.toString());
	}

}
