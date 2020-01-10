package org.eclipse.jgit.niofs.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.eclipse.jgit.niofs.JGitFileSystemBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@Ignore
public class JGitFileSystemBuilderTest {

	@AfterClass
	@BeforeClass
	public static void cleanup() {
		try {
			FileUtils.delete(new File(".niogit"), FileUtils.RECURSIVE);
		} catch (IOException ex) {
			// ignore
		}
	}

	@Test
	public void testSimpleBuilderSample() throws IOException {
		final FileSystem fs = JGitFileSystemBuilder.newFileSystem("myrepo");

		Path foo = fs.getPath("/foo");
		Files.createDirectory(foo);

		Path hello = foo.resolve("hello.txt"); // /foo/hello.txt

		Files.write(hello, Collections.singletonList("hello world"), StandardCharsets.UTF_8);

		assertEquals("hello world", Files.readAllLines(hello).get(0));
	}
}
