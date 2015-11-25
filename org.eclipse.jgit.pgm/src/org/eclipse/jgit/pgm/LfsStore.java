package org.eclipse.jgit.pgm;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.lfs.lib.PlainFSRepository;
import org.eclipse.jgit.lfs.server.LargeObjectServlet;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_runLfsStore")
class LfsStore extends TextBuiltin {

	private static final String OBJECTS = "objects/";

	private static final String STORE_PATH = "/" + OBJECTS + "*";

	private static final String PROTOCOL_PATH = "/info/lfs";

	@Option(name = "--port", aliases = { "-p" }, usage = "usage_LFSPort")
	int port;

	@Option(name = "--store-url", aliases = { "-u" }, usage = "usage_LFSStoreUrl")
	String storeUrl;

	@Option(name = "--no-run-store", usage = "usage_LFSRunStore")
	boolean noRunStore;

	@Argument(required = true, metaVar = "metaVar_directory", usage = "usage_LFSDirectory")
	String directory;

	String protocolUrl;

	protected void run() throws Exception {
		AppServer server = new AppServer(port);
		ServletContextHandler app = server.addContext("/");
		Path dir = Paths.get(directory);
		PlainFSRepository repository = new PlainFSRepository(dir);

		if (!noRunStore) {
			LargeObjectServlet content = new LargeObjectServlet(repository, 30000);
			app.addServlet(new ServletHolder(content), STORE_PATH);
		}

		LfsProtocolServlet protocol = new LfsProtocolServlet(getStoreUrl());
		app.addServlet(new ServletHolder(protocol), PROTOCOL_PATH);

		server.setUp();

		if (!noRunStore) {
		  outw.println("LFS objects located in: " + directory);
		}
		outw.println("LFS store URL: " + getStoreUrl());
		outw.println("LFS protocol URL: " + getProtocolUrl());
	}

	private String getStoreUrl() {
		if (storeUrl == null) {
			if (noRunStore) {
				die("Local store not running and no --store-url specified");
			} else {
				// TODO: get real host name from the OS
				storeUrl = "http://localhost:" + port + "/" + OBJECTS;
			}
		}
		return storeUrl;
	}

	private String getProtocolUrl() {
		if (protocolUrl == null) {
			protocolUrl = "http://localhost:" + port + PROTOCOL_PATH;
		}
		return protocolUrl;
	}
}
