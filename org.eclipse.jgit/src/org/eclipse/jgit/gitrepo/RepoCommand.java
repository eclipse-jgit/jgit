/*
 * Copyright (C) 2014, Google Inc.
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
package org.eclipse.jgit.gitrepo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.SubmoduleAddCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.gitrepo.internal.RepoText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FileUtils;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * A class used to execute a repo command.
 *
 * This will parse a repo XML manifest, convert it into .gitmodules file and the
 * repository config file.
 *
 * If called against a bare repository, it will replace all the existing content
 * of the repository with the contents populated from the manifest.
 *
 * @see <a href="https://code.google.com/p/git-repo/">git-repo project page</a>
 * @since 3.4
 */
public class RepoCommand extends GitCommand<RevCommit> {

	private String path;
	private String uri;
	private String groups;
	private String branch;
	private PersonIdent author;
	private RemoteReader callback;
	private InputStream inputStream;

	private List<Project> bareProjects;
	private Git git;
	private ProgressMonitor monitor;

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
		 *            The ref (branch/tag/etc.) to read
		 * @return the sha1 of the remote repository
		 * @throws GitAPIException
		 */
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
		 */
		public byte[] readFile(String uri, String ref, String path)
				throws GitAPIException, IOException;
	}

	/** A default implementation of {@link RemoteReader} callback. */
	public static class DefaultRemoteReader implements RemoteReader {
		public ObjectId sha1(String uri, String ref) throws GitAPIException {
			Map<String, Ref> map = Git
					.lsRemoteRepository()
					.setRemote(uri)
					.callAsMap();
			Ref r = RefDatabase.findRef(map, ref);
			return r != null ? r.getObjectId() : null;
		}

		public byte[] readFile(String uri, String ref, String path)
				throws GitAPIException, IOException {
			File dir = FileUtils.createTempDir("jgit_", ".git", null); //$NON-NLS-1$ //$NON-NLS-2$
			Repository repo = Git
					.cloneRepository()
					.setBare(true)
					.setDirectory(dir)
					.setURI(uri)
					.call()
					.getRepository();
			try {
				return readFileFromRepo(repo, ref, path);
			} finally {
				FileUtils.delete(dir, FileUtils.RECURSIVE);
			}
		}

		protected byte[] readFileFromRepo(Repository repo,
				String ref, String path) throws GitAPIException, IOException {
			ObjectReader reader = repo.newObjectReader();
			byte[] result;
			try {
				ObjectId oid = repo.resolve(ref + ":" + path); //$NON-NLS-1$
				result = reader.open(oid).getBytes(Integer.MAX_VALUE);
			} finally {
				reader.release();
			}
			return result;
		}
	}

	private static class CopyFile {
		final Repository repo;
		final String path;
		final String src;
		final String dest;

		CopyFile(Repository repo, String path, String src, String dest) {
			this.repo = repo;
			this.path = path;
			this.src = src;
			this.dest = dest;
		}

		void copy() throws IOException {
			File srcFile = new File(repo.getWorkTree(),
					path + "/" + src); //$NON-NLS-1$
			File destFile = new File(repo.getWorkTree(), dest);
			FileInputStream input = new FileInputStream(srcFile);
			try {
				FileOutputStream output = new FileOutputStream(destFile);
				try {
					FileChannel channel = input.getChannel();
					output.getChannel().transferFrom(channel, 0, channel.size());
				} finally {
					output.close();
				}
			} finally {
				input.close();
			}
		}
	}

	private static class Project {
		final String name;
		final String path;
		final String revision;
		final Set<String> groups;
		final List<CopyFile> copyfiles;

		Project(String name, String path, String revision, String groups) {
			this.name = name;
			this.path = path;
			this.revision = revision;
			this.groups = new HashSet<String>();
			if (groups != null && groups.length() > 0)
				this.groups.addAll(Arrays.asList(groups.split(","))); //$NON-NLS-1$
			copyfiles = new ArrayList<CopyFile>();
		}

		void addCopyFile(CopyFile copyfile) {
			copyfiles.add(copyfile);
		}
	}

	private static class XmlManifest extends DefaultHandler {
		private final RepoCommand command;
		private final InputStream inputStream;
		private final String filename;
		private final String baseUrl;
		private final Map<String, String> remotes;
		private final List<Project> projects;
		private final Set<String> plusGroups;
		private final Set<String> minusGroups;
		private String defaultRemote;
		private String defaultRevision;
		private Project currentProject;

		XmlManifest(RepoCommand command, InputStream inputStream,
				String filename, String baseUrl, String groups) {
			this.command = command;
			this.inputStream = inputStream;
			this.filename = filename;

			// Strip trailing /s to match repo behavior.
			int lastIndex = baseUrl.length() - 1;
			while (lastIndex >= 0 && baseUrl.charAt(lastIndex) == '/')
				lastIndex--;
			this.baseUrl = baseUrl.substring(0, lastIndex + 1);

			remotes = new HashMap<String, String>();
			projects = new ArrayList<Project>();
			plusGroups = new HashSet<String>();
			minusGroups = new HashSet<String>();
			if (groups == null || groups.length() == 0 || groups.equals("default")) { //$NON-NLS-1$
				// default means "all,-notdefault"
				minusGroups.add("notdefault"); //$NON-NLS-1$
			} else {
				for (String group : groups.split(",")) { //$NON-NLS-1$
					if (group.startsWith("-")) //$NON-NLS-1$
						minusGroups.add(group.substring(1));
					else
						plusGroups.add(group);
				}
			}
		}

		void read() throws IOException {
			final XMLReader xr;
			try {
				xr = XMLReaderFactory.createXMLReader();
			} catch (SAXException e) {
				throw new IOException(JGitText.get().noXMLParserAvailable);
			}
			xr.setContentHandler(this);
			try {
				xr.parse(new InputSource(inputStream));
			} catch (SAXException e) {
				IOException error = new IOException(
							RepoText.get().errorParsingManifestFile);
				error.initCause(e);
				throw error;
			}
		}

		@Override
		public void startElement(
				String uri,
				String localName,
				String qName,
				Attributes attributes) throws SAXException {
			if ("project".equals(qName)) { //$NON-NLS-1$
				currentProject = new Project( //$NON-NLS-1$
						attributes.getValue("name"), //$NON-NLS-1$
						attributes.getValue("path"), //$NON-NLS-1$
						attributes.getValue("revision"), //$NON-NLS-1$
						attributes.getValue("groups")); //$NON-NLS-1$
			} else if ("remote".equals(qName)) { //$NON-NLS-1$
				remotes.put(attributes.getValue("name"), //$NON-NLS-1$
						attributes.getValue("fetch")); //$NON-NLS-1$
			} else if ("default".equals(qName)) { //$NON-NLS-1$
				defaultRemote = attributes.getValue("remote"); //$NON-NLS-1$
				defaultRevision = attributes.getValue("revision"); //$NON-NLS-1$
				if (defaultRevision == null)
					defaultRevision = command.branch;
			} else if ("copyfile".equals(qName)) { //$NON-NLS-1$
				if (currentProject == null)
					throw new SAXException(RepoText.get().invalidManifest);
				currentProject.addCopyFile(new CopyFile(
							command.repo,
							currentProject.path,
							attributes.getValue("src"), //$NON-NLS-1$
							attributes.getValue("dest"))); //$NON-NLS-1$
			}
		}

		@Override
		public void endElement(
				String uri,
				String localName,
				String qName) throws SAXException {
			if ("project".equals(qName)) { //$NON-NLS-1$
				projects.add(currentProject);
				currentProject = null;
			}
		}

		@Override
		public void endDocument() throws SAXException {
			if (defaultRemote == null) {
				if (filename != null)
					throw new SAXException(MessageFormat.format(
							RepoText.get().errorNoDefaultFilename, filename));
				else
					throw new SAXException(RepoText.get().errorNoDefault);
			}
			final String remoteUrl;
			try {
				URI uri = new URI(baseUrl);
				remoteUrl = uri.resolve(remotes.get(defaultRemote)).toString();
			} catch (URISyntaxException e) {
				throw new SAXException(e);
			}
			for (Project proj : projects) {
				if (inGroups(proj)) {
					command.addSubmodule(remoteUrl + proj.name,
							proj.path,
							proj.revision == null
									? defaultRevision : proj.revision,
							proj.copyfiles);
				}
			}
		}

		boolean inGroups(Project proj) {
			for (String group : minusGroups) {
				if (proj.groups.contains(group)) {
					// minus groups have highest priority.
					return false;
				}
			}
			if (plusGroups.isEmpty() || plusGroups.contains("all")) { //$NON-NLS-1$
				// empty plus groups means "all"
				return true;
			}
			for (String group : plusGroups) {
				if (proj.groups.contains(group))
					return true;
			}
			return false;
		}
	}

	private static class ManifestErrorException extends GitAPIException {
		ManifestErrorException(Throwable cause) {
			super(RepoText.get().invalidManifest, cause);
		}
	}

	private static class RemoteUnavailableException extends GitAPIException {
		RemoteUnavailableException(String uri) {
			super(MessageFormat.format(RepoText.get().errorRemoteUnavailable, uri));
		}
	}

	/**
	 * @param repo
	 */
	public RepoCommand(final Repository repo) {
		super(repo);
	}

	/**
	 * Set path to the manifest XML file.
	 *
	 * Calling {@link #setInputStream} will ignore the path set here.
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public RepoCommand setPath(final String path) {
		this.path = path;
		return this;
	}

	/**
	 * Set the input stream to the manifest XML.
	 *
	 * Setting inputStream will ignore the path set. It will be closed in
	 * {@link #call}.
	 *
	 * @param inputStream
	 * @return this command
	 * @since 3.5
	 */
	public RepoCommand setInputStream(final InputStream inputStream) {
		this.inputStream = inputStream;
		return this;
	}

	/**
	 * Set base URI of the pathes inside the XML
	 *
	 * @param uri
	 * @return this command
	 */
	public RepoCommand setURI(final String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * Set groups to sync
	 *
	 * @param groups groups separated by comma, examples: default|all|G1,-G2,-G3
	 * @return this command
	 */
	public RepoCommand setGroups(final String groups) {
		this.groups = groups;
		return this;
	}

	/**
	 * Set default branch.
	 *
	 * This is generally the name of the branch the manifest file was in. If
	 * there's no default revision (branch) specified in manifest and no
	 * revision specified in project, this branch will be used.
	 *
	 * @param branch
	 * @return this command
	 */
	public RepoCommand setBranch(final String branch) {
		this.branch = branch;
		return this;
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see org.eclipse.jgit.lib.NullProgressMonitor
	 * @param monitor
	 * @return this command
	 */
	public RepoCommand setProgressMonitor(final ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Set the author/committer for the bare repository commit.
	 *
	 * For non-bare repositories, the current user will be used and this will be
	 * ignored.
	 *
	 * @param author
	 * @return this command
	 */
	public RepoCommand setAuthor(final PersonIdent author) {
		this.author = author;
		return this;
	}

	/**
	 * Set the GetHeadFromUri callback.
	 *
	 * This is only used in bare repositories.
	 *
	 * @param callback
	 * @return this command
	 */
	public RepoCommand setRemoteReader(final RemoteReader callback) {
		this.callback = callback;
		return this;
	}

	@Override
	public RevCommit call() throws GitAPIException {
		try {
			checkCallable();
			if (uri == null || uri.length() == 0)
				throw new IllegalArgumentException(
						JGitText.get().uriNotConfigured);
			if (inputStream == null) {
				if (path == null || path.length() == 0)
					throw new IllegalArgumentException(
							JGitText.get().pathNotConfigured);
				try {
					inputStream = new FileInputStream(path);
				} catch (IOException e) {
					throw new IllegalArgumentException(
							JGitText.get().pathNotConfigured);
				}
			}

			if (repo.isBare()) {
				bareProjects = new ArrayList<Project>();
				if (author == null)
					author = new PersonIdent(repo);
				if (callback == null)
					callback = new DefaultRemoteReader();
			} else
				git = new Git(repo);

			XmlManifest manifest = new XmlManifest(
					this, inputStream, path, uri, groups);
			try {
				manifest.read();
			} catch (IOException e) {
				throw new ManifestErrorException(e);
			}
		} finally {
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				// Just ignore it, it's not important.
			}
		}

		if (repo.isBare()) {
			DirCache index = DirCache.newInCore();
			DirCacheBuilder builder = index.builder();
			ObjectInserter inserter = repo.newObjectInserter();
			RevWalk rw = new RevWalk(repo);
			try {
				Config cfg = new Config();
				for (Project proj : bareProjects) {
					String name = proj.path;
					String uri = proj.name;
					cfg.setString("submodule", name, "path", name); //$NON-NLS-1$ //$NON-NLS-2$
					cfg.setString("submodule", name, "url", uri); //$NON-NLS-1$ //$NON-NLS-2$
					// create gitlink
					DirCacheEntry dcEntry = new DirCacheEntry(name);
					ObjectId objectId;
					if (ObjectId.isId(proj.revision))
						objectId = ObjectId.fromString(proj.revision);
					else {
						objectId = callback.sha1(uri, proj.revision);
					}
					if (objectId == null)
						throw new RemoteUnavailableException(uri);
					dcEntry.setObjectId(objectId);
					dcEntry.setFileMode(FileMode.GITLINK);
					builder.add(dcEntry);

					for (CopyFile copyfile : proj.copyfiles) {
						byte[] src = callback.readFile(
								uri, proj.revision, copyfile.src);
						objectId = inserter.insert(Constants.OBJ_BLOB, src);
						dcEntry = new DirCacheEntry(copyfile.dest);
						dcEntry.setObjectId(objectId);
						dcEntry.setFileMode(FileMode.REGULAR_FILE);
						builder.add(dcEntry);
					}
				}
				String content = cfg.toText();

				// create a new DirCacheEntry for .gitmodules file.
				final DirCacheEntry dcEntry = new DirCacheEntry(Constants.DOT_GIT_MODULES);
				ObjectId objectId = inserter.insert(Constants.OBJ_BLOB,
						content.getBytes(Constants.CHARACTER_ENCODING));
				dcEntry.setObjectId(objectId);
				dcEntry.setFileMode(FileMode.REGULAR_FILE);
				builder.add(dcEntry);

				builder.finish();
				ObjectId treeId = index.writeTree(inserter);

				// Create a Commit object, populate it and write it
				ObjectId headId = repo.resolve(Constants.HEAD + "^{commit}"); //$NON-NLS-1$
				CommitBuilder commit = new CommitBuilder();
				commit.setTreeId(treeId);
				if (headId != null)
					commit.setParentIds(headId);
				commit.setAuthor(author);
				commit.setCommitter(author);
				commit.setMessage(RepoText.get().repoCommitMessage);

				ObjectId commitId = inserter.insert(commit);
				inserter.flush();

				RefUpdate ru = repo.updateRef(Constants.HEAD);
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
						throw new ConcurrentRefUpdateException(
								JGitText.get().couldNotLockHEAD, ru.getRef(),
								rc);
					default:
						throw new JGitInternalException(MessageFormat.format(
								JGitText.get().updatingRefFailed,
								Constants.HEAD, commitId.name(), rc));
				}

				return rw.parseCommit(commitId);
			} catch (IOException e) {
				throw new ManifestErrorException(e);
			} finally {
				rw.release();
			}
		} else {
			return git
				.commit()
				.setMessage(RepoText.get().repoCommitMessage)
				.call();
		}
	}

	private void addSubmodule(String url, String name, String revision,
			List<CopyFile> copyfiles) throws SAXException {
		if (repo.isBare()) {
			Project proj = new Project(url, name, revision, null);
			proj.copyfiles.addAll(copyfiles);
			bareProjects.add(proj);
		} else {
			SubmoduleAddCommand add = git
				.submoduleAdd()
				.setPath(name)
				.setURI(url);
			if (monitor != null)
				add.setProgressMonitor(monitor);

			try {
				Repository subRepo = add.call();
				if (revision != null) {
					Git sub = new Git(subRepo);
					sub.checkout().setName(findRef(revision, subRepo)).call();
					git.add().addFilepattern(name).call();
				}
				for (CopyFile copyfile : copyfiles) {
					copyfile.copy();
					git.add().addFilepattern(copyfile.dest).call();
				}
			} catch (GitAPIException e) {
				throw new SAXException(e);
			} catch (IOException e) {
				throw new SAXException(e);
			}
		}
	}

	private static String findRef(String ref, Repository repo)
			throws IOException {
		if (!ObjectId.isId(ref)) {
			Ref r = repo.getRef(
					Constants.DEFAULT_REMOTE_NAME + "/" + ref);
			if (r != null)
				return r.getName();
		}
		return ref;
	}
}
