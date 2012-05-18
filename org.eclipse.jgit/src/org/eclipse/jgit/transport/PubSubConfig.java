/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

/**
 * Contains a list of subscribed remote repositories, stored in a separate
 * git-style configuration file. This file is shared by all repositories a user
 * subscribes to.
 *
 * This configuration holds all publishers using sections:
 *
 * <pre>
 *[publisher "https://android.googlesource.com/"]
 * subscriber = origin /home/.../android/.git
 * subscriber = review /home/.../android/.git
 *</pre>
 */
public class PubSubConfig {
	/**
	 * @param uri
	 * @return the scheme, domain and port only e.g "git://github.com:8080/"
	 */
	public static String getUriRoot(URIish uri) {
		return uri.setPath("/").toString();
	}

	/** A single publisher config with one or more subscribers. */
	public static class Publisher {
		private final String key;

		private final URIish uriRoot;

		private Map<String, Subscriber> subscribers;

		Publisher(Config c, String k) throws URISyntaxException, IOException,
				ConfigInvalidException {
			uriRoot = new URIish(k);
			key = k;
			String sList[] = c.getStringList(SECTION_PUBLISHER, k,
					KEY_SUBSCRIBER);

			subscribers = new HashMap<String, Subscriber>();
			for (String subscriber : sList)
				addSubscriber(new Subscriber(this, subscriber));
		}

		/**
		 * Create a new publisher for this URI root.
		 *
		 * @param uriRoot
		 *            e.g "https://github.com:8080/"
		 * @throws URISyntaxException
		 */
		public Publisher(String uriRoot) throws URISyntaxException {
			this.uriRoot = new URIish(uriRoot);
			key = uriRoot;
			subscribers = new HashMap<String, Subscriber>();
		}

		/** @return publisher section key */
		public String getKey() {
			return key;
		}

		/** @return pubsub URI root */
		public URIish getUri() {
			return uriRoot;
		}

		/** @return all subscribers for this publisher */
		public Collection<Subscriber> getSubscribers() {
			return Collections.unmodifiableCollection(subscribers.values());
		}

		/**
		 * @param remote
		 *            the remote section name
		 * @param directory
		 *            the metadata directory of the local repository
		 * @return subscriber with k == name, else null
		 */
		public Subscriber getSubscriber(String remote, String directory) {
			return subscribers.get(remote + " " + directory);
		}

		/**
		 * @param s
		 *            subscriber to add
		 */
		public void addSubscriber(Subscriber s) {
			subscribers.put(s.getKey(), s);
		}

		/**
		 * @param s
		 *            subscriber to remove
		 * @return true if the subscriber was found and removed
		 */
		public boolean removeSubscriber(Subscriber s) {
			return subscribers.remove(s.getKey()) != null;
		}

		/**
		 * @param remote
		 *            section name
		 * @param directory
		 *            of repository
		 * @return true if the subscriber was found and removed
		 */
		public boolean removeSubscriber(String remote, String directory) {
			return subscribers.remove(remote + " " + directory) != null;
		}

		/**
		 * Write the configuration represented by this PubSubConfig instance to
		 * the config file.
		 *
		 * @param c
		 *            the config to update
		 */
		public void update(final Config c) {
			List<String> values = new ArrayList<String>();
			for (Subscriber s : subscribers.values())
				values.add(s.getKey());
			c.setStringList(SECTION_PUBLISHER, getKey(), KEY_SUBSCRIBER,
					values);
		}
	}

	/**
	 * A single subscriber (remote of a repository), connected to a Publisher.
	 * Subscriber RefSpecs are pulled from the individual repository
	 * configuration using the "fetch" key.
	 */
	public static class Subscriber {
		private Publisher publisher;

		private String directory;

		private String remote;

		private String name;

		private List<RefSpec> subscribeSpecs;

		/**
		 * @param pub
		 *            the publisher instance that this subscriber connects
		 *            through to receive updates according to base URI
		 *
		 * @param remote
		 *            the remote config section name
		 * @param dir
		 *            the metadata directory of the local repository
		 * @throws IOException
		 * @throws ConfigInvalidException
		 * @throws URISyntaxException
		 */
		public Subscriber(Publisher pub, String remote, String dir)
				throws IOException, ConfigInvalidException, URISyntaxException {
			publisher = pub;
			directory = dir;
			this.remote = remote;
			load();
		}

		Subscriber(Publisher pub, String configLine)
				throws IOException, ConfigInvalidException, URISyntaxException {
			String[] parts = configLine.split(" ", 2);
			remote = parts[0];
			directory = parts[1];
			publisher = pub;
			load();
		}

		private void load()
				throws IOException, ConfigInvalidException, URISyntaxException {
			FS fs = FS.detect();
			File git = RepositoryCache.FileKey.resolve(new File(directory), fs);
			if (git == null)
				throw new FileNotFoundException(directory);
			FileBasedConfig fc = new FileBasedConfig(new File(git, "config"),
					fs);
			fc.load();
			RemoteConfig rc = new RemoteConfig(fc, remote);
			subscribeSpecs = rc.getFetchRefSpecs();
			// Remove the leading slash
			if (rc.getURIs().size() == 0)
				throw new ConfigInvalidException(MessageFormat.format(
						JGitText.get().invalidRemote, remote));
			name = rc.getURIs().get(0).getPath().substring(1);
		}

		/** @return the key that identifies this subscriber */
		public String getKey() {
			return remote + " " + directory;
		}

		/** @return the remote section name that is subscribed to */
		public String getRemote() {
			return remote;
		}

		/** @return the unique name for this repository on the remote host */
		public String getName() {
			return name;
		}

		/** @return the base directory of the repository */
		public String getDirectory() {
			return directory;
		}

		/** @return the publisher this subscriber connects to */
		public Publisher getPublisher() {
			return publisher;
		}

		/** @return a list of all RefSpecs to subscribe to */
		public List<RefSpec> getSubscribeSpecs() {
			return Collections.unmodifiableList(subscribeSpecs);
		}
	}

	static final String SECTION_PUBLISHER = "publisher";

	static final String KEY_SUBSCRIBER = "subscriber";

	private Map<String, Publisher> publishers;

	/**
	 * Load configuration from config file.
	 *
	 * @param c
	 * @throws URISyntaxException
	 * @throws ConfigInvalidException
	 * @throws IOException
	 */
	public PubSubConfig(Config c) throws URISyntaxException, IOException,
			ConfigInvalidException {
		publishers = new HashMap<String, Publisher>();
		for (String p : c.getSubsections(SECTION_PUBLISHER))
			addPublisher(new PubSubConfig.Publisher(c, p));
	}

	/**
	 * Update the entire pubsub config file with any changes made to
	 * subsections.
	 *
	 * @param c
	 */
	public void update(Config c) {
		for (Publisher p : getPublishers())
			p.update(c);
	}

	/** @return all publishers */
	public Collection<Publisher> getPublishers() {
		return publishers.values();
	}

	/**
	 * @param uriRoot
	 * @return publisher for this uriRoot, else null
	 */
	public Publisher getPublisher(String uriRoot) {
		return publishers.get(uriRoot);
	}

	/**
	 * @param p
	 *            Publisher to add
	 */
	public void addPublisher(Publisher p) {
		publishers.put(p.getKey(), p);
	}

	/**
	 * @param p
	 *            publisher to remove
	 * @return true if publisher was found and removed
	 */
	public boolean removePublisher(Publisher p) {
		return publishers.remove(p.getKey()) != null;
	}
}
