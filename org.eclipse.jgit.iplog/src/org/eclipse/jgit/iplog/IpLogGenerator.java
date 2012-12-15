/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.iplog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.iplog.Committer.ActiveRange;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.RawParseUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Creates an Eclipse IP log in XML format.
 *
 * @see <a href="http://www.eclipse.org/projects/xml/iplog.xsd">IP log XSD</a>
 */
public class IpLogGenerator {
	private static final String IPLOG_NS = "http://www.eclipse.org/projects/xml/iplog";

	private static final String IPLOG_PFX = "iplog:";

	private static final String INDENT = "{http://xml.apache.org/xslt}indent-amount";

	/** Projects indexed by their ID string, e.g. {@code technology.jgit}. */
	private final Map<String, Project> projects = new TreeMap<String, Project>();

	/** Projects indexed by their ID string, e.g. {@code technology.jgit}. */
	private final Map<String, Project> consumedProjects = new TreeMap<String, Project>();

	/** Known committers, indexed by their foundation ID. */
	private final Map<String, Committer> committersById = new HashMap<String, Committer>();

	/** Known committers, indexed by their email address. */
	private final Map<String, Committer> committersByEmail = new HashMap<String, Committer>();

	/** Discovered contributors. */
	private final Map<String, Contributor> contributorsByName = new HashMap<String, Contributor>();

	/** All known CQs matching the projects we care about. */
	private final Set<CQ> cqs = new HashSet<CQ>();

	/** Root commits which were scanned to gather project data. */
	private final Set<RevCommit> commits = new HashSet<RevCommit>();

	/** The meta file we loaded to bootstrap our definitions. */
	private IpLogMeta meta;

	/** URL to obtain review information about a specific contribution. */
	private String reviewUrl;

	private String characterEncoding = "UTF-8";

	private Repository db;

	private RevWalk rw;

	private NameConflictTreeWalk tw;

	private ObjectReader curs;

	private final MutableObjectId idbuf = new MutableObjectId();

	private Document doc;

	/** Create an empty generator. */
	public IpLogGenerator() {
		// Do nothing.
	}

	/**
	 * Set the character encoding used to write the output file.
	 *
	 * @param encodingName
	 *            the character set encoding name.
	 */
	public void setCharacterEncoding(String encodingName) {
		characterEncoding = encodingName;
	}

	/**
	 * Scan a Git repository's history to compute the changes within it.
	 *
	 * @param repo
	 *            the repository to scan.
	 * @param startCommit
	 *            commit the IP log is needed for.
	 * @param version
	 *            symbolic label for the version.
	 * @throws IOException
	 *             the repository cannot be read.
	 * @throws ConfigInvalidException
	 *             the {@code .eclipse_iplog} file present at the top level of
	 *             {@code startId} is not a valid configuration file.
	 */
	public void scan(Repository repo, RevCommit startCommit, String version)
			throws IOException, ConfigInvalidException {
		try {
			db = repo;
			curs = db.newObjectReader();
			rw = new RevWalk(curs);
			tw = new NameConflictTreeWalk(curs);

			RevCommit c = rw.parseCommit(startCommit);

			loadEclipseIpLog(version, c);
			loadCommitters(repo);
			scanProjectCommits(meta.getProjects().get(0), c);
			commits.add(c);
		} finally {
			curs.release();
			db = null;
			rw = null;
			tw = null;
		}
	}

	private void loadEclipseIpLog(String version, RevCommit commit)
			throws IOException, ConfigInvalidException {
		TreeWalk log = TreeWalk.forPath(db, IpLogMeta.IPLOG_CONFIG_FILE, commit
				.getTree());
		if (log == null)
			return;

		meta = new IpLogMeta();
		try {
			meta.loadFrom(new BlobBasedConfig(null, db, log.getObjectId(0)));
		} catch (ConfigInvalidException e) {
			throw new ConfigInvalidException(MessageFormat.format(IpLogText.get().configurationFileInCommitIsInvalid
					, log.getPathString(), commit.name()), e);
		}

		if (meta.getProjects().isEmpty()) {
			throw new ConfigInvalidException(MessageFormat.format(IpLogText.get().configurationFileInCommitHasNoProjectsDeclared
					, log.getPathString(), commit.name()));
		}

		for (Project p : meta.getProjects()) {
			p.setVersion(version);
			projects.put(p.getName(), p);
		}
		for (Project p : meta.getConsumedProjects()) {
			consumedProjects.put(p.getName(), p);
		}
		cqs.addAll(meta.getCQs());
		reviewUrl = meta.getReviewUrl();
	}

	private void loadCommitters(Repository repo) throws IOException {
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		File list = new File(repo.getDirectory(), "gerrit_committers");
		BufferedReader br = new BufferedReader(new FileReader(list));
		try {
			String line;

			while ((line = br.readLine()) != null) {
				String[] field = line.trim().split(" *\\| *");
				String user = field[1];
				String name = field[2];
				String email = field[3];
				Date begin = parseDate(dt, field[4]);
				Date end = parseDate(dt, field[5]);

				if (user.startsWith("username:"))
					user = user.substring("username:".length());

				Committer who = committersById.get(user);
				if (who == null) {
					who = new Committer(user);
					int sp = name.indexOf(' ');
					if (0 < sp) {
						who.setFirstName(name.substring(0, sp).trim());
						who.setLastName(name.substring(sp + 1).trim());
					} else {
						who.setFirstName(name);
						who.setLastName(null);
					}
					committersById.put(who.getID(), who);
				}

				who.addEmailAddress(email);
				who.addActiveRange(new ActiveRange(begin, end));
				committersByEmail.put(email, who);
			}
		} finally {
			br.close();
		}
	}

	private static Date parseDate(SimpleDateFormat dt, String value)
			throws IOException {
		if ("NULL".equals(value) || "".equals(value) || value == null)
			return null;
		int dot = value.indexOf('.');
		if (0 < dot)
			value = value.substring(0, dot);
		try {
			return dt.parse(value);
		} catch (ParseException e) {
			IOException err = new IOException(MessageFormat.format(IpLogText.get().invalidDate, value));
			err.initCause(e);
			throw err;
		}
	}

	private void scanProjectCommits(Project proj, RevCommit start)
			throws IOException {
		rw.reset();
		rw.markStart(start);

		RevCommit commit;
		while ((commit = rw.next()) != null) {
			if (proj.isSkippedCommit(commit)) {
				continue;
			}

			final PersonIdent author = commit.getAuthorIdent();
			final Date when = author.getWhen();

			Committer who = committersByEmail.get(author.getEmailAddress());
			if (who != null && who.inRange(when)) {
				// Commit was written by the committer while they were
				// an active committer on the project.
				//
				who.setHasCommits(true);
				continue;
			}

			// Commit from a non-committer contributor.
			//
			final int cnt = commit.getParentCount();
			if (2 <= cnt) {
				// Avoid a pointless merge attributed to a non-committer.
				// Skip this commit if every file matches at least one
				// of the parent commits exactly, if so then the blame
				// for code in that file can be fully passed onto that
				// parent and this non-committer isn't responsible.
				//
				tw.setFilter(TreeFilter.ANY_DIFF);
				tw.setRecursive(true);

				RevTree[] trees = new RevTree[1 + cnt];
				trees[0] = commit.getTree();
				for (int i = 0; i < cnt; i++)
					trees[i + 1] = commit.getParent(i).getTree();
				tw.reset(trees);

				boolean matchAll = true;
				while (tw.next()) {
					boolean matchOne = false;
					for (int i = 1; i <= cnt; i++) {
						if (tw.getRawMode(0) == tw.getRawMode(i)
								&& tw.idEqual(0, i)) {
							matchOne = true;
							break;
						}
					}
					if (!matchOne) {
						matchAll = false;
						break;
					}
				}
				if (matchAll)
					continue;
			}

			Contributor contributor = contributorsByName.get(author.getName());
			if (contributor == null) {
				String id = author.getEmailAddress();
				String name = author.getName();
				contributor = new Contributor(id, name);
				contributorsByName.put(name, contributor);
			}

			String id = commit.name();
			String subj = commit.getShortMessage();
			SingleContribution item = new SingleContribution(id, when, subj);

			if (2 <= cnt) {
				item.setSize("(merge)");
				contributor.add(item);
				continue;
			}

			int addedLines = 0;
			if (1 == cnt) {
				final RevCommit parent = commit.getParent(0);
				tw.setFilter(TreeFilter.ANY_DIFF);
				tw.setRecursive(true);
				tw.reset(new RevTree[] { parent.getTree(), commit.getTree() });
				while (tw.next()) {
					if (tw.getFileMode(1).getObjectType() != Constants.OBJ_BLOB)
						continue;

					byte[] oldImage;
					if (tw.getFileMode(0).getObjectType() == Constants.OBJ_BLOB)
						oldImage = openBlob(0);
					else
						oldImage = new byte[0];

					EditList edits = MyersDiff.INSTANCE.diff(
							RawTextComparator.DEFAULT, new RawText(oldImage),
							new RawText(openBlob(1)));
					for (Edit e : edits)
						addedLines += e.getEndB() - e.getBeginB();
				}

			} else { // no parents, everything is an addition
				tw.setFilter(TreeFilter.ALL);
				tw.setRecursive(true);
				tw.reset(commit.getTree());
				while (tw.next()) {
					if (tw.getFileMode(0).getObjectType() == Constants.OBJ_BLOB) {
						byte[] buf = openBlob(0);
						for (int ptr = 0; ptr < buf.length;) {
							ptr = RawParseUtils.nextLF(buf, ptr);
							addedLines++;
						}
					}
				}
			}

			if (addedLines < 0)
				throw new IOException(MessageFormat.format(IpLogText.get().incorrectlyScanned, commit.name()));
			if (1 == addedLines)
				item.setSize("+1 line");
			else
				item.setSize("+" + addedLines + " lines");
			contributor.add(item);
		}
	}

	private byte[] openBlob(int side) throws IOException {
		tw.getObjectId(idbuf, side);
		return curs.open(idbuf, Constants.OBJ_BLOB).getCachedBytes();
	}

	/**
	 * Dump the scanned information into an XML file.
	 *
	 * @param out
	 *            the file stream to write to. The caller is responsible for
	 *            closing the stream upon completion.
	 * @throws IOException
	 *             the stream cannot be written.
	 */
	public void writeTo(OutputStream out) throws IOException {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer s = factory.newTransformer();
			s.setOutputProperty(OutputKeys.ENCODING, characterEncoding);
			s.setOutputProperty(OutputKeys.METHOD, "xml");
			s.setOutputProperty(OutputKeys.INDENT, "yes");
			s.setOutputProperty(INDENT, "2");
			s.transform(new DOMSource(toXML()), new StreamResult(out));
		} catch (ParserConfigurationException e) {
			IOException err = new IOException(IpLogText.get().cannotSerializeXML);
			err.initCause(e);
			throw err;

		} catch (TransformerConfigurationException e) {
			IOException err = new IOException(IpLogText.get().cannotSerializeXML);
			err.initCause(e);
			throw err;

		} catch (TransformerException e) {
			IOException err = new IOException(IpLogText.get().cannotSerializeXML);
			err.initCause(e);
			throw err;
		}
	}

	private Document toXML() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		doc = factory.newDocumentBuilder().newDocument();

		Element root = createElement("iplog");
		doc.appendChild(root);

		if (projects.size() == 1) {
			Project soleProject = projects.values().iterator().next();
			root.setAttribute("name", soleProject.getID());
		}

		Set<String> licenses = new TreeSet<String>();
		for (Project project : sort(projects, Project.COMPARATOR)) {
			root.appendChild(createProject(project));
			licenses.addAll(project.getLicenses());
		}

		if (!consumedProjects.isEmpty())
			appendBlankLine(root);
		for (Project project : sort(consumedProjects, Project.COMPARATOR)) {
			root.appendChild(createConsumes(project));
			licenses.addAll(project.getLicenses());
		}

		for (RevCommit c : sort(commits))
			root.appendChild(createCommitMeta(c));

		if (licenses.size() > 1)
			appendBlankLine(root);
		for (String name : sort(licenses))
			root.appendChild(createLicense(name));

		if (!cqs.isEmpty())
			appendBlankLine(root);
		for (CQ cq : sort(cqs, CQ.COMPARATOR))
			root.appendChild(createCQ(cq));

		if (!committersByEmail.isEmpty())
			appendBlankLine(root);
		for (Committer committer : sort(committersById, Committer.COMPARATOR))
			root.appendChild(createCommitter(committer));

		for (Contributor c : sort(contributorsByName, Contributor.COMPARATOR)) {
			appendBlankLine(root);
			root.appendChild(createContributor(c));
		}

		return doc;
	}

	private void appendBlankLine(Element root) {
		root.appendChild(doc.createTextNode("\n\n  "));
	}

	private Element createProject(Project p) {
		Element project = createElement("project");
		populateProjectType(p, project);
		return project;
	}

	private Element createConsumes(Project p) {
		Element project = createElement("consumes");
		populateProjectType(p, project);
		return project;
	}

	private static void populateProjectType(Project p, Element project) {
		required(project, "id", p.getID());
		required(project, "name", p.getName());
		optional(project, "comments", p.getComments());
		optional(project, "version", p.getVersion());
	}

	private Element createCommitMeta(RevCommit c) {
		Element meta = createElement("meta");
		required(meta, "key", "git-commit");
		required(meta, "value", c.name());
		return meta;
	}

	private Element createLicense(String name) {
		Element license = createElement("license");
		required(license, "id", name);
		optional(license, "description", null);
		optional(license, "comments", null);
		return license;
	}

	private Element createCQ(CQ cq) {
		Element r = createElement("cq");
		required(r, "id", Long.toString(cq.getID()));
		required(r, "description", cq.getDescription());
		optional(r, "license", cq.getLicense());
		optional(r, "use", cq.getUse());
		optional(r, "state", mapCQState(cq.getState()));
		optional(r, "comments", cq.getComments());
		return r;
	}

	private static String mapCQState(String state) {
		// "approved" CQs shall be listed as "active" in the iplog
		if (state.equals("approved"))
			return "active";
		return state;
	}

	private Element createCommitter(Committer who) {
		Element r = createElement("committer");
		required(r, "id", who.getID());
		required(r, "firstName", who.getFirstName());
		required(r, "lastName", who.getLastName());
		optional(r, "affiliation", who.getAffiliation());
		required(r, "active", Boolean.toString(who.isActive()));
		required(r, "hasCommits", Boolean.toString(who.hasCommits()));
		optional(r, "comments", who.getComments());
		return r;
	}

	private Element createContributor(Contributor c) {
		Element r = createElement("contributor");
		required(r, "id", c.getID());
		required(r, "name", c.getName());

		for (SingleContribution s : sort(c.getContributions(),
				SingleContribution.COMPARATOR))
			r.appendChild(createContribution(s));

		return r;
	}

	private Element createContribution(SingleContribution s) {
		Element r = createElement("contribution");
		required(r, "id", s.getID());
		required(r, "description", s.getSummary());
		required(r, "size", s.getSize());
		if (reviewUrl != null)
			optional(r, "url", reviewUrl + s.getID());
		return r;
	}

	private Element createElement(String name) {
		return doc.createElementNS(IPLOG_NS, IPLOG_PFX + name);
	}

	private static void required(Element r, String name, String value) {
		if (value == null)
			value = "";
		r.setAttribute(name, value);
	}

	private static void optional(Element r, String name, String value) {
		if (value != null && value.length() > 0)
			r.setAttribute(name, value);
	}

	private static <T, Q extends Comparator<T>> Iterable<T> sort(
			Collection<T> objs, Q cmp) {
		ArrayList<T> sorted = new ArrayList<T>(objs);
		Collections.sort(sorted, cmp);
		return sorted;
	}

	private static <T, Q extends Comparator<T>> Iterable<T> sort(
			Map<?, T> objs, Q cmp) {
		return sort(objs.values(), cmp);
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable> Iterable<T> sort(Collection<T> objs) {
		ArrayList<T> sorted = new ArrayList<T>(objs);
		Collections.sort(sorted);
		return sorted;
	}
}
