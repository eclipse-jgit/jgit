package org.eclipse.jgit.lib;

import junit.framework.TestCase;

public class ConfigTest extends TestCase {
	public void testQuotingForSubSectionNames() {
		Config config = new Config();
		config.setString("testsection", "testsubsection", "testname",
				"testvalue");
		assertTrue(config.toText().contains("\"testsubsection\""));
		config.clear();
		config.setString("testsection", "#quotable", "testname", "testvalue");
		assertTrue(config.toText().contains("\"#quotable\""));
		assertFalse(config.toText().contains("\"\"#quotable\"\""));
		config.clear();
		config.setString("testsection", "with\"quote", "testname", "testvalue");
		assertTrue(config.toText().contains("\"with\\\"quote\""));
		assertFalse(config.toText().contains("\"\"with\\\"quote\"\""));
	}
}
