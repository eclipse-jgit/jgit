package org.eclipse.jgit.http.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class SetAdditionalHeadersTest extends HttpTestCase {

	private URIish remoteURI;

	private RevBlob A_txt;

	private RevCommit A, B;


	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> src = createTestRepository();
		final File srcGit = src.getRepository().getDirectory();
		final URI base = srcGit.getParentFile().toURI();

		ServletContextHandler app = server.addContext("/git");
		app.setResourceBase(base.toString());
		ServletHolder holder = app.addServlet(DefaultServlet.class, "/");
		// The tmp directory is symlinked on OS X
		holder.setInitParameter("aliases", "true");
		server.setUp();

		remoteURI = toURIish(app, srcGit.getName());

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);
	}

	@Test
	public void testSetHeaders() throws IOException {
		Repository dst = createBareRepository();

		assertEquals("http", remoteURI.getScheme());

		Transport t = Transport.open(dst, remoteURI);
		try {
			// I didn't make up these public interface names, I just
			// approved them for inclusion into the code base. Sorry.
			// --spearce
			//
			assertTrue("isa TransportHttp", t instanceof TransportHttp);
			assertTrue("isa HttpTransport", t instanceof HttpTransport);

			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("Cookie", "someTokenValue=23gBog34");
			headers.put("AnotherKey", "someValue");
			((TransportHttp) t).setAdditionalHeaders(headers);
			t.openFetch();
		} finally {
			t.close();
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(info.getRequestHeader("Cookie"), "someTokenValue=23gBog34");
		assertEquals(info.getRequestHeader("AnotherKey"), "someValue");
		assertEquals(200, info.getStatus());

		info = requests.get(1);
		assertEquals("GET", info.getMethod());
		assertEquals(info.getRequestHeader("Cookie"), "someTokenValue=23gBog34");
		assertEquals(info.getRequestHeader("AnotherKey"), "someValue");
		assertEquals(200, info.getStatus());
	}

}
