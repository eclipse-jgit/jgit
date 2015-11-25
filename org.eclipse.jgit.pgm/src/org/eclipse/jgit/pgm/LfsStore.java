package org.eclipse.jgit.pgm;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.lfs.lib.PlainFSRepository;
import org.eclipse.jgit.lfs.server.LargeObjectServlet;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_runLfsStore")
class LfsStore extends TextBuiltin {

	@Option(name = "--port", aliases = { "-p" }, usage = "usage_LFSPort")
	int port;

	@Argument(required = true, metaVar = "metaVar_directory", usage = "usage_LFSDirectory")
	String directory;

	protected void run() throws Exception {
		outw.println("Starting LFS Store in: " + directory);
		AppServer server = new AppServer(port);
		ServletContextHandler app = server.addContext("/lfs");
		Path dir = Paths.get(directory);
		PlainFSRepository repository = new PlainFSRepository(dir);
		LargeObjectServlet servlet = new LargeObjectServlet(repository, 30000);
		app.addServlet(new ServletHolder(servlet), "/objects/*");
		server.setUp();
		outw.println("Running on http://localhost:" + server.getPort()
				+ "/lfs/objects/*");
	}
}
