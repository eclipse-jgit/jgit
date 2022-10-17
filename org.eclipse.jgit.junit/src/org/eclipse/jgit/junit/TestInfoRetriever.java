package org.eclipse.jgit.junit;

import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Abstract super class to retrieve a test's TestInfo before a test method is
 * executed
 *
 * @since 6.4
 */
public abstract class TestInfoRetriever {

	/**
	 * Test info
	 */
	protected TestInfo testInfo;

	/**
	 * @param info
	 */
	@BeforeEach
	protected void testInfo(TestInfo info) {
		this.testInfo = info;
	}

	/**
	 * @return name of the currently executed test method
	 */
	protected String getTestMethodName() {
		Optional<Method> testMethod = testInfo.getTestMethod();
		if (testMethod.isPresent()) {
			return testMethod.get().getName();
		}
		return "Test method name unavailable";
	}

}
