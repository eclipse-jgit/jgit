/*
 * Copyright (C) 2016, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.junit;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@link TestRule} which enables to run the same JUnit test repeatedly. Add
 * this rule to the test class
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

	public static class RepeatedTestException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public RepeatedTestException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static class RepeatStatement extends Statement {

		private final int repetitions;

		private final Statement statement;

		private RepeatStatement(int repetitions, Statement statement) {
			this.repetitions = repetitions;
			this.statement = statement;
		}

		@Override
		public void evaluate() throws Throwable {
			for (int i = 0; i < repetitions; i++) {
				try {
					statement.evaluate();
				} catch (Throwable e) {
					RepeatedTestException ex = new RepeatedTestException(
							MessageFormat.format(
									"Repeated test failed when run for the {0}. time",
									Integer.valueOf(i + 1)),
							e);
					LOG.log(Level.SEVERE, ex.getMessage(), ex);
					throw ex;
				}
			}
		}
	}

	@Override
	public Statement apply(Statement statement, Description description) {
		Statement result = statement;
		Repeat repeat = description.getAnnotation(Repeat.class);
		if (repeat != null) {
			int n = repeat.n();
			result = new RepeatStatement(n, statement);
		}
		return result;
	}
}
