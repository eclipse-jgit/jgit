package org.eclipse.jgit.internal.transport.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class FirstWantLineTest {

	@Test
	public void testWantLineWithOptions() {
		String line = "want b9d4d1eb2f93058814480eae9e1b67550f46ee38 "
				+ "no-progress include-tag ofs-delta agent=JGit/unknown";

		FirstWantLine r = FirstWantLine.fromLine(line);
		assertEquals("want b9d4d1eb2f93058814480eae9e1b67550f46ee38",
				r.getLine());
		assertTrue(r.getOptions()
				.containsAll(Arrays.asList("no-progress", "include-tag",
						"ofs-delta", "agent=JGit/unknown")));
	}

	@Test
	public void testWantLineWithoutOptions() {
		String line = "want b9d4d1eb2f93058814480eae9e1b67550f46ee38";

		FirstWantLine r = FirstWantLine.fromLine(line);
		assertEquals("want b9d4d1eb2f93058814480eae9e1b67550f46ee38",
				r.getLine());
		assertTrue(r.getOptions().isEmpty());
	}
}
