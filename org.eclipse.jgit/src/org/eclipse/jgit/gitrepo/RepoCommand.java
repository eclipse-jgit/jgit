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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.SubmoduleAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.gitrepo.internal.RepoText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;

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
 */
public class RepoCommand extends GitCommand<Void> {

	private String path;
	private String uri;
	private String groups;

	private ProgressMonitor monitor;

	private static class CopyFile {
		final String src;
		final String dest;

		CopyFile(String path, String src, String dest) {
			this.src = path + "/" + src;
			this.dest = dest;
		}

		void copy() throws IOException {
			FileInputStream input = new FileInputStream(src);
			FileOutputStream output = new FileOutputStream(dest);
			FileChannel channel = input.getChannel();
			output.getChannel().transferFrom(channel, 0, channel.size());
			input.close();
			output.close();
		}
	}

	private static class Project {
		final String name;
		final String path;
		final Set<String> groups;
		final List<CopyFile> copyfiles;

		Project(String name, String path, String groups) {
			this.name = name;
			this.path = path;
			this.groups = new HashSet<String>();
			if (groups != null && groups.length() > 0) {
				this.groups.addAll(Arrays.asList(groups.split(",")));
			}
			copyfiles = new ArrayList<CopyFile>();
		}

		void addCopyFile(CopyFile copyfile) {
			copyfiles.add(copyfile);
		}
	}

	private static class XmlManifest extends DefaultHandler {
		private final RepoCommand command;
		private final String filename;
		private final String baseUrl;
		private final Map<String, String> remotes;
		private final List<Project> projects;
		private final Set<String> plusGroups;
		private final Set<String> minusGroups;
		private String defaultRemote;
		private Project currentProject;

		XmlManifest(RepoCommand command, String filename, String baseUrl, String groups) {
			this.command = command;
			this.filename = filename;
			this.baseUrl = baseUrl;
			remotes = new HashMap<String, String>();
			projects = new ArrayList<Project>();
			plusGroups = new HashSet<String>();
			minusGroups = new HashSet<String>();
			if (groups == null || groups.length() == 0 || groups.equals("default")) {
				// default means "all,-notdefault"
				minusGroups.add("notdefault");
			} else {
				for (String group : groups.split(",")) {
					if (group.startsWith("-"))
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
			final FileInputStream in = new FileInputStream(filename);
			try {
				xr.parse(new InputSource(in));
			} catch (SAXException e) {
				throw new IOException(MessageFormat.format(
							RepoText.get().errorParsingFromFile, filename), e);
			} finally {
				in.close();
			}
		}

		@Override
		public void startElement(
				String uri,
				String localName,
				String qName,
				Attributes attributes) throws SAXException {
			if ("project".equals(qName)) {
				currentProject = new Project(
						attributes.getValue("name"),
						attributes.getValue("path"),
						attributes.getValue("groups"));
			} else if ("remote".equals(qName)) {
				remotes.put(attributes.getValue("name"),
						attributes.getValue("fetch"));
			} else if ("default".equals(qName)) {
				defaultRemote = attributes.getValue("remote");
			} else if ("copyfile".equals(qName)) {
				if (currentProject == null)
					throw new SAXException(RepoText.get().invalidManifest);
				currentProject.addCopyFile(new CopyFile(
							currentProject.path,
							attributes.getValue("src"),
							attributes.getValue("dest")));
			}
		}

		@Override
		public void endElement(
				String uri,
				String localName,
				String qName) throws SAXException {
			if ("project".equals(qName)) {
				projects.add(currentProject);
				currentProject = null;
			}
		}

		@Override
		public void endDocument() throws SAXException {
			if (defaultRemote == null) {
				throw new SAXException(MessageFormat.format(
						RepoText.get().errorNoDefault, filename));
			}
			final String remoteUrl;
			try {
				URI uri = new URI(String.format("%s/%s/", baseUrl, remotes.get(defaultRemote)));
				remoteUrl = uri.normalize().toString();
			} catch (URISyntaxException e) {
				throw new SAXException(e);
			}
			for (Project proj : projects) {
				if (inGroups(proj)) {
					String url = remoteUrl + proj.name;
					command.addSubmodule(url, proj.path);
					for (CopyFile copyfile : proj.copyfiles) {
						try {
							copyfile.copy();
						} catch (IOException e) {
							throw new SAXException(
									RepoText.get().copyFileFailed, e);
						}
						AddCommand add = new AddCommand(command.repo)
							.addFilepattern(copyfile.dest);
						try {
							add.call();
						} catch (GitAPIException e) {
							throw new SAXException(e);
						}
					}
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
			if (plusGroups.isEmpty() || plusGroups.contains("all")) {
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

	/**
	 * @param repo
	 */
	public RepoCommand(final Repository repo) {
		super(repo);
	}

	/**
	 * Set path to the manifest XML file
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
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 * @return this command
	 */
	public RepoCommand setProgressMonitor(final ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	public Void call() throws GitAPIException {
		checkCallable();
		if (path == null || path.length() == 0)
			throw new IllegalArgumentException(JGitText.get().pathNotConfigured);
		if (uri == null || uri.length() == 0)
			throw new IllegalArgumentException(JGitText.get().uriNotConfigured);

		XmlManifest manifest = new XmlManifest(this, path, uri, groups);
		try {
			manifest.read();
		} catch (IOException e) {
			throw new ManifestErrorException(e);
		}

		return null;
	}

	private void addSubmodule(String url, String name) throws SAXException {
		SubmoduleAddCommand add = new SubmoduleAddCommand(repo)
			.setPath(name)
			.setURI(url);
		if (monitor != null)
			add.setProgressMonitor(monitor);
		try {
			Repository sub = add.call();
		} catch (GitAPIException e) {
			throw new SAXException(e);
		}
	}
}
