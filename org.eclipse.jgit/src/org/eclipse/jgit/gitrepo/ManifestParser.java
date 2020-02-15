/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gitrepo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.gitrepo.RepoProject.CopyFile;
import org.eclipse.jgit.gitrepo.RepoProject.LinkFile;
import org.eclipse.jgit.gitrepo.RepoProject.ReferenceFile;
import org.eclipse.jgit.gitrepo.internal.RepoText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Repo XML manifest parser.
 *
 * @see <a href="https://code.google.com/p/git-repo/">git-repo project page</a>
 * @since 4.0
 */
public class ManifestParser extends DefaultHandler {
	private final String filename;
	private final URI baseUrl;
	private final String defaultBranch;
	private final Repository rootRepo;
	private final Map<String, Remote> remotes;
	private final Set<String> plusGroups;
	private final Set<String> minusGroups;
	private final List<RepoProject> projects;
	private final List<RepoProject> filteredProjects;
	private final IncludedFileReader includedReader;

	private String defaultRemote;
	private String defaultRevision;
	private int xmlInRead;
	private RepoProject currentProject;

	/**
	 * A callback to read included xml files.
	 */
	public interface IncludedFileReader {
		/**
		 * Read a file from the same base dir of the manifest xml file.
		 *
		 * @param path
		 *            The relative path to the file to read
		 * @return the {@code InputStream} of the file.
		 * @throws GitAPIException
		 * @throws IOException
		 */
		public InputStream readIncludeFile(String path)
				throws GitAPIException, IOException;
	}

	/**
	 * Constructor for ManifestParser
	 *
	 * @param includedReader
	 *            a
	 *            {@link org.eclipse.jgit.gitrepo.ManifestParser.IncludedFileReader}
	 *            object.
	 * @param filename
	 *            a {@link java.lang.String} object.
	 * @param defaultBranch
	 *            a {@link java.lang.String} object.
	 * @param baseUrl
	 *            a {@link java.lang.String} object.
	 * @param groups
	 *            a {@link java.lang.String} object.
	 * @param rootRepo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public ManifestParser(IncludedFileReader includedReader, String filename,
			String defaultBranch, String baseUrl, String groups,
			Repository rootRepo) {
		this.includedReader = includedReader;
		this.filename = filename;
		this.defaultBranch = defaultBranch;
		this.rootRepo = rootRepo;
		this.baseUrl = normalizeEmptyPath(URI.create(baseUrl));

		plusGroups = new HashSet<>();
		minusGroups = new HashSet<>();
		if (groups == null || groups.length() == 0
				|| groups.equals("default")) { //$NON-NLS-1$
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

		remotes = new HashMap<>();
		projects = new ArrayList<>();
		filteredProjects = new ArrayList<>();
	}

	/**
	 * Read the xml file.
	 *
	 * @param inputStream
	 *            a {@link java.io.InputStream} object.
	 * @throws java.io.IOException
	 */
	public void read(InputStream inputStream) throws IOException {
		xmlInRead++;
		final XMLReader xr;
		try {
			xr = XMLReaderFactory.createXMLReader();
		} catch (SAXException e) {
			throw new IOException(JGitText.get().noXMLParserAvailable, e);
		}
		xr.setContentHandler(this);
		try {
			xr.parse(new InputSource(inputStream));
		} catch (SAXException e) {
			throw new IOException(RepoText.get().errorParsingManifestFile, e);
		}
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public void startElement(
			String uri,
			String localName,
			String qName,
			Attributes attributes) throws SAXException {
		if (qName == null) {
			return;
		}
		switch (qName) {
		case "project":
			if (attributes.getValue("name") == null) {
				throw new SAXException(RepoText.get().invalidManifest);
			}
			currentProject = new RepoProject(attributes.getValue("name"),
					attributes.getValue("path"),
					attributes.getValue("revision"),
					attributes.getValue("remote"),
					attributes.getValue("groups"));
			currentProject
					.setRecommendShallow(attributes.getValue("clone-depth"));
			break;
		case "remote":
			String alias = attributes.getValue("alias");
			String fetch = attributes.getValue("fetch");
			String revision = attributes.getValue("revision");
			Remote remote = new Remote(fetch, revision);
			remotes.put(attributes.getValue("name"), remote);
			if (alias != null) {
				remotes.put(alias, remote);
			}
			break;
		case "default":
			defaultRemote = attributes.getValue("remote");
			defaultRevision = attributes.getValue("revision");
			break;
		case "copyfile":
			if (currentProject == null) {
				throw new SAXException(RepoText.get().invalidManifest);
			}
			currentProject.addCopyFile(new CopyFile(rootRepo,
					currentProject.getPath(), attributes.getValue("src"),
					attributes.getValue("dest")));
			break;
		case "linkfile":
			if (currentProject == null) {
				throw new SAXException(RepoText.get().invalidManifest);
			}
			currentProject.addLinkFile(new LinkFile(rootRepo,
					currentProject.getPath(), attributes.getValue("src"),
					attributes.getValue("dest")));
			break;
		case "include":
			String name = attributes.getValue("name");
			if (includedReader != null) {
				try (InputStream is = includedReader.readIncludeFile(name)) {
					if (is == null) {
						throw new SAXException(
								RepoText.get().errorIncludeNotImplemented);
					}
					read(is);
				} catch (Exception e) {
					throw new SAXException(MessageFormat
							.format(RepoText.get().errorIncludeFile, name), e);
				}
			} else if (filename != null) {
				int index = filename.lastIndexOf('/');
				String path = filename.substring(0, index + 1) + name;
				try (InputStream is = new FileInputStream(path)) {
					read(is);
				} catch (IOException e) {
					throw new SAXException(MessageFormat
							.format(RepoText.get().errorIncludeFile, path), e);
				}
			}
			break;
		case "remove-project": {
			String name2 = attributes.getValue("name");
			projects.removeIf((p) -> p.getName().equals(name2));
			break;
		}
		default:
			break;
		}
	}

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
	@Override
	public void endDocument() throws SAXException {
		xmlInRead--;
		if (xmlInRead != 0)
			return;

		// Only do the following after we finished reading everything.
		Map<String, URI> remoteUrls = new HashMap<>();
		if (defaultRevision == null && defaultRemote != null) {
			Remote remote = remotes.get(defaultRemote);
			if (remote != null) {
				defaultRevision = remote.revision;
			}
			if (defaultRevision == null) {
				defaultRevision = defaultBranch;
			}
		}
		for (RepoProject proj : projects) {
			String remote = proj.getRemote();
			String revision = defaultRevision;
			if (remote == null) {
				if (defaultRemote == null) {
					if (filename != null) {
						throw new SAXException(MessageFormat.format(
								RepoText.get().errorNoDefaultFilename,
								filename));
					}
					throw new SAXException(RepoText.get().errorNoDefault);
				}
				remote = defaultRemote;
			} else {
				Remote r = remotes.get(remote);
				if (r != null && r.revision != null) {
					revision = r.revision;
				}
			}
			URI remoteUrl = remoteUrls.get(remote);
			if (remoteUrl == null) {
				String fetch = remotes.get(remote).fetch;
				if (fetch == null) {
					throw new SAXException(MessageFormat
							.format(RepoText.get().errorNoFetch, remote));
				}
				remoteUrl = normalizeEmptyPath(baseUrl.resolve(fetch));
				remoteUrls.put(remote, remoteUrl);
			}
			proj.setUrl(remoteUrl.resolve(proj.getName()).toString())
				.setDefaultRevision(revision);
		}

		filteredProjects.addAll(projects);
		removeNotInGroup();
		removeOverlaps();
	}

	static URI normalizeEmptyPath(URI u) {
		// URI.create("scheme://host").resolve("a/b") => "scheme://hosta/b"
		// That seems like bug https://bugs.openjdk.java.net/browse/JDK-4666701.
		// We workaround this by special casing the empty path case.
		if (u.getHost() != null && !u.getHost().isEmpty() &&
			(u.getPath() == null || u.getPath().isEmpty())) {
			try {
				return new URI(u.getScheme(),
					u.getUserInfo(), u.getHost(), u.getPort(),
						"/", u.getQuery(), u.getFragment()); //$NON-NLS-1$
			} catch (URISyntaxException x) {
				throw new IllegalArgumentException(x.getMessage(), x);
			}
		}
		return u;
	}

	/**
	 * Getter for projects.
	 *
	 * @return projects list reference, never null
	 */
	public List<RepoProject> getProjects() {
		return projects;
	}

	/**
	 * Getter for filterdProjects.
	 *
	 * @return filtered projects list reference, never null
	 */
	@NonNull
	public List<RepoProject> getFilteredProjects() {
		return filteredProjects;
	}

	/** Remove projects that are not in our desired groups. */
	void removeNotInGroup() {
		Iterator<RepoProject> iter = filteredProjects.iterator();
		while (iter.hasNext())
			if (!inGroups(iter.next()))
				iter.remove();
	}

	/** Remove projects that sits in a subdirectory of any other project. */
	void removeOverlaps() {
		Collections.sort(filteredProjects);
		Iterator<RepoProject> iter = filteredProjects.iterator();
		if (!iter.hasNext())
			return;
		RepoProject last = iter.next();
		while (iter.hasNext()) {
			RepoProject p = iter.next();
			if (last.isAncestorOf(p))
				iter.remove();
			else
				last = p;
		}
		removeNestedCopyAndLinkfiles();
	}

	private void removeNestedCopyAndLinkfiles() {
		for (RepoProject proj : filteredProjects) {
			List<CopyFile> copyfiles = new ArrayList<>(proj.getCopyFiles());
			proj.clearCopyFiles();
			for (CopyFile copyfile : copyfiles) {
				if (!isNestedReferencefile(copyfile)) {
					proj.addCopyFile(copyfile);
				}
			}
			List<LinkFile> linkfiles = new ArrayList<>(proj.getLinkFiles());
			proj.clearLinkFiles();
			for (LinkFile linkfile : linkfiles) {
				if (!isNestedReferencefile(linkfile)) {
					proj.addLinkFile(linkfile);
				}
			}
		}
	}

	boolean inGroups(RepoProject proj) {
		for (String group : minusGroups) {
			if (proj.inGroup(group)) {
				// minus groups have highest priority.
				return false;
			}
		}
		if (plusGroups.isEmpty() || plusGroups.contains("all")) { //$NON-NLS-1$
			// empty plus groups means "all"
			return true;
		}
		for (String group : plusGroups) {
			if (proj.inGroup(group))
				return true;
		}
		return false;
	}

	private boolean isNestedReferencefile(ReferenceFile referencefile) {
		if (referencefile.dest.indexOf('/') == -1) {
			// If the referencefile is at root level then it won't be nested.
			return false;
		}
		for (RepoProject proj : filteredProjects) {
			if (proj.getPath().compareTo(referencefile.dest) > 0) {
				// Early return as remaining projects can't be ancestor of this
				// referencefile config (filteredProjects is sorted).
				return false;
			}
			if (proj.isAncestorOf(referencefile.dest)) {
				return true;
			}
		}
		return false;
	}

	private static class Remote {
		final String fetch;
		final String revision;

		Remote(String fetch, String revision) {
			this.fetch = fetch;
			this.revision = revision;
		}
	}
}
