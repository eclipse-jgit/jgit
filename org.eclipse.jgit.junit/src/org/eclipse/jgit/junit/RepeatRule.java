/*
 * Copyright (C) 2016, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@link org.junit.rules.TestRule} which enables to run the same JUnit test
 * repeatedly. Add this rule to the test class
 *
 * <pre>
 * public class MyTest {
 * 	&#64;Rule
 * 	public RepeatRule repeatRule = new RepeatRule();
 * 	...
 * }
 * </pre>
 *
 * and annotate the test to be repeated with the
 * {@code @Repeat(n=<repetitions>)} annotation
 *
 * <pre>
 * &#64;Test
 * &#64;Repeat(n = 100)
 * public void test() {
 * 	...
 * }
 * </pre>
 *
 * then this test will be repeated 100 times. If any test execution fails test
 * repetition will be stopped.
 */
public class RepeatRule implements TestRule {

	private static Logger LOG = Logger
			.getLogger(RepeatRule.class.getName());

	/**
	 * Exception thrown if repeated execution of a test annotated with
	 * {@code @Repeat} failed.
	 */
	public static class RepeatedTestException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		/**
		 * Constructor
		 *
		 * @param message
		 *            the error message
		 */
		public RepeatedTestException(String message) {
			super(message);
		}

		/**
		 * Constructor
		 *
		 * @param message
		 *            the error message
		 * @param cause
		 *            exception causing this exception
		 */
		public RepeatedTestException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static class RepeatStatement extends Statement {

		private final int repetitions;

		private boolean abortOnFailure;

		private final Statement statement;

		private RepeatStatement(int repetitions, boolean abortOnFailure,
				Statement statement) {
			this.repetitions = repetitions;
			this.abortOnFailure = abortOnFailure;
			this.statement = statement;
		}

		@Override
		public void evaluate() throws Throwable {
			int failures = 0;
			for (int i = 0; i < repetitions; i++) {
				try {
					statement.evaluate();
				} catch (Throwable e) {
					failures += 1;
					RepeatedTestException ex = new RepeatedTestException(
							MessageFormat.format(
									"Repeated test failed when run for the {0}. time",
									Integer.valueOf(i + 1)),
							e);
					LOG.log(Level.SEVERE, ex.getMessage(), ex);
					if (abortOnFailure) {
						throw ex;
					}
				}
			}
			if (failures > 0) {
				RepeatedTestException e = new RepeatedTestException(
						MessageFormat.format(
								"Test failed {0} times out of {1} repeated executions",
								Integer.valueOf(failures),
								Integer.valueOf(repetitions)));
				LOG.log(Level.SEVERE, e.getMessage(), e);
				throw e;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public Statement apply(Statement statement, Description description) {
		Statement result = statement;
		Repeat repeat = description.getAnnotation(Repeat.class);
		if (repeat != null) {
			int n = repeat.n();
			boolean abortOnFailure = repeat.abortOnFailure();
			result = new RepeatStatement(n, abortOnFailure, statement);
		}
		return result;
	}
}
